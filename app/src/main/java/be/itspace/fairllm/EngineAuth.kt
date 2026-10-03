package be.itspace.fairllm

import android.content.Context
import java.io.File
import java.util.UUID

const val LOCAL_SERVER_URL = "http://127.0.0.1:18080"

fun engineApiKey(context: Context): String {
    val file = File(context.filesDir, "engine.key")
    // The UI creates the key before launching the engine process.
    if (!file.exists()) file.writeText(UUID.randomUUID().toString())
    return file.readText().trim()
}
