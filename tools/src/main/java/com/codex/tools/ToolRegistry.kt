package com.codex.tools

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ToolRegistry - manages available tools and their execution.
 */
class ToolRegistry {
    companion object {
        private const val TAG = "CodexTools"
    }
    
    private val tools: MutableMap<String, Tool> = mutableMapOf()
    
    init {
        registerTool(ShellTool())
        registerTool(FileTool())
        registerTool(SearchTool())
    }
    
    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
        Log.d(TAG, "Registered tool: ${tool.name}")
    }
    
    suspend fun executeTool(toolName: String, arguments: String): ToolResult {
        val tool = tools[toolName] ?: return ToolResult(
            stdout = "",
            stderr = "Tool '$toolName' not found",
            exitCode = 1,
            durationMs = 0
        )
        
        return tool.execute(arguments)
    }
    
    fun listTools(): List<Tool> = tools.values.toList()
}

/**
 * Base class for all tools.
 */
abstract class Tool(
    val name: String,
    val description: String,
    val parameters: Map<String, String>
) {
    abstract suspend fun execute(arguments: String): ToolResult
}

/**
 * Shell tool - executes terminal commands.
 */
class ShellTool : Tool(
    name = "shell",
    description = "Execute a shell command on the device",
    parameters = mapOf("command" to "The shell command to execute")
) {
    override suspend fun execute(arguments: String): ToolResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", arguments))
                
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                
                val exitCode = process.waitFor()
                
                ToolResult(
                    stdout = stdout,
                    stderr = stderr,
                    exitCode = exitCode,
                    durationMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                ToolResult(
                    stdout = "",
                    stderr = e.message ?: "Unknown error",
                    exitCode = 1,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }
}

/**
 * File tool - read/write files.
 */
class FileTool : Tool(
    name = "file",
    description = "Read or write a file",
    parameters = mapOf(
        "action" to "read or write",
        "path" to "file path",
        "content" to "content to write (for write action)"
    )
) {
    override suspend fun execute(arguments: String): ToolResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simple JSON parsing of arguments
                val parts = arguments.split("|")
                val action = parts.getOrNull(0)?.trim() ?: "read"
                val path = parts.getOrNull(1)?.trim() ?: ""
                val content = parts.getOrNull(2)?.trim() ?: ""
                
                when (action.lowercase()) {
                    "write" -> {
                        File(path).apply {
                            parentFile?.mkdirs()
                            writeText(content)
                        }
                        ToolResult(
                            stdout = "File written successfully",
                            stderr = "",
                            exitCode = 0,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    }
                    "read" -> {
                        val text = File(path).readText()
                        ToolResult(
                            stdout = text,
                            stderr = "",
                            exitCode = 0,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    }
                    else -> ToolResult(
                        stdout = "",
                        stderr = "Unknown action: $action",
                        exitCode = 1,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            } catch (e: Exception) {
                ToolResult(
                    stdout = "",
                    stderr = e.message ?: "Unknown error",
                    exitCode = 1,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }
}

/**
 * Search tool - search for information.
 */
class SearchTool : Tool(
    name = "search",
    description = "Search for information online",
    parameters = mapOf("query" to "Search query")
) {
    override suspend fun execute(arguments: String): ToolResult {
        return ToolResult(
            stdout = "Search results for: $arguments\n\n(Note: Full search integration would require web_search MCP server or similar)",
            stderr = "",
            exitCode = 0,
            durationMs = 50
        )
    }
}

data class ToolResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val durationMs: Long?
)