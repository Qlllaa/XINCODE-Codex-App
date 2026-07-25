package com.codex.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.core.AgentStateExtended
import kotlinx.coroutines.flow.Flow

@Composable
fun ChatView(
    messages: List<String>,
    userInput: String,
    onUserInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    agentState: AgentStateExtended,
    onToggleStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Agent status indicator
        agentState.status.let { status ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status) {
                    "Running" -> Text(
                        text = "● 运行中 (${agentState.iteration})",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    "Completed" -> Text(
                        text = "✓ 已完成",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> Text(
                        text = "空闲",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Messages list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                messages.forEach { message ->
                    MessageBubble(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (messages.isEmpty()) {
                    Text(
                        text = "输入消息开始对话...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        }

        // Input area
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = onUserInputChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("输入消息...")
                    },
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                if (agentState.isRunning) {
                    Button(
                        onClick = onToggleStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("停止")
                    }
                } else {
                    Button(
                        onClick = onSend,
                        enabled = userInput.isNotBlank()
                    ) {
                        Text("发送")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SettingsView(
    baseUrl: String,
    onBaseUrlChanged: (String) -> Unit,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    model: String,
    onModelChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "API 配置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChanged,
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.openai.com") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChanged,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            isPassword = true,
            placeholder = { Text("sk-...") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = model,
            onValueChange = onModelChanged,
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("gpt-4") }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存配置")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "支持的 API 服务商：OpenAI, Anthropic, Google, 以及兼容 OpenAI API 的其他提供商",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}