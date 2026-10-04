package be.itspace.fairllm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val FairColors = darkColorScheme(
    primary = Color(0xFF7CFFB2), onPrimary = Color(0xFF062014), secondary = Color(0xFF8FB7FF),
    background = Color(0xFF0B0D12), surface = Color(0xFF121722), surfaceVariant = Color(0xFF1A2230),
    onBackground = Color(0xFFF4F7FB), onSurface = Color(0xFFF4F7FB),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = FairColors) { FairLLMApp() } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FairLLMApp(vm: ChatViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val status by vm.status.collectAsState()
    val error by vm.error.collectAsState()
    val keyboard = WindowInsets.isImeVisible
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!keyboard) NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.ChatBubbleOutline, null) }, label = { Text("Chat") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Memory, null) }, label = { Text("Modèles") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.PersonOutline, null) }, label = { Text("Agents") })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                when (tab) {
                    0 -> ChatScreen(vm, status, keyboard)
                    1 -> ModelsScreen(vm, status)
                    else -> AgentsScreen(vm, onChat = { tab = 0 })
                }
            }
        }
    }
    if (error != null) AlertDialog(onDismissRequest = vm::dismissError,
        title = { Text("FairLLM") }, text = { Text(error.orEmpty()) },
        confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } })
}

@Composable
private fun StatusLabel(status: ServerStatus) {
    val (label, color) = when (status) {
        ServerStatus.Ready -> "Prêt" to MaterialTheme.colorScheme.primary
        ServerStatus.Loading -> "Chargement…" to Color(0xFFFFD580)
        ServerStatus.Offline -> "Arrêté" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
}

@Composable
private fun ChatScreen(vm: ChatViewModel, status: ServerStatus, keyboard: Boolean) {
    val messages by vm.messages.collectAsState()
    val generating by vm.generating.collectAsState()
    val agents by vm.agents.collectAsState()
    val agent by vm.selectedAgent.collectAsState()
    val index by vm.selectedIndex.collectAsState()
    val models by vm.models.collectAsState()
    var input by rememberSaveable(agent.id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val speed by vm.generationSpeed.collectAsState()
    val send = {
        if (input.isNotBlank() && status == ServerStatus.Ready && !generating) {
            vm.send(input); input = ""
        }
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length?.div(64)) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (messages.isNotEmpty() && lastVisible >= messages.lastIndex - 1) listState.scrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (keyboard) 4.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { menu = true }, enabled = status != ServerStatus.Loading) {
                    Text(agent.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choisir un agent")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    agents.forEach { profile -> DropdownMenuItem(text = { Text(profile.name) },
                        onClick = { vm.selectAgent(profile.id); menu = false }) }
                }
            }
            StatusLabel(status)
            IconButton(onClick = { confirmClear = true }, enabled = messages.isNotEmpty()) {
                Icon(Icons.Default.DeleteOutline, "Effacer cette conversation")
            }
        }
        if (!keyboard) Text(models[index].title, Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall)
        if (status != ServerStatus.Ready) EngineCard(vm, status, compact = keyboard)
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = if (keyboard) 6.dp else 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Une conversation avec ${agent.name}", style = MaterialTheme.typography.titleMedium)
                    Text(if (status == ServerStatus.Ready) "Écris ton premier message."
                        else "Démarre le modèle pour discuter. Après son téléchargement, tes échanges restent hors ligne.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(messages) { _, message -> MessageBubble(message) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Ton message…") }, shape = RoundedCornerShape(22.dp), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }))
            FilledIconButton(onClick = { if (generating) vm.cancelGeneration() else send() },
                enabled = generating || (status == ServerStatus.Ready && input.isNotBlank()),
                modifier = Modifier.padding(bottom = 4.dp).size(48.dp)) {
                Icon(if (generating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (generating) "Arrêter la réponse" else "Envoyer")
            }
        }
        if (!keyboard && speed != null) Text("${"%.1f".format(speed)} tokens/s", Modifier.padding(start = 18.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("Effacer la conversation ?") }, text = { Text("Les messages de ${agent.name} seront effacés.") },
        confirmButton = { TextButton(onClick = { vm.clearChat(); confirmClear = false }) { Text("Effacer") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } })
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth(if (user) .88f else .98f),
            shape = RoundedCornerShape(20.dp), color = if (user) MaterialTheme.colorScheme.primary.copy(alpha = .13f)
                else MaterialTheme.colorScheme.surfaceVariant) {
            SelectionContainer { Text(message.content.ifBlank { "…" }, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun EngineCard(vm: ChatViewModel, status: ServerStatus, compact: Boolean = false) {
    val detail by vm.engineDetail.collectAsState()
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(if (compact) 8.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!compact) {
                Text("Moteur local", fontWeight = FontWeight.SemiBold)
                Text(vm.selectedModel.subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (detail.isNotBlank()) Text(detail, maxLines = if (compact) 1 else 3,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status == ServerStatus.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    TextButton(onClick = vm::stopServer) { Text("Annuler") }
                } else EngineStartButton(vm)
            }
        }
    }
}

@Composable
private fun EngineStartButton(vm: ChatViewModel, enabled: Boolean = true) {
    var confirm by remember { mutableStateOf(false) }
    Button(enabled = enabled, onClick = { if (vm.modelIsDownloaded()) vm.startServer() else confirm = true }) {
        Icon(Icons.Default.PlayArrow, null); Text(" Démarrer")
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Télécharger le modèle") },
        text = { Text("${vm.selectedModel.title}\n${vm.selectedModel.subtitle}\n\nUtilise de préférence le Wi-Fi. Le modèle sera vérifié puis conservé sur ton téléphone pour fonctionner hors ligne. La taille du fichier ne comprend pas toute la mémoire nécessaire au moteur.") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.startServer() }) { Text("Télécharger et démarrer") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelsScreen(vm: ChatViewModel, status: ServerStatus) {
    val index by vm.selectedIndex.collectAsState()
    val models by vm.models.collectAsState()
    val downloaded by vm.downloaded.collectAsState()
    val agent by vm.selectedAgent.collectAsState()
    var importing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ModelPreset?>(null) }
    var mode by remember { mutableStateOf(vm.settings.engineMode) }
    var layers by remember { mutableFloatStateOf(vm.settings.gpuLayers.toFloat()) }
    val detail by vm.engineDetail.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Mes modèles", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Modèle de l’agent ${agent.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusLabel(status)
                    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
                    Text("Accélération", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mode == "auto", onClick = { mode = "auto"; vm.settings.engineMode = mode }, enabled = status == ServerStatus.Offline,
                            label = { Text("GPU expérimental") })
                        FilterChip(selected = mode == "cpu", onClick = { mode = "cpu"; vm.settings.engineMode = mode }, enabled = status == ServerStatus.Offline,
                            label = { Text("CPU") })
                    }
                    if (mode != "cpu") {
                        Text("Couches sur le GPU : ${layers.toInt()}", style = MaterialTheme.typography.bodySmall)
                        Slider(value = layers, onValueChange = { layers = it; vm.settings.gpuLayers = it.toInt() },
                            valueRange = 1f..99f, enabled = status == ServerStatus.Offline)
                        Text("Vulkan utilise des réglages prudents, potentiellement plus lents et plus gourmands en mémoire. Si les réponses sont incohérentes ou répétitives, choisis CPU puis commence une nouvelle conversation.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (status != ServerStatus.Offline) {
                        Text("Arrête le moteur pour changer de modèle ou supprimer un téléchargement.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = vm::stopServer) { Icon(Icons.Default.Stop, null); Text(" Arrêter le moteur") }
                    } else EngineStartButton(vm)
                    Text("Les modèles sont téléchargés une fois, puis utilisés hors ligne. Les modèles ajoutés peuvent ne pas être compatibles avec ce moteur ou dépasser la mémoire du téléphone.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { OutlinedButton(onClick = { importing = true }, enabled = status == ServerStatus.Offline, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Text(" Ajouter depuis Hugging Face")
        } }
        itemsIndexed(models, key = { _, model -> model.sha256 }) { position, model ->
            Card(onClick = { vm.selectModel(position) }, enabled = status == ServerStatus.Offline,
                colors = CardDefaults.cardColors(containerColor = if (index == position) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (index == position) Icon(Icons.Default.CheckCircle, "Modèle sélectionné", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(model.subtitle, style = MaterialTheme.typography.bodySmall)
                    Text(if (model.sha256 in downloaded) "Téléchargé • disponible hors ligne" else "À télécharger",
                        color = if (model.sha256 in downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    if (model.sha256 in downloaded) TextButton(onClick = { deleting = model }, enabled = status == ServerStatus.Offline) {
                        Text("Supprimer le téléchargement")
                    }
                }
            }
        }
    }
    if (importing) ImportModelDialog(vm, onClose = { importing = false; vm.closeImport() })
    deleting?.let { model -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Supprimer le téléchargement ?") },
        text = { Text("${model.title}\n\nLe fichier sera supprimé du téléphone. Tu pourras le télécharger à nouveau.") },
        confirmButton = { TextButton(onClick = { vm.deleteDownload(model); deleting = null }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentsScreen(vm: ChatViewModel, onChat: () -> Unit) {
    val agents by vm.agents.collectAsState()
    val selected by vm.selectedAgent.collectAsState()
    val models by vm.models.collectAsState()
    val generating by vm.generating.collectAsState()
    val status by vm.status.collectAsState()
    val editable = !generating && status != ServerStatus.Loading
    var editing by remember { mutableStateOf<AgentProfile?>(null) }
    var deleting by remember { mutableStateOf<AgentProfile?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Mes agents", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Un rôle, des consignes et des réglages pour chaque conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Button(onClick = { editing = AgentProfile(name = "", modelSha = vm.selectedModel.sha256) }, enabled = editable) {
            Icon(Icons.Default.Add, null); Text(" Créer un agent")
        } }
        itemsIndexed(agents, key = { _, profile -> profile.id }) { _, profile ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile.name + if (profile.id == selected.id) " • sélectionné" else "", style = MaterialTheme.typography.titleMedium)
                    Text(models.find { it.sha256 == profile.modelSha }?.title ?: "Modèle indisponible", style = MaterialTheme.typography.bodySmall)
                    Text(profile.instructions.ifBlank { "Aucune consigne personnalisée" }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("Température ${"%.2f".format(profile.temperature)} • ${profile.maxTokens} tokens max", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.selectAgent(profile.id); if (vm.selectedAgent.value.id == profile.id) onChat() }, enabled = status != ServerStatus.Loading) { Text("Converser") }
                        TextButton(onClick = { editing = profile }, enabled = editable) { Text("Modifier") }
                        TextButton(onClick = { deleting = profile }, enabled = editable && agents.size > 1) { Text("Supprimer") }
                    }
                }
            }
        }
        item { Text("Chaque agent garde sa conversation pendant cette session. Les profils sont enregistrés sur le téléphone. Changer d’agent avec un autre modèle arrête le moteur ; touche ensuite Démarrer.", style = MaterialTheme.typography.bodySmall) }
    }
    editing?.let { profile -> AgentEditor(profile, models, onSave = { if (vm.saveAgent(it)) editing = null }, onClose = { editing = null }) }
    deleting?.let { profile -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Supprimer cet agent ?") },
        text = { Text("Le profil ${profile.name} et sa conversation seront retirés.") },
        confirmButton = { TextButton(onClick = { vm.deleteAgent(profile.id); deleting = null }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } }) }
}

@Composable
private fun AgentEditor(profile: AgentProfile, models: List<ModelPreset>, onSave: (AgentProfile) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var instructions by rememberSaveable(profile.id) { mutableStateOf(profile.instructions) }
    var sha by rememberSaveable(profile.id) { mutableStateOf(profile.modelSha) }
    var temperature by rememberSaveable(profile.id) { mutableStateOf(profile.temperature) }
    var topP by rememberSaveable(profile.id) { mutableStateOf(profile.topP) }
    var maxTokens by rememberSaveable(profile.id) { mutableFloatStateOf(profile.maxTokens.toFloat()) }
    var anti by rememberSaveable(profile.id) { mutableStateOf(profile.antiHallucination) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, title = { Text(if (profile.name.isBlank()) "Créer un agent" else "Modifier l’agent") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(value = instructions, onValueChange = { instructions = it.take(12000) }, label = { Text("Rôle et consignes") }, minLines = 3, maxLines = 6)
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(models.find { it.sha256 == sha }?.title ?: "Choisir un modèle", Modifier.weight(1f), maxLines = 2)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        models.forEach { model -> DropdownMenuItem(text = { Text(model.title) }, onClick = { sha = model.sha256; menu = false }) }
                    }
                }
                Text("Température : ${"%.2f".format(temperature)}")
                Text("Plus basse : réponses plus prévisibles. Plus haute : réponses plus variées.", style = MaterialTheme.typography.bodySmall)
                Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..1.2f)
                Text("Top-p : ${"%.2f".format(topP)}")
                Slider(value = topP, onValueChange = { topP = it }, valueRange = .1f..1f)
                Text("Longueur maximum : ${maxTokens.toInt()} tokens")
                Slider(value = maxTokens, onValueChange = { maxTokens = it }, valueRange = 128f..1024f, steps = 6)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prudence sur les faits")
                        Text("Demande au modèle de signaler ses incertitudes. Cela ne garantit pas l’exactitude.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = anti, onCheckedChange = { anti = it })
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onSave(profile.copy(name = name.trim(), instructions = instructions, modelSha = sha,
                temperature = temperature, topP = topP, maxTokens = maxTokens.toInt(), antiHallucination = anti))
        }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Annuler") } },
    )
}

