package be.itspace.fairllm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LlamaApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    @Volatile private var activeCall: Call? = null

    suspend fun health(baseUrl: String): ServerStatus = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(baseUrl.trimEnd('/') + "/health").get().build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> ServerStatus.Ready
                    503 -> ServerStatus.Loading
                    else -> ServerStatus.Offline
                }
            }
        } catch (_: Exception) {
            ServerStatus.Offline
        }
    }

    suspend fun streamChat(
        baseUrl: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
        onToken: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val jsonMessages = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            jsonMessages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.forEach { m ->
            jsonMessages.put(JSONObject().put("role", m.role).put("content", m.content))
        }

        val bodyJson = JSONObject()
            .put("model", "local")
            .put("messages", jsonMessages)
            .put("stream", true)
            .put("temperature", temperature.toDouble())
            .put("top_p", topP.toDouble())
            .put("top_k", 20)
            .put("min_p", 0.0)
            .put("max_tokens", maxTokens)

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/chat/completions")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        activeCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Serveur llama.cpp: HTTP ${response.code}")
                }
                val source = response.body?.source() ?: return@use
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isBlank()) continue
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices") ?: continue
                        if (choices.length() == 0) continue
                        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                        val token = delta.optString("content", "")
                        if (token.isNotEmpty()) onToken(token)
                    } catch (_: Exception) {
                        // Ignore malformed keep-alive or non-content chunks.
                    }
                }
            }
        } finally {
            activeCall = null
        }
    }

    fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }
}

sealed class ServerStatus {
    data object Offline : ServerStatus()
    data object Loading : ServerStatus()
    data object Ready : ServerStatus()
}
