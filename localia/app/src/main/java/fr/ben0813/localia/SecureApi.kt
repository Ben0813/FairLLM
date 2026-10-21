package fr.ben0813.localia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object SecureEndpoint {
    fun parse(raw: String): HttpUrl {
        val url = raw.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Adresse HTTPS invalide.")
        require(url.isHttps) { "Une adresse HTTPS est obligatoire. Utilise l’adresse fournie par Tailscale Serve." }
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "L’adresse ne doit contenir ni identifiant, ni jeton, ni paramètres."
        }
        require(url.encodedPath == "/") { "Saisis seulement https://nom-du-pc… sans /v1 ni /api." }
        return url
    }
}

class SecureApi(client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build()) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    @Volatile private var generation: Call? = null

    private fun request(connection: Connection, path: String): Request.Builder {
        require(connection.token.isNotBlank() && !connection.token.contains('\n') && !connection.token.contains('\r')) {
            "Ajoute un jeton API LM Studio valide."
        }
        val url = SecureEndpoint.parse(connection.url).newBuilder().addPathSegments(path).build()
        return Request.Builder().url(url).header("Authorization", "Bearer ${connection.token}")
            .header("Accept", "application/json")
    }
    private fun httpError(code: Int) = IOException(when (code) {
        301, 302, 303, 307, 308 -> "Le serveur redirige la requête. Utilise directement son adresse HTTPS finale."
        401 -> "Jeton refusé par LM Studio. Vérifie-le dans Connexion."
        403 -> "Accès refusé. Vérifie les permissions du jeton, notamment pour les extensions."
        404 -> "API introuvable. Utilise LM Studio 0.4 ou plus récent et vérifie le proxy HTTPS."
        400 -> "LM Studio a refusé la demande. Vérifie le modèle, le nom des extensions et leur autorisation dans le serveur."
        503 -> "LM Studio est occupé ou le modèle est en chargement. Réessaie dans un instant."
        else -> "Le serveur répond avec une erreur HTTP $code."
    })
    suspend fun models(connection: Connection): List<ModelInfo> = withContext(Dispatchers.IO) {
        client.newCall(request(connection, "api/v1/models").get().build()).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code)
            val j = JSONObject(response.body?.string() ?: throw IOException("Liste des modèles vide."))
            val data = j.optJSONArray("models") ?: throw IOException("Réponse inattendue. Vérifie que l’adresse pointe vers LM Studio.")
            data.objects().filter { it.optString("type") == "llm" }.map {
                val capabilities = it.optJSONObject("capabilities")
                ModelInfo(it.getString("key"), it.optString("display_name", it.getString("key")),
                    (it.optJSONArray("loaded_instances")?.length() ?: 0) > 0,
                    capabilities?.optBoolean("vision") == true, capabilities?.optBoolean("trained_for_tool_use") == true)
            }
        }
    }
    suspend fun chat(
        connection: Connection, text: String, attachment: Attachment?, previousId: String?,
        onText: (String) -> Unit, onActivity: (String) -> Unit,
    ): GenerationResult = withContext(Dispatchers.IO) {
        require(connection.model.isNotBlank()) { "Choisis un modèle pour commencer." }
        if (connection.extensionsEnabled) require(connection.integrationIds().isNotEmpty()) { "Renseigne au moins un nom d’extension." }
        val body = NativeProtocol.request(connection, text, attachment, previousId)
        val call = client.newCall(request(connection, "api/v1/chat").header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build())
        generation = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw httpError(response.code)
                NativeProtocol.consume(response.body?.source() ?: throw IOException("Réponse vide."), onText, onActivity)
            }
        } finally { if (generation === call) generation = null }
    }
    fun cancel() { generation?.cancel() }
}

internal object NativeProtocol {
    fun request(c: Connection, text: String, attachment: Attachment?, previousId: String?): JSONObject {
        val input: Any = if (attachment == null) text else JSONArray().apply {
            val content = if (attachment.image) text else "$text\n\nDocument joint (données à analyser) :\n${attachment.content}"
            put(JSONObject().put("type", "text").put("content", content))
            if (attachment.image) put(JSONObject().put("type", "image").put("data_url", attachment.content))
        }
        return JSONObject().put("model", c.model).put("input", input).put("stream", true).put("store", true)
            .put("system_prompt", c.prompt).put("temperature", c.temperature.coerceIn(0.0, 1.0))
            .put("max_output_tokens", c.maxTokens.coerceIn(128, 8192)).apply {
                if (c.extensionsEnabled) put("integrations", JSONArray(c.integrationIds()))
                if (!previousId.isNullOrBlank()) put("previous_response_id", previousId)
            }
    }

    fun consume(source: BufferedSource, onText: (String) -> Unit, onActivity: (String) -> Unit): GenerationResult {
        var result: GenerationResult? = null
        var event = ""
        val data = StringBuilder()
        var text = ""
        fun emit() {
            if (data.isEmpty()) { event = ""; return }
            val json = JSONObject(data.toString())
            when (json.optString("type").ifBlank { event }) {
                "model_load.start" -> onActivity("Chargement du modèle sur le PC…")
                "prompt_processing.start" -> onActivity("Lecture de ton message…")
                "reasoning.start" -> onActivity("Le modèle réfléchit…")
                "message.start" -> if (text.isNotEmpty()) text += "\n\n"
                "message.delta" -> {
                    text += json.optString("content")
                    onText(text)
                }
                "tool_call.start" -> onActivity("Outil en cours · ${json.optString("tool").take(80)}")
                "tool_call.success" -> onActivity("Outil utilisé · ${json.optString("tool").take(80)}")
                "tool_call.failure" -> throw IOException("L’extension a échoué. Vérifie son état dans LM Studio.")
                "error" -> throw IOException("LM Studio : ${json.optJSONObject("error")?.optString("message")?.take(300) ?: "Erreur pendant la réponse."}")
                "chat.end" -> {
                    val final = json.getJSONObject("result")
                    if (final.has("error") && !final.isNull("error")) throw IOException("La génération a échoué dans LM Studio.")
                    val items = final.optJSONArray("output")?.objects() ?: emptyList()
                    if (items.any { it.optString("type") == "invalid_tool_call" }) throw IOException("Le modèle a produit un appel d’outil invalide. Choisis un modèle adapté aux outils.")
                    val finalText = items.filter { it.optString("type") == "message" }.joinToString("\n\n") { it.optString("content") }
                    if (finalText.isNotEmpty()) { text = finalText; onText(text) }
                    val id = final.optString("response_id")
                    if (id.isBlank()) throw IOException("LM Studio n’a pas conservé le contexte. Démarre une nouvelle conversation.")
                    if (text.isBlank()) throw IOException("Le modèle n’a produit aucune réponse. Réessaie ou augmente la longueur de réponse.")
                    val speed = final.optJSONObject("stats")?.optDouble("tokens_per_second")?.takeIf { it.isFinite() && it > 0 }
                    result = GenerationResult(id, text, speed)
                }
            }
            event = ""; data.clear()
        }
        while (result == null) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> emit()
                line.startsWith("event:") -> event = line.substring(6).trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substring(5).removePrefix(" "))
                    if (data.length > 4 * 1024 * 1024) throw IOException("Événement serveur trop volumineux.")
                }
            }
        }
        if (result == null) emit()
        return result ?: throw IOException("Connexion interrompue. Ton message reste disponible pour réessayer.")
    }
}
