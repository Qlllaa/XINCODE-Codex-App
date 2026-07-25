package com.codex.provider

import android.util.Base64
import android.util.Log
import com.codex.data.AppDatabase
import com.codex.security.KeystoreProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class AgentStreamResult {
    data class Token(val text: String) : AgentStreamResult()
    data class Success(
        val content: String, 
        val rawResponse: String? = null
    ) : AgentStreamResult()
    data class Error(val error: Exception, val httpCode: Int? = null) : AgentStreamResult()
    data object Idle : AgentStreamResult()
}

class ApiError(
    message: String,
    val httpCode: Int? = null,
    val isAuthenticationError: Boolean = false,
    val isRateLimited: Boolean = false
) : Exception(message) {
    companion object {
        fun from(exception: IOException, httpCode: Int? = null): ApiError {
            val message = if (httpCode != null) "HTTP $httpCode: ${exception.message}" else exception.message ?: "Network error"
            return ApiError(
                message = message,
                httpCode = httpCode,
                isAuthenticationError = httpCode in listOf(401, 403),
                isRateLimited = httpCode == 429
            )
        }
    }
}

class OpenAiClient(
    private val database: AppDatabase,
    private val keystore: KeystoreProvider
) {
    companion object {
        private const val TAG = "CodexProvider"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Streaming client: longer read timeout to survive token generation pauses
    private val streamingHttpClient = httpClient.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Make a chat completion request
     */
    suspend fun chat(messages: List<JSONObject>, stream: Boolean = false): Result<AgentStreamResult> = 
        withContext(Dispatchers.IO) {
            try {
                val configResult = resolveConfig()
                if (!configResult.isSuccess) {
                    return@withContext Result.failure(configResult.exceptionOrNull()!!)
                }
                
                val config = configResult.getOrNull()!!
                
                val requestBuilder = Request.Builder()
                    .url("${config.baseUrl}/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                
                val requestBody = JSONObject().apply {
                    put("model", config.model)
                    put("messages", JSONArray(messages))
                    put("stream", stream)
                    if (stream) {
                        put("max_tokens", 4096)
                        put("temperature", 0.7)
                    }
                }
                
                val request = if (stream) {
                    requestBuilder.post(requestBody.toString().toRequestBody(JSON))
                        .header("Accept", "text/event-stream")
                        .build()
                } else {
                    requestBuilder.post(requestBody.toString().toRequestBody(JSON))
                        .build()
                }
                
                Log.d(TAG, "→ POST ${request.url}")
                
                val response = if (stream) {
                    streamingHttpClient.newCall(request).execute()
                } else {
                    httpClient.newCall(request).execute()
                }
                
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "← ${response.code} ${responseBody.take(300)}")
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        ApiError.from(IOException("HTTP ${response.code}"), httpCode = response.code)
                    )
                }
                
                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                
                if (choices != null && choices.length > 0) {
                    val choice = choices.getJSONObject(0)
                    val message = choice.optJSONObject("message")
                    val content = message?.optString("content", "")
                    
                    return@withContext Result.success(AgentStreamResult.Success(
                        content = content ?: "",
                        rawResponse = responseBody
                    ))
                }
                
                return@withContext Result.failure(Exception("No response from API"))
                
            } catch (e: Exception) {
                Log.e(TAG, "Chat error", e)
                return@withContext Result.failure(e)
            }
        }

    private suspend fun resolveConfig(): Result<ResolvedConfig> = withContext(Dispatchers.IO) {
        try {
            val active = database.configDao().getActive() ?: return@withContext Result.failure(
                IllegalStateException("API configuration not found")
            )
            
            val baseUrl = active.baseUrl.trimEnd('/')
            val model = active.model
            
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("base_url 未配置"))
            }
            if (model.isBlank()) {
                return@withContext Result.failure(IllegalStateException("model 未配置"))
            }
            
            val apiKeyBytes = Base64.decode(active.apiKeyEnc, Base64.NO_WRAP)
            val apiKey = String(keystore.decrypt(apiKeyBytes))
            
            Result.success(ResolvedConfig(baseUrl, model, apiKey))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private data class ResolvedConfig(
        val baseUrl: String,
        val model: String,
        val apiKey: String
    )
}