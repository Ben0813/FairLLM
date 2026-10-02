package be.itspace.fairllm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val api = LlamaApi(engineApiKey(app))
    val settings = AppSettings(app)
    private val engine = EngineController(app) { state, detail, index ->
        _engineDetail.value = detail
        _status.value = when (state) {
            "ready" -> ServerStatus.Ready
            "loading" -> ServerStatus.Loading
            else -> ServerStatus.Offline
        }
        if (index in MODEL_PRESETS.indices && state != "offline") settings.modelIndex = index
        if (state == "error") _error.value = detail
    }
    private val _engineDetail = MutableStateFlow("")
    val engineDetail: StateFlow<String> = _engineDetail.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Offline)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var streamJob: Job? = null
    @Volatile private var generationId = 0

    init {
        settings.serverUrl = LOCAL_SERVER_URL
        engine.connect()
    }

    fun refreshStatus() {
        engine.connect()
    }

    fun modelIsDownloaded(): Boolean = ModelStore(getApplication()).ready(MODEL_PRESETS[settings.modelIndex])

    fun startServer() {
        if (_status.value != ServerStatus.Offline) return
        _error.value = null
        try {
            _status.value = ServerStatus.Loading
            _engineDetail.value = "Préparation du modèle…"
            engine.start(settings.modelIndex)
        } catch (e: Exception) {
            _status.value = ServerStatus.Offline
            _error.value = "Impossible de lancer le moteur : ${e.message}"
        }
    }

    fun stopServer() {
        cancelGeneration()
        engine.stop()
        _engineDetail.value = "Moteur arrêté."
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
        val requestId = ++generationId

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
                    if (requestId != generationId) return@streamChat
                    answer += token
                    val copy = _messages.value.toMutableList()
                    if (copy.isNotEmpty()) copy[copy.lastIndex] = ChatMessage("assistant", answer)
                    _messages.value = copy
                }
            } catch (e: Exception) {
                if (requestId == generationId && _generating.value) _error.value = e.message ?: "Erreur pendant la génération"
            } finally {
                if (requestId == generationId) _generating.value = false
            }
        }
    }

    fun cancelGeneration() {
        generationId++
        api.cancel()
        streamJob?.cancel()
        _generating.value = false
    }

    fun clearChat() {
        cancelGeneration()
        _messages.value = emptyList()
        _generating.value = false
    }

    override fun onCleared() {
        cancelGeneration()
        engine.close()
        super.onCleared()
    }

    fun dismissError() { _error.value = null }
}

