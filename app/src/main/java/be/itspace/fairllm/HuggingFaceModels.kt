package be.itspace.fairllm

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class HuggingFaceModels(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
) {
    companion object {
        fun repository(input: String): String {
            val value = input.trim()
            val repo = if (value.startsWith("https://")) {
                val url = value.toHttpUrlOrNull()
                    ?: throw IOException("Lien Hugging Face invalide.")
                if (url.host != "huggingface.co" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) {
                    throw IOException("Utilise un lien https://huggingface.co/auteur/modele.")
                }
                url.pathSegments.take(2).joinToString("/")
            } else value
            if (!Regex("[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*").matches(repo)) {
                throw IOException("Colle le lien du dépôt Hugging Face ou auteur/modele.")
            }
            return repo
        }

        fun parse(repo: String, json: String): List<ModelPreset> {
            val info = JSONObject(json)
            if (info.optBoolean("private") || (info.has("gated") && info.opt("gated") != false && info.opt("gated") != JSONObject.NULL)) {
                throw IOException("Ce dépôt nécessite une autorisation. Choisis un modèle public sans restriction d’accès.")
            }
            val revision = info.getString("sha")
            if (!Regex("[a-fA-F0-9]{40}").matches(revision)) throw IOException("Révision du modèle invalide.")
            val files = info.optJSONArray("siblings") ?: throw IOException("Aucun fichier disponible.")
            val result = mutableListOf<ModelPreset>()
            for (index in 0 until files.length()) {
                val file = files.getJSONObject(index)
                val name = file.getString("rfilename")
                val lower = name.lowercase(Locale.ROOT)
                if (!lower.endsWith(".gguf") || Regex("-\\d{5}-of-\\d{5}\\.gguf$").containsMatchIn(lower) ||
                    lower.substringAfterLast('/').startsWith("mmproj") || name.split('/').any { it == ".." || it.isEmpty() }) continue
                val lfs = file.optJSONObject("lfs") ?: continue
                val hash = lfs.optString("sha256").lowercase(Locale.ROOT)
                val size = lfs.optLong("size", 0)
                if (size <= 0 || !Regex("[a-f0-9]{64}").matches(hash)) continue
                result += ModelPreset(
                    title = name.substringAfterLast('/').removeSuffix(".gguf"),
                    hf = repo, gpuLayers = 0, contextSize = 2048,
                    subtitle = String.format(Locale.FRANCE, "%s • %.2f Go", repo, size / 1_000_000_000.0),
                    fileName = name, revision = revision, byteSize = size, sha256 = hash,
                    storageName = "$hash.gguf",
                )
            }
            if (result.isEmpty()) throw IOException("Aucun modèle GGUF en une seule partie avec taille et empreinte vérifiables. Choisis un dépôt GGUF public.")
            return result.sortedWith(compareBy<ModelPreset> { !it.fileName.contains("Q4_K_M", true) }.thenBy { it.byteSize })
        }
    }

    fun list(input: String): List<ModelPreset> {
        val repo = repository(input)
        val request = Request.Builder().url("https://huggingface.co/api/models/$repo?blobs=true").build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                401, 403 -> throw IOException("Ce modèle nécessite un compte ou une autorisation Hugging Face.")
                404 -> throw IOException("Dépôt introuvable ou privé. Vérifie le lien.")
                429 -> throw IOException("Hugging Face reçoit trop de demandes. Réessaie plus tard.")
            }
            if (!response.isSuccessful) throw IOException("Hugging Face : HTTP ${response.code}.")
            return parse(repo, response.body?.string() ?: throw IOException("Réponse vide."))
        }
    }
}
