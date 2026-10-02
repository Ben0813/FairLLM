package be.itspace.fairllm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val api = LlamaApi()
    val settings = AppSettings(app)
    val termux = TermuxController(app)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Offline)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var streamJob: Job? = null

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _status.value = api.health(settings.serverUrl)
        }
    }

    fun startServer() {
        _error.value = null
        try {
            termux.startServer(MODEL_PRESETS[settings.modelIndex])
            _status.value = ServerStatus.Loading
            viewModelScope.launch {
                repeat(60) {
                    delay(1000)
                    val s = api.health(settings.serverUrl)
                    _status.value = s
                    if (s == ServerStatus.Ready) return@launch
                }
                _error.value = "Le serveur ne répond pas. Vérifie Termux et ~/fairllm/server.log."
            }
        } catch (e: Exception) {
            _error.value = "Impossible de lancer Termux : ${e.message}"
        }
    }

    fun stopServer() {
        api.cancel()
        try { termux.stopServer() } catch (_: Exception) {}
        _status.value = ServerStatus.Offline
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _generating.value) return
        if (_status.value != ServerStatus.Ready) {
            _error.value = "Démarre d'abord le moteur local."
            return
        }

        _error.value = null
        val baseMessages = _messages.value + ChatMessage("user", trimmed)
        _messages.value = baseMessages + ChatMessage("assistant", "")
        _generating.value = true

        streamJob = viewModelScope.launch {
            try {
                var answer = ""
                api.streamChat(
                    baseUrl = settings.serverUrl,
                    messages = baseMessages,
                    systemPrompt = if (settings.antiHallucination) ANTI_HALLUCINATION_PROMPT else null,
                    temperature = settings.temperature,
                    topP = settings.topP,
                    maxTokens = settings.maxTokens,
                ) { token ->
                    answer += token
                    val copy = _messages.value.toMutableList()
                    if (copy.isNotEmpty()) copy[copy.lastIndex] = ChatMessage("assistant", answer)
                    _messages.value = copy
                }
            } catch (e: Exception) {
                if (_generating.value) _error.value = e.message ?: "Erreur pendant la génération"
            } finally {
                _generating.value = false
            }
        }
    }

    fun cancelGeneration() {
        api.cancel()
        streamJob?.cancel()
        _generating.value = false
    }

    fun clearChat() {
        api.cancel()
        _messages.value = emptyList()
        _generating.value = false
    }

    fun dismissError() { _error.value = null }
}
