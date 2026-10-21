package fr.ben0813.localia

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.net.ssl.SSLException

data class LocaliaState(
    val loading: Boolean = true, val vault: Vault = Vault(), val vaultError: Boolean = false,
    val tab: Int = 0, val selectedId: String? = null, val draft: String = "",
    val models: List<ModelInfo> = emptyList(), val connecting: Boolean = false, val verified: Boolean = false,
    val generating: Boolean = false, val streamingText: String = "", val pendingText: String = "",
    val attachment: Attachment? = null, val readingFile: Boolean = false,
    val activity: String? = null, val notice: String? = null, val speed: Double? = null,
) {
    val chat: Conversation? get() = vault.chats.find { it.id == selectedId }
}

class LocaliaViewModel(app: Application) : AndroidViewModel(app) {
    private val store = VaultStore(app)
    private val api = SecureApi()
    private val _state = MutableStateFlow(LocaliaState())
    val state = _state.asStateFlow()
    private val mutations = Mutex()
    private var job: Job? = null
    @Volatile private var generationId = 0
    @Volatile private var attachmentId = 0
    private var submittedDraft = ""

    init { viewModelScope.launch {
        try { val vault = store.read(); _state.update { it.copy(vault = vault, loading = false) } }
        catch (_: Exception) { _state.update { it.copy(loading = false, vaultError = true) } }
    } }

    private suspend fun changeVault(transform: (Vault) -> Vault) = withContext(NonCancellable) { mutations.withLock {
        check(!_state.value.vaultError && !_state.value.loading)
        val old = _state.value.vault
        val next = transform(old).copy(revision = old.revision + 1)
        store.save(next)
        _state.update { it.copy(vault = next) }
    } }
    fun notice(message: String) { _state.update { it.copy(notice = message) } }
    fun dismissNotice() { _state.update { it.copy(notice = null) } }
    fun tab(index: Int) { _state.update { it.copy(tab = index) } }
    fun draft(text: String) { if (!_state.value.generating) _state.update { it.copy(draft = text.take(24000)) } }
    fun removeAttachment() { attachmentId++; _state.update { it.copy(attachment = null, readingFile = false) } }

    fun connect(candidate: Connection) {
        if (_state.value.connecting || _state.value.generating) return
        _state.update { it.copy(connecting = true, verified = false) }
        viewModelScope.launch {
            try {
                val url = SecureEndpoint.parse(candidate.url).toString().trimEnd('/')
                val clean = candidate.copy(url = url, token = candidate.token.trim())
                val models = api.models(clean)
                check(models.isNotEmpty()) { "Aucun modèle de chat disponible. Télécharge un modèle dans LM Studio." }
                val model = clean.model.takeIf { id -> models.any { it.key == id } }
                    ?: models.firstOrNull { it.loaded }?.key ?: models.first().key
                val previousBinding = _state.value.vault.connection.binding()
                changeVault { it.copy(connection = clean.copy(model = model)) }
                _state.update { it.copy(models = models, verified = true,
                    selectedId = if (previousBinding == it.vault.connection.binding()) it.selectedId else null,
                    notice = "Connexion HTTPS vérifiée · ${models.size} modèle(s) disponible(s).") }
            } catch (e: Exception) { notice(friendly(e, candidate.token)) }
            finally { _state.update { it.copy(connecting = false) } }
        }
    }
    fun selectModel(model: String) {
        if (_state.value.generating || _state.value.models.none { it.key == model }) return
        viewModelScope.launch {
            try {
                changeVault { it.copy(connection = it.connection.copy(model = model)) }
                newChat()
            } catch (e: Exception) { notice(friendly(e)) }
        }
    }
    fun saveOptions(enabled: Boolean, names: String, prompt: String, temperature: Double, maxTokens: Int) {
        if (_state.value.generating) return
        viewModelScope.launch {
            try {
                val c = _state.value.vault.connection.copy(extensionsEnabled = enabled, extensionNames = names.trim(),
                    prompt = prompt.take(12000), temperature = temperature, maxTokens = maxTokens)
                require(!enabled || c.integrationIds().isNotEmpty()) { "Ajoute le nom de l’extension sous mcpServers dans LM Studio." }
                changeVault { it.copy(connection = c) }
                newChat()
                notice("Préférences enregistrées. La prochaine conversation utilisera ces réglages.")
            } catch (e: Exception) { notice(friendly(e)) }
        }
    }
    fun screenProtection(enabled: Boolean) { viewModelScope.launch {
        try { changeVault { it.copy(protectScreen = enabled) } }
        catch (e: Exception) { notice(friendly(e)) }
    } }
    fun newChat() {
        cancel()
        removeAttachment()
        _state.update { it.copy(selectedId = null, draft = "", tab = 0, activity = null, speed = null) }
    }
    fun openChat(id: String) {
        cancel(); removeAttachment()
        _state.update { it.copy(selectedId = id, tab = 0, draft = "", activity = null, speed = null) }
    }
    fun deleteChat(id: String) { viewModelScope.launch {
        try {
            if (_state.value.selectedId == id) newChat()
            changeVault { it.copy(chats = it.chats.filterNot { chat -> chat.id == id }) }
        } catch (e: Exception) { notice(friendly(e)) }
    } }
    fun pinChat(id: String) { viewModelScope.launch {
        try { changeVault { it.copy(chats = it.chats.map { chat -> if (chat.id == id) chat.copy(pinned = !chat.pinned) else chat }) } }
        catch (e: Exception) { notice(friendly(e)) }
    } }
    fun renameChat(id: String, title: String) { viewModelScope.launch {
        try { changeVault { it.copy(chats = it.chats.map { chat -> if (chat.id == id) chat.copy(title = title.trim().take(100).ifBlank { chat.title }) else chat }) } }
        catch (e: Exception) { notice(friendly(e)) }
    } }

