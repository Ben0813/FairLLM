package be.itspace.fairllm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AgentProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val instructions: String = "Réponds en français, clairement et utilement.",
    val modelSha: String,
    val temperature: Float = 0.7f,
    val topP: Float = 0.8f,
    val maxTokens: Int = 512,
    val antiHallucination: Boolean = true,
) {
    fun systemPrompt(): String = listOfNotNull(
        instructions.trim().takeIf { it.isNotEmpty() },
        ANTI_HALLUCINATION_PROMPT.takeIf { antiHallucination },
    ).joinToString("\n\n")

    fun validate() {
        require(name.isNotBlank() && name.length <= 80) { "Donne un nom à l’agent (80 caractères maximum)." }
        require(instructions.length <= 12000) { "Les consignes sont trop longues." }
        require(temperature in 0f..1.2f && topP in 0.1f..1f && maxTokens in 128..1024) { "Paramètres de l’agent invalides." }
        require(Regex("[a-f0-9]{64}").matches(modelSha)) { "Choisis un modèle." }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("instructions", instructions); put("modelSha", modelSha)
        put("temperature", temperature.toDouble()); put("topP", topP.toDouble())
        put("maxTokens", maxTokens); put("antiHallucination", antiHallucination)
    }

    companion object {
        fun fromJson(json: JSONObject): AgentProfile = AgentProfile(
            id = json.getString("id"), name = json.getString("name"), instructions = json.getString("instructions"),
            modelSha = json.getString("modelSha"), temperature = json.getDouble("temperature").toFloat(),
            topP = json.getDouble("topP").toFloat(), maxTokens = json.getInt("maxTokens"),
            antiHallucination = json.getBoolean("antiHallucination"),
        ).also { it.validate() }
    }
}

class AgentStore(context: Context) {
    private val prefs = context.getSharedPreferences("agents", Context.MODE_PRIVATE)
    fun load(defaultModel: ModelPreset, settings: AppSettings): List<AgentProfile> {
        val saved = prefs.getString("profiles", null) ?: return listOf(AgentProfile(
            id = "default", name = "Assistant", modelSha = defaultModel.sha256,
            temperature = settings.temperature, topP = settings.topP, maxTokens = settings.maxTokens,
            antiHallucination = settings.antiHallucination,
        ))
        val json = JSONArray(saved)
        return (0 until json.length()).map { AgentProfile.fromJson(json.getJSONObject(it)) }.also {
            require(it.isNotEmpty()) { "Il faut conserver au moins un agent." }
        }
    }
    fun save(agents: List<AgentProfile>) {
        require(agents.isNotEmpty())
        agents.forEach { it.validate() }
        val json = JSONArray(); agents.forEach { json.put(it.toJson()) }
        check(prefs.edit().putString("profiles", json.toString()).commit()) { "Impossible d’enregistrer les agents." }
    }
    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(value) { prefs.edit().putString("selected", value).apply() }
}
