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
    private val _remoteModels = MutableStateFlow<List<String>>(emptyList())
    val remoteModels: StateFlow<List<String>> = _remoteModels.asStateFlow()

    fun changeMode(remote: Boolean) {
        cancelGeneration()
        settings.remoteMode = remote
        _messages.value = emptyList()
        refreshStatus()
        if (remote) refreshModels()
    }

    fun refreshModels() {
        if (!settings.remoteMode) return
        viewModelScope.launch {
            try {
                val found = api.listModels(settings.remoteUrl, settings.remoteToken)
                _remoteModels.value = found
                if (settings.remoteModel !in found && found.isNotEmpty()) settings.remoteModel = found.first()
                if (found.isEmpty()) _error.value = "Aucun modèle disponible. Charge un modèle dans LM Studio."
            } catch (e: Exception) { _error.value = "Connexion LM Studio : ${e.message}" }
        }
    }
    fun updateRemote(url: String, token: String) {
        settings.remoteUrl = url
        settings.remoteToken = token
        refreshStatus()
        refreshModels()
    }
    fun selectRemoteModel(model: String) { settings.remoteModel = model }


    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _status.value = api.health(settings.activeUrl, if (settings.remoteMode) settings.remoteToken else "")
        }
    }

    fun startServer() {
        if (settings.remoteMode) { refreshModels(); refreshStatus(); return }
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
        if (settings.remoteMode) return
        api.cancel()
        try { termux.stopServer() } catch (_: Exception) {}
        _status.value = ServerStatus.Offline
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _generating.value) return
        if (_status.value != ServerStatus.Ready) {
            _error.value = if (settings.remoteMode) "LM Studio inaccessible : vérifie l’adresse et le serveur." else "Démarre d’abord le moteur local."
            return
        }

        if (settings.remoteMode && settings.remoteModel.isBlank()) {
            _error.value = "Choisis un modèle LM Studio dans Réglages."
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
                    baseUrl = settings.activeUrl,
                    model = if (settings.remoteMode) settings.remoteModel else "local",
                    apiKey = if (settings.remoteMode) settings.remoteToken else "",
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
