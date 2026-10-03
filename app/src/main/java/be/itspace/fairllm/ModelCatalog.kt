package be.itspace.fairllm

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ModelCatalog(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "model-catalog.json"))

    fun models(): List<ModelPreset> {
        if (!file.baseFile.exists()) return MODEL_PRESETS
        val entries = file.openRead().bufferedReader().use { JSONArray(it.readText()) }
        return MODEL_PRESETS + (0 until entries.length()).map { decode(entries.getJSONObject(it)) }
    }

    fun add(model: ModelPreset): List<ModelPreset> {
        val current = models()
        if (current.any { it.sha256 == model.sha256 }) return current
        val next = current + model
        val entries = JSONArray()
        next.drop(MODEL_PRESETS.size).forEach { entries.put(encode(it)) }
        val output = file.startWrite()
        try {
            output.write(entries.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) { file.failWrite(output); throw error }
        return next
    }

    private fun encode(model: ModelPreset) = JSONObject().apply {
        put("title", model.title); put("hf", model.hf); put("contextSize", model.contextSize)
        put("subtitle", model.subtitle); put("fileName", model.fileName); put("revision", model.revision)
        put("byteSize", model.byteSize); put("sha256", model.sha256); put("storageName", model.storageName)
    }

    private fun decode(json: JSONObject) = ModelPreset(
        title = json.getString("title"), hf = json.getString("hf"), gpuLayers = 0,
        contextSize = json.getInt("contextSize"), subtitle = json.getString("subtitle"),
        fileName = json.getString("fileName"), revision = json.getString("revision"),
        byteSize = json.getLong("byteSize"), sha256 = json.getString("sha256"), storageName = json.getString("storageName"),
    )
}