    fun attach(uri: Uri) {
        if (_state.value.generating) return
        val serial = ++attachmentId
        _state.update { it.copy(readingFile = true) }
        viewModelScope.launch {
            try {
                val attachment = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0).take(100) else "Document"
                    } ?: "Document"
                    val type = resolver.getType(uri) ?: "text/plain"
                    val image = type in listOf("image/jpeg", "image/png", "image/webp")
                    val limit = if (image) 4 * 1024 * 1024 else 200 * 1024
                    val bytes = resolver.openInputStream(uri)?.use { stream ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = stream.read(chunk)
                            if (n < 0) break
                            require(buffer.size() + n <= limit) { "Fichier trop volumineux : 4 Mo pour les images, 200 Ko pour le texte." }
                            buffer.write(chunk, 0, n)
                        }
                        buffer.toByteArray()
                    } ?: throw IOException("Impossible de lire le fichier.")
                    val content = if (image) "data:$type;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}" else {
                        require(bytes.none { it == 0.toByte() }) { "Choisis un fichier texte UTF-8 ou une image PNG/JPEG/WebP. Les PDF ne sont pas pris en charge dans cette version." }
                        Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    }
                    Attachment(name, content, image)
                }
                if (serial == attachmentId) _state.update { it.copy(attachment = attachment) }
            } catch (e: Exception) { if (serial == attachmentId) notice(friendly(e)) }
            finally { if (serial == attachmentId) _state.update { it.copy(readingFile = false) } }
        }
    }

    fun send() {
        val initial = _state.value
        if (initial.generating || initial.readingFile || initial.loading || initial.vaultError) return
        val text = initial.draft.trim()
        val attachment = initial.attachment
        if (text.isEmpty() && attachment == null) return
        val connection = initial.vault.connection
        if (connection.url.isBlank() || connection.model.isBlank()) { tab(2); notice("Connecte ton PC pour commencer."); return }
        val oldChat = initial.chat
        if (oldChat != null && oldChat.binding != connection.binding()) {
            notice("La configuration a changé. Crée une nouvelle conversation pour utiliser ce serveur et ces réglages."); return
        }
        val knownModel = initial.models.find { it.key == connection.model }
        if (attachment?.image == true && knownModel?.vision == false) { notice("Ce modèle ne prend pas en charge les images. Choisis un modèle avec vision."); return }
        val serial = ++generationId
        submittedDraft = initial.draft
        val submitted = text.ifBlank { "Analyse le document joint." }
        val displayText = submitted + (attachment?.let { "\n\nPièce jointe : ${it.name}" } ?: "")
        _state.update { it.copy(generating = true, streamingText = "", pendingText = displayText,
            draft = "", activity = "Connexion chiffrée au PC…", speed = null) }
        job = viewModelScope.launch {
            try {
                val result = api.chat(connection, submitted, attachment, oldChat?.responseId,
                    onText = { value -> if (generationId == serial) _state.update { it.copy(streamingText = value) } },
                    onActivity = { value -> if (generationId == serial) _state.update { it.copy(activity = value) } })
                if (generationId != serial) return@launch
                val messages = (oldChat?.messages ?: emptyList()) + listOf(Message("user", displayText), Message("assistant", result.text))
                val chat = oldChat?.copy(messages = messages, responseId = result.responseId, updated = System.currentTimeMillis())
                    ?: Conversation(title = submitted.take(70), model = connection.model, binding = connection.binding(),
                        responseId = result.responseId, messages = messages)
                changeVault { vault -> vault.copy(chats = listOf(chat) + vault.chats.filterNot { it.id == chat.id }) }
                if (generationId == serial) {
                    removeAttachment()
                    _state.update { it.copy(selectedId = chat.id, speed = result.tokensPerSecond) }
                }
            } catch (e: Exception) {
                if (generationId == serial) {
                    _state.update { it.copy(draft = initial.draft) }
                    if (e !is CancellationException) notice(friendly(e, connection.token))
                }
            } finally {
                if (generationId == serial) _state.update { it.copy(generating = false, streamingText = "", pendingText = "") }
            }
        }
    }
    fun cancel() {
        if (!_state.value.generating) return
        generationId++
        api.cancel(); job?.cancel()
        _state.update { it.copy(generating = false, draft = submittedDraft,
            streamingText = "", pendingText = "", activity = "Réponse interrompue. Tu peux renvoyer ton message.") }
    }
    fun eraseAll() { viewModelScope.launch {
        cancel(); removeAttachment()
        try {
            mutations.withLock { store.erase(); _state.value = LocaliaState(loading = false) }
        } catch (_: Exception) { notice("Impossible d’effacer le coffre. Réessaie depuis les paramètres Android.") }
    } }
    private fun friendly(e: Exception, token: String = _state.value.vault.connection.token): String {
        val message = when (e) {
            is SSLException -> "Certificat HTTPS non valide ou non reconnu. Vérifie l’adresse Tailscale et l’horloge du téléphone. Aucun certificat non vérifié n’est accepté."
            is java.net.UnknownHostException -> "PC introuvable. Vérifie que Tailscale est connecté sur les deux appareils et que l’adresse est correcte."
            is java.net.SocketTimeoutException -> "Le PC met trop de temps à répondre. Vérifie LM Studio puis réessaie."
            is java.net.ConnectException -> "PC inaccessible. Vérifie LM Studio, Tailscale et le proxy HTTPS."
            else -> e.message ?: "Une erreur est survenue. Réessaie."
        }
        return (if (token.isBlank()) message else message.replace(token, "[jeton masqué]")).take(600)
    }
    override fun onCleared() { api.cancel(); super.onCleared() }
}
