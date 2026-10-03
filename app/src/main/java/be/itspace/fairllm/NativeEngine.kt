package be.itspace.fairllm

object NativeEngine {
    init { System.loadLibrary("fairllm") }
    external fun run(args: Array<String>, logPath: String): Int
    external fun gpuName(cpuOnly: Boolean): String
}
