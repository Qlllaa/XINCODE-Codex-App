package com.codex.provider

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HttpCacheProvider {
    companion object {
        private const val CACHE_SIZE = 50 * 1024 * 1024 // 50MB
        
        fun get(): okhttp3.Cache? {
            return null // Placeholder - would need cache directory from context
        }
    }
}

fun createJsonRequest(url: String, apiKey: String, body: JSONObject): Request {
    return Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()
}

class ProviderManager {
    companion object {
        private const val TAG = "CodexProvider"
        
        suspend fun testConnection(baseUrl: String, apiKey: String): Boolean {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url("$baseUrl/v1/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                return response.isSuccessful
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Test connection failed", e)
                return false
            }
        }
    }
}