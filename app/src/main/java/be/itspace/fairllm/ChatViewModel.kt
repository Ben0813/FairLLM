package be.itspace.fairllm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val api = LlamaApi(engineApiKey(app))
    val settings = AppSettings(app)
    private val catalog = ModelCatalog(app)
    private val _models = MutableStateFlow(MODEL_PRESETS)
    val models = _models.asStateFlow()
    private val _selectedIndex = MutableStateFlow(settings.modelIndex.coerceIn(MODEL_PRESETS.indices))
    val selectedIndex = _selectedIndex.asStateFlow()
    val selectedModel: ModelPreset get() = _models.value[_selectedIndex.value]
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _importFiles = MutableStateFlow<List<ModelPreset>>(emptyList())
    val importFiles = _importFiles.asStateFlow()
    private val _importError = MutableStateFlow<String?>(null)
    val importError = _importError.asStateFlow()
    private var importJob: Job? = null
    private val _downloaded = MutableStateFlow<Set<String>>(emptySet())
    val downloaded = _downloaded.asStateFlow()
    private val agentStore = AgentStore(app)
    private val _agents = MutableStateFlow(listOf(AgentProfile(id = "default", name = "Assistant", modelSha = MODEL_PRESETS[1].sha256)))
    val agents = _agents.asStateFlow()
    private val _selectedAgent = MutableStateFlow(_agents.value.first())
    val selectedAgent = _selectedAgent.asStateFlow()
    private val conversations = mutableMapOf<String, List<ChatMessage>>()
    private val engine = EngineController(app) { state, detail, index ->
        _engineDetail.value = detail
        _status.value = when (state) {
            "ready" -> ServerStatus.Ready
            "loading" -> ServerStatus.Loading
            else -> ServerStatus.Offline
        }
        if (index in _models.value.indices && state != "offline") {
            settings.modelIndex = index
            _selectedIndex.value = index
        }
        if (state == "ready" || state == "error") refreshDownloads()
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
        try {
            _models.value = catalog.models()
            _selectedIndex.value = settings.modelIndex.coerceIn(_models.value.indices)
            settings.modelIndex = _selectedIndex.value
        } catch (e: Exception) { _error.value = "Impossible de lire les modèles ajoutés : ${e.message}" }
        try {
            _agents.value = agentStore.load(selectedModel, settings)
            _selectedAgent.value = _agents.value.find { it.id == agentStore.selectedId } ?: _agents.value.first()
            val index = _models.value.indexOfFirst { it.sha256 == _selectedAgent.value.modelSha }
            if (index >= 0) { _selectedIndex.value = index; settings.modelIndex = index }
        } catch (e: Exception) { _error.value = "Impossible de lire les agents : ${e.message}" }
        refreshDownloads()
        settings.serverUrl = LOCAL_SERVER_URL
        engine.connect()
    }

    fun refreshStatus() {
        engine.connect()
    }

    fun modelIsDownloaded(): Boolean = ModelStore(getApplication()).ready(selectedModel)

    private fun refreshDownloads() {
        val store = ModelStore(getApplication())
        _downloaded.value = _models.value.filter { store.ready(it) }.map { it.sha256 }.toSet()
    }

    fun selectModel(index: Int) {
        if (_status.value != ServerStatus.Offline || index !in _models.value.indices) return
        val updated = _selectedAgent.value.copy(modelSha = _models.value[index].sha256)
        if (!saveAgent(updated)) return
        settings.modelIndex = index
        _selectedIndex.value = index
        clearChat()
    }

    fun selectAgent(id: String) {
        val profile = _agents.value.find { it.id == id } ?: return
        if (profile.id == _selectedAgent.value.id) return
        if (_status.value == ServerStatus.Loading) { _error.value = "Termine ou annule le démarrage avant de changer d’agent."; return }
        val index = _models.value.indexOfFirst { it.sha256 == profile.modelSha }
        if (index < 0) { _error.value = "Le modèle de cet agent n’est plus disponible."; return }
        cancelGeneration()
        conversations[_selectedAgent.value.id] = _messages.value
        if (index != _selectedIndex.value && _status.value != ServerStatus.Offline) stopServer()
        _selectedAgent.value = profile; agentStore.selectedId = profile.id
        _selectedIndex.value = index; settings.modelIndex = index
        _messages.value = conversations[profile.id].orEmpty()
    }

    fun saveAgent(profile: AgentProfile): Boolean {
        if (_status.value == ServerStatus.Loading || _generating.value) { _error.value = "Arrête la réponse ou le démarrage avant de modifier un agent."; return false }
        return try {
            profile.validate()
            require(_models.value.any { it.sha256 == profile.modelSha }) { "Choisis un modèle disponible." }
            val exists = _agents.value.any { it.id == profile.id }
            val next = if (exists) _agents.value.map { if (it.id == profile.id) profile else it } else _agents.value + profile
            agentStore.save(next)
            _agents.value = next
            if (_selectedAgent.value.id == profile.id) {
                if (_selectedAgent.value.modelSha != profile.modelSha) {
                    if (_status.value != ServerStatus.Offline) stopServer()
                    clearChat()
                }
                _selectedAgent.value = profile
                val index = _models.value.indexOfFirst { it.sha256 == profile.modelSha }
                _selectedIndex.value = index; settings.modelIndex = index
            }
            true
        } catch (e: Exception) { _error.value = e.message ?: "Impossible d’enregistrer l’agent."; false }
    }

    fun deleteAgent(id: String) {
        if (_agents.value.size <= 1) { _error.value = "Garde au moins un agent."; return }
        if (_status.value == ServerStatus.Loading || _generating.value) { _error.value = "Arrête la réponse ou le démarrage avant de supprimer un agent."; return }
        try {
            val next = _agents.value.filter { it.id != id }
            agentStore.save(next)
            _agents.value = next
            if (_selectedAgent.value.id == id) selectAgent(next.first().id)
            conversations.remove(id)
        } catch (e: Exception) { _error.value = e.message }
    }

    fun findModels(input: String) {
        importJob?.cancel()
        _importing.value = true; _importError.value = null; _importFiles.value = emptyList()
        importJob = viewModelScope.launch {
            try {
                _importFiles.value = withContext(Dispatchers.IO) { HuggingFaceModels().list(input) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _importError.value = e.message ?: "Impossible de lire Hugging Face." }
            finally { if (kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) _importing.value = false }
        }
    }

    fun closeImport() {
        importJob?.cancel(); _importing.value = false
        _importFiles.value = emptyList(); _importError.value = null
    }

    fun addModel(model: ModelPreset): Boolean {
        if (_status.value != ServerStatus.Offline) return false
        return try {
            _models.value = catalog.add(model)
            selectModel(_models.value.indexOfFirst { it.sha256 == model.sha256 })
            refreshDownloads()
            true
        } catch (e: Exception) { _importError.value = "Impossible d’enregistrer le modèle : ${e.message}"; false }
    }

    fun deleteDownload(model: ModelPreset) {
        if (_status.value != ServerStatus.Offline) return
        try { ModelStore(getApplication()).delete(model); refreshDownloads() }
        catch (e: Exception) { _error.value = e.message }
    }

    fun startServer() {
        if (_status.value != ServerStatus.Offline) return
        if (selectedModel.sha256 != _selectedAgent.value.modelSha) {
            _error.value = "Choisis de nouveau un modèle pour cet agent avant de démarrer."
            return
        }
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
                    systemPrompt = _selectedAgent.value.systemPrompt(),
                    temperature = _selectedAgent.value.temperature,
                    topP = _selectedAgent.value.topP,
                    maxTokens = _selectedAgent.value.maxTokens,
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
        if (_messages.value.lastOrNull()?.let { it.role == "assistant" && it.content.isBlank() } == true) {
            _messages.value = _messages.value.dropLast(1)
        }
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

