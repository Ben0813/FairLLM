package be.itspace.fairllm

data class ChatMessage(val role: String, val content: String)

data class ModelPreset(
    val title: String,
    val hf: String,
    val gpuLayers: Int,
    val contextSize: Int,
    val subtitle: String,
)

val MODEL_PRESETS = listOf(
    ModelPreset(
        title = "Qwen3 4B Instruct 2507",
        hf = "bartowski/Qwen_Qwen3-4B-Instruct-2507-GGUF:Q4_K_M",
        gpuLayers = 24,
        contextSize = 2048,
        subtitle = "Meilleure qualité • Q4_K_M • ~2,5 Go"
    ),
    ModelPreset(
        title = "Qwen3 1.7B",
        hf = "ggml-org/Qwen3-1.7B-GGUF:Q4_K_M",
        gpuLayers = 99,
        contextSize = 2048,
        subtitle = "Plus rapide • Q4_K_M • ~1,3 Go"
    )
)

const val ANTI_HALLUCINATION_PROMPT = """Tu es un assistant hors ligne. Tu n'as pas accès à Internet, à la météo en direct, aux actualités, aux prix actuels ni aux événements postérieurs à tes connaissances d'entraînement. N'invente jamais une information manquante. Si une information dépend de données actuelles ou si tu n'es pas certain, réponds explicitement que tu ne sais pas ou que tu n'as pas accès à cette information. Distingue clairement les faits connus, les suppositions et les informations nécessitant une source externe. Réponds en français sauf demande contraire."""
