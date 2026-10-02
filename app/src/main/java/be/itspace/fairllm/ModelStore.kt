package be.itspace.fairllm

import android.content.Context
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelStore(
    private val directory: File,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).build(),
    private val availableBytes: () -> Long = { directory.usableSpace },
    private val downloadUrl: (ModelPreset) -> String = { it.downloadUrl },
) {
    constructor(context: Context) : this(File(context.filesDir, "models"))
    init { check(directory.exists() || directory.mkdirs()) { "Impossible de créer le dossier des modèles." } }
    @Volatile private var call: Call? = null
    @Volatile private var cancelled = false

    fun cancel() { cancelled = true; call?.cancel() }

    fun ready(model: ModelPreset): Boolean {
        val file = File(directory, model.fileName)
        val receipt = File(directory, model.fileName + ".sha256")
        return file.length() == model.byteSize && receipt.exists() && receipt.readText() == model.sha256
    }

    fun prepare(model: ModelPreset, progress: (String) -> Unit): File {
        if (ready(model)) return File(directory, model.fileName)
        val partial = File(directory, model.fileName + ".part")
        if (partial.length() > model.byteSize) partial.delete()
        val offset = partial.length()
        val available = availableBytes()
        if (available < model.byteSize - offset + 128L * 1024 * 1024) {
            throw IOException("Espace insuffisant : libère au moins ${((model.byteSize - offset) / 1_000_000_000.0 + 0.2).toFloat()} Go.")
        }
        checkCancellation()
        if (offset < model.byteSize) {
            progress("Téléchargement du modèle…")
            val request = Request.Builder().url(downloadUrl(model)).apply {
                if (offset > 0) header("Range", "bytes=$offset-")
            }.build()
            val active = client.newCall(request)
            call = active
            checkCancellation()
            active.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Téléchargement : HTTP ${response.code}. Réessaie avec une connexion Internet.")
                val resumed = response.code == 206 && offset > 0
                if (response.code == 206 && !response.header("Content-Range").orEmpty().startsWith("bytes $offset-")) {
                    throw IOException("Le serveur a renvoyé une plage de téléchargement incorrecte.")
                }
                val body = response.body ?: throw IOException("Le téléchargement est vide.")
                RandomAccessFile(partial, "rw").use { out ->
                    if (resumed) out.seek(offset) else out.setLength(0)
                    var total = if (resumed) offset else 0L
                    var lastPercent = -1
                    body.byteStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            checkCancellation()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > model.byteSize) throw IOException("Taille du modèle inattendue.")
                            out.write(buffer, 0, count)
                            val percent = (total * 100 / model.byteSize).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                progress("Téléchargement : $percent % (${total / 1_000_000} / ${model.byteSize / 1_000_000} Mo)")
                            }
                        }
                    }
                    out.fd.sync()
                }
            }
            call = null
        }
        if (partial.length() != model.byteSize) throw IOException("Téléchargement interrompu. Appuie sur Démarrer pour le reprendre.")
        progress("Vérification du modèle…")
        val digest = MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (hash != model.sha256) {
            partial.delete()
            throw IOException("Le fichier du modèle est endommagé. Relance le téléchargement.")
        }
        val file = File(directory, model.fileName)
        if (file.exists() && !file.delete()) throw IOException("Impossible de remplacer le modèle.")
        if (!partial.renameTo(file)) throw IOException("Impossible d'enregistrer le modèle.")
        File(directory, model.fileName + ".sha256").writeText(hash)
        return file
    }

    private fun checkCancellation() {
        if (cancelled || Thread.currentThread().isInterrupted) throw IOException("Démarrage annulé.")
    }
}
