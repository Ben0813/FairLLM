package be.itspace.fairllm

data class EngineOptions(val useGpu: Boolean, val gpuLayers: Int, val threads: Int) {
    fun arguments(model: ModelPreset): List<String> = listOf(
        "-ngl", if (useGpu) gpuLayers.toString() else "0",
        "-c", model.contextSize.toString(), "-t", threads.toString(),
        "-tb", threads.toString(), "-b", "256", "-ub", "64", "-fa", "auto",
    )
    companion object {
        fun choose(mode: String, gpuName: String, layers: Int, processors: Int) = EngineOptions(
            useGpu = mode != "cpu" && gpuName.isNotBlank(),
            gpuLayers = layers.coerceIn(1, 99), threads = processors.coerceIn(1, 4),
        )
    }
}