@Composable
private fun ImportModelDialog(vm: ChatViewModel, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var repository by rememberSaveable { mutableStateOf<String?>(null) }
    var lightOnly by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf<ModelPreset?>(null) }
    val loading by vm.importing.collectAsState()
    val results by vm.searchResults.collectAsState()
    val files by vm.importFiles.collectAsState()
    val error by vm.importError.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val search = { focus.clearFocus(); keyboard?.hide(); vm.searchModels(query) }
    LaunchedEffect(Unit) { if (results.isEmpty()) vm.searchModels("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Trouver un modèle") }, text = {
        Column(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (repository == null) {
                Text("Recherche dans le catalogue GGUF de Hugging Face. Choisis un dépôt, puis un fichier.")
                OutlinedTextField(value = query, onValueChange = { query = it.take(200) },
                    label = { Text("Nom du modèle : Qwen, Gemma…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }))
                Button(onClick = search, enabled = !loading) { Text("Rechercher") }
                if (!loading) Text("${results.size} dépôts • triés par téléchargements", style = MaterialTheme.typography.bodySmall)
            } else {
                TextButton(onClick = { repository = null; vm.backToSearch() }) { Text("‹ Résultats de recherche") }
                Text(repository.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fichiers jusqu’à 3 Go", Modifier.weight(1f)); Switch(checked = lightOnly, onCheckedChange = { lightOnly = it })
                }
            }
            if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Recherche…")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repository == null) itemsIndexed(results, key = { _, result -> result.id }) { _, result ->
                    Card(onClick = { repository = result.id; vm.findModels(result.id) }, enabled = !loading) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(result.id, fontWeight = FontWeight.Medium)
                            Text("${result.downloads} téléchargements • GGUF", style = MaterialTheme.typography.bodySmall)
                            Text("Voir les fichiers", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    val visible = files.filter { !lightOnly || it.byteSize <= 3_000_000_000L }
                    if (!loading && files.isNotEmpty() && visible.isEmpty()) item {
                        Text("Aucun fichier sous 3 Go. Désactive ce filtre pour afficher les autres ; les gros modèles demandent davantage de mémoire.")
                    }
                    itemsIndexed(visible, key = { _, model -> model.fileName }) { _, model ->
                        Card(onClick = { selected = model }, enabled = !loading) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(model.title, fontWeight = FontWeight.Medium)
                                Text(model.subtitle, style = MaterialTheme.typography.bodySmall)
                                Text("Télécharger et utiliser", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Fermer") } })
    selected?.let { model -> AlertDialog(onDismissRequest = { selected = null }, title = { Text("Télécharger ce modèle ?") },
        text = { Text("${model.title}\n${model.subtitle}\n\nUtilise le Wi-Fi. Le modèle sera conservé pour fonctionner hors ligne. Le format GGUF ne garantit pas la compatibilité du moteur ni que la mémoire du téléphone suffira.") },
        confirmButton = { TextButton(onClick = {
            if (vm.addModel(model)) { selected = null; onClose(); vm.startServer() }
        }) { Text("Télécharger") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Annuler") } }) }
}
