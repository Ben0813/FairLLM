package be.itspace.fairllm

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("fairllm", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("serverUrl", "http://127.0.0.1:8080") ?: "http://127.0.0.1:8080"
        set(value) = prefs.edit().putString("serverUrl", value).apply()

    var modelIndex: Int
        get() = prefs.getInt("modelIndex", 1).coerceIn(MODEL_PRESETS.indices)
        set(value) = prefs.edit().putInt("modelIndex", value).apply()

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.7f)
        set(value) = prefs.edit().putFloat("temperature", value).apply()

    var topP: Float
        get() = prefs.getFloat("topP", 0.8f)
        set(value) = prefs.edit().putFloat("topP", value).apply()

    var maxTokens: Int
        get() = prefs.getInt("maxTokens", 512)
        set(value) = prefs.edit().putInt("maxTokens", value).apply()

    var antiHallucination: Boolean
        get() = prefs.getBoolean("antiHallucination", true)
        set(value) = prefs.edit().putBoolean("antiHallucination", value).apply()
}

