package com.codex.core

import android.util.Log
import com.codex.data.StateCursorDao
import com.codex.data.StateCursorEntity
import com.codex.provider.AgentStreamResult
import com.codex.provider.ApiError
import com.codex.provider.OpenAiClient
import com.codex.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Actions emitted by AgentCore during tool execution lifecycle.
 */
sealed class ToolBlockAction {
    data class PushCall(
        val callIndex: Int,
        val toolName: String,
        val arguments: String
    ) : ToolBlockAction()

    data class UpdateResult(
        val callIndex: Int,
        val stdout: String,
        val stderr: String,
        val exitCode: Int?,
        val durationMs: Long?,
        val status: String  // "SUCCESS" | "FAIL" | "DENIED"
    ) : ToolBlockAction()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgentCore(
    private val openAiClient: OpenAiClient,
    private val sessionId: String,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    cursorDao: StateCursorDao? = null,
    private val confirmHandler: (suspend (GateCommand, String) -> ToolConfirmResult)? = null,
    private val maxIterations: Int = 50,
    private val totalTimeoutMs: Long = 5 * 60 * 1000L
) {
    companion object {
        private const val TAG = "CodexAgentCore"
        
        const val DEFAULT_SYSTEM_PROMPT = """
            You are a helpful and autonomous AI agent that can execute commands, 
            manage files, search information, and perform complex tasks on behalf of the user.
            
            IMPORTANT RULES:
            - Always think step by step
            - Use tools when appropriate to gather information or perform actions
            - Verify results before reporting success
            - If uncertain, ask clarifying questions
            - Be concise but thorough in responses
            - When executing shell commands, always handle errors gracefully
            
            TOOL USAGE:
            - shell: Execute terminal commands
            - file: Read/write files
            - search: Search for information
            - code: Write and execute code
            
            Respond in the same language as the user's query unless specified otherwise.
        """.trimIndent()
    }

    private var messages: List<JSONObject> = emptyList()
    private val tokenFlow = MutableSharedFlow<String>(replay = 0)
    private val toolActions = Channel<ToolBlockAction>(Channel.UNLIMITED)
    private val stateFlow = MutableStateFlow(AgentState())
    
    private var currentJob: Job? = null
    private var pendingToolCallJson: String? = null
    private var pendingToolResultJson: String? = null
    
    private val cursorDao = cursorDao
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Start or continue the agent loop
     */
    fun start(message: String): Flow<AgentStreamResult> {
        Log.d(TAG, "Starting agent with message: ${message.take(50)}")
        
        if (currentJob?.isActive == true) {
            Log.w(TAG, "Agent already running, stopping previous job")
            currentJob?.cancel("User initiated stop")
        }
        
        currentJob = scope.launch {
            try {
                runLoop(message)
            } catch (e: Exception) {
                Log.e(TAG, "Agent loop error", e)
                stateFlow.value = AgentState.Error(e.message ?: "Unknown error")
            }
        }
        
        return createStreamFlow()
    }
    
    private suspend fun runLoop(initialMessage: String) {
        // Initialize messages with system prompt and initial user message
        val msgs = mutableListOf<JSONObject>()
        msgs.add(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        
        if (initialMessage.isNotBlank()) {
            msgs.add(JSONObject().apply {
                put("role", "user")
                put("content", initialMessage)
            })
        }
        messages = msgs.toList()
        
        var iterationCount = 0
        val startTime = System.currentTimeMillis()
        
        while (iterationCount < maxIterations) {
            // Check timeout
            if (System.currentTimeMillis() - startTime > totalTimeoutMs) {
                Log.w(TAG, "Total timeout reached")
                break
            }
            
            iterationCount++
            stateFlow.value = stateFlow.value.copy(
                iteration = iterationCount,
                status = "Running"
            )
            
            // Call AI model
            val response = openAiClient.chat(messages)
            when (response) {
                is AgentStreamResult.Success -> {
                    val content = response.content
                    messages.add(JSONObject().apply {
                        put("role", "assistant")
                        put("content", content)
                    })
                    
                    tokenFlow.emit(content)
                    
                    // Check for tool calls
                    val toolCalls = extractToolCalls(response.rawResponse)
                    if (toolCalls.isNotEmpty()) {
                        // Process tool calls
                        processToolCalls(toolCalls, iterationCount)
                    } else {
                        // Final response
                        stateFlow.value = stateFlow.value.copy(status = "Completed")
                        break
                    }
                }
                is AgentStreamResult.Error -> {
                    stateFlow.value = AgentState.Error(response.error.message)
                    break
                }
                else -> {
                    // Continue for other states
                }
            }
        }
        
        if (stateFlow.value !is AgentState.Error) {
            stateFlow.value = AgentState.Idle
        }
    }
    
    private fun extractToolCalls(rawResponse: String?): List<JSONObject> {
        if (rawResponse.isNullOrEmpty()) return emptyList()
        
        try {
            val json = JSONObject(rawResponse)
            val choices = json.optJSONArray("choices") ?: return emptyList()
            
            for (i in 0 until choices.length()) {
                val choice = choices.getJSONObject(i)
                val message = choice.optJSONObject("message") ?: continue
                val toolCalls = message.optJSONArray("tool_calls") ?: continue
                
                val result = mutableListOf<JSONObject>()
                for (j in 0 until toolCalls.length()) {
                    result.add(toolCalls.getJSONObject(j))
                }
                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting tool calls", e)
        }
        
        return emptyList()
    }
    
    private suspend fun processToolCalls(toolCalls: List<JSONObject>, iteration: Int) {
        for ((index, toolCall) in toolCalls.withIndex()) {
            val toolName = toolCall.optString("function", "").let { 
                JSONObject(toolCall.optString("function", "{}")).optString("name", "unknown") 
            }
            val arguments = toolCall.optString("arguments", "{}")
            
            // Emit tool call action
            toolActions.send(ToolBlockAction.PushCall(index, toolName, arguments))
            
            // Execute tool (simplified - would integrate with ToolRegistry in full implementation)
            val result = executeTool(toolName, arguments)
            
            // Emit result action
            toolActions.send(ToolBlockAction.UpdateResult(
                callIndex = index,
                stdout = result.stdout,
                stderr = result.stderr,
                exitCode = result.exitCode,
                durationMs = result.durationMs,
                status = if (result.exitCode == 0) "SUCCESS" else "FAIL"
            ))
            
            // Add tool result to messages
            messages.add(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", toolCall.optString("id"))
                put("content", result.stdout)
            })
        }
    }
    
    private suspend fun executeTool(name: String, arguments: String): ToolExecutionResult {
        // Simplified tool execution
        // In full implementation, this would use ToolRegistry and security gate
        return ToolExecutionResult(
            stdout = "Tool '$name' executed",
            stderr = "",
            exitCode = 0,
            durationMs = 100
        )
    }
    
    private fun createStreamFlow(): Flow<AgentStreamResult> {
        return flow {
            stateFlow.collect { state ->
                emit(AgentStreamResult.State(state))
            }
            
            tokenFlow.collect { token ->
                emit(AgentStreamResult.Token(token))
            }
            
            toolActions.receiveCatching().getOrNull()?.let { action ->
                emit(AgentStreamResult.ToolAction(action))
            }
        }
    }
    
    /**
     * Stop the agent
     */
    fun stop() {
        Log.d(TAG, "Stopping agent")
        currentJob?.cancel("User requested stop")
        currentJob = null
        stateFlow.value = AgentState.Idle
    }
    
    /**
     * Get the current state
     */
    val currentState: AgentState
        get() = stateFlow.value
        
    /**
     * Get token flow
     */
    val tokens: Flow<String>
        get() = tokenFlow
        
    /**
     * Get tool actions flow
     */
    val toolActionsFlow: Flow<ToolBlockAction>
        get() = toolActions.asFlow()
}

data class ToolExecutionResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val durationMs: Long?
)