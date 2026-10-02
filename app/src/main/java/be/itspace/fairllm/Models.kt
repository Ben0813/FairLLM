package be.itspace.fairllm

data class ChatMessage(val role: String, val content: String)

data class ModelPreset(
    val title: String,
    val hf: String,
    val gpuLayers: Int,
    val contextSize: Int,
    val subtitle: String,
    val fileName: String,
    val revision: String,
    val byteSize: Long,
    val sha256: String,
    val storageName: String = fileName,
) {
    val downloadUrl: String
        get() = okhttp3.HttpUrl.Builder().scheme("https").host("huggingface.co")
            .addPathSegments(hf.substringBefore(':')).addPathSegment("resolve")
            .addPathSegment(revision).apply { fileName.split('/').forEach { addPathSegment(it) } }
            .build().toString()
}

val MODEL_PRESETS = listOf(
    ModelPreset(
        title = "Qwen3 4B Instruct 2507",
        hf = "bartowski/Qwen_Qwen3-4B-Instruct-2507-GGUF:Q4_K_M",
        gpuLayers = 24,
        contextSize = 2048,
        subtitle = "Meilleure qualité • Q4_K_M • téléchargement de 2,5 Go",
        fileName = "Qwen_Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        revision = "ae44f08e1392f39c0e474af10c3ff8355c8b6688",
        byteSize = 2497280736L,
        sha256 = "2fde00ce69dd4899c70d020845e2638353015bba0fdf161b3eb965f2bca4464e"
    ),
    ModelPreset(
        title = "Qwen3 1.7B",
        hf = "ggml-org/Qwen3-1.7B-GGUF:Q4_K_M",
        gpuLayers = 99,
        contextSize = 2048,
        subtitle = "Plus léger • Q4_K_M • téléchargement de 1,3 Go",
        fileName = "Qwen3-1.7B-Q4_K_M.gguf",
        revision = "daeb8e2d528a760970442092f6bf1e55c3b659eb",
        byteSize = 1282439264L,
        sha256 = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"
    )
)

const val ANTI_HALLUCINATION_PROMPT = """Tu es un assistant hors ligne. Tu n'as pas accès à Internet, à la météo en direct, aux actualités, aux prix actuels ni aux événements postérieurs à tes connaissances d'entraînement. N'invente jamais une information manquante. Si une information dépend de données actuelles ou si tu n'es pas certain, réponds explicitement que tu ne sais pas ou que tu n'as pas accès à cette information. Distingue clairement les faits connus, les suppositions et les informations nécessitant une source externe. Réponds en français sauf demande contraire."""

