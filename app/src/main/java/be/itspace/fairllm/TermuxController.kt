package be.itspace.fairllm

import android.content.Context
import android.content.Intent

class TermuxController(private val context: Context) {
    companion object {
        const val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION = "com.termux.RUN_COMMAND"
        private const val PATH = "com.termux.RUN_COMMAND_PATH"
        private const val ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    }

    fun startServer(model: ModelPreset) {
        val dollar = '$'
        val command = """
            if pgrep -f '/build/bin/[l]lama-server.*--port 8080' >/dev/null 2>&1; then exit 0; fi;
            mkdir -p "${dollar}HOME/fairllm";
            export LD_LIBRARY_PATH="${dollar}HOME/adreno-opencl:${dollar}PREFIX/lib:/vendor/lib64";
            cd "${dollar}HOME/llama.cpp" || exit 2;
            exec ./build/bin/llama-server -hf '${model.hf}' -ngl ${model.gpuLayers} -c ${model.contextSize} --host 127.0.0.1 --port 8080 >> "${dollar}HOME/fairllm/server.log" 2>&1
        """.trimIndent().replace("\n", " ")
        runBash(command)
    }

    fun stopServer() {
        val command = "pkill -f '/build/bin/[l]lama-server.*--port 8080' 2>/dev/null || true"
        runBash(command)
    }

    private fun runBash(command: String) {
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
            action = ACTION
            putExtra(PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(ARGUMENTS, arrayOf("-lc", command))
            putExtra(WORKDIR, "/data/data/com.termux/files/home")
            putExtra(BACKGROUND, true)
        }
        context.startService(intent)
    }
}
