package be.itspace.fairllm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val FairColors = darkColorScheme(
    primary = Color(0xFF7CFFB2),
    onPrimary = Color(0xFF062014),
    secondary = Color(0xFF8FB7FF),
    background = Color(0xFF0B0D12),
    surface = Color(0xFF121722),
    surfaceVariant = Color(0xFF1A2230),
    onBackground = Color(0xFFF4F7FB),
    onSurface = Color(0xFFF4F7FB),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = FairColors) {
                FairLLMApp()
            }
        }
    }
}

@Composable
private fun FairLLMApp(vm: ChatViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val status by vm.status.collectAsState()
    val error by vm.error.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Memory, null) }, label = { Text("Chat") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Réglages") })
            }
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(
                Brush.verticalGradient(listOf(Color(0xFF0B0D12), Color(0xFF101827)))
            )
        ) {
            when (tab) {
                0 -> ChatScreen(vm, status)
                else -> SettingsScreen(vm, status)
            }
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = vm::dismissError,
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
            title = { Text("FairLLM") },
            text = { Text(error ?: "") }
        )
    }
}

@Composable
private fun StatusPill(status: ServerStatus) {
    val (label, color) = when (status) {
        ServerStatus.Ready -> "Moteur prêt" to Color(0xFF7CFFB2)
        ServerStatus.Loading -> "Chargement…" to Color(0xFFFFD580)
        ServerStatus.Offline -> "Hors ligne" to Color(0xFFFF8A8A)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ChatScreen(vm: ChatViewModel, status: ServerStatus) {
    val messages by vm.messages.collectAsState()
    val generating by vm.generating.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("FairLLM", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(MODEL_PRESETS[vm.settings.modelIndex].title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            }
            StatusPill(status)
        }

        if (status != ServerStatus.Ready) {
            EngineCard(vm, status)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    ) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("LLM local, vraiment local.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("Les messages restent sur ton Fairphone. Le moteur est intégré à FairLLM. Après le téléchargement du modèle, tu peux discuter hors ligne.")
                        }
                    }
                }
            }
            itemsIndexed(messages) { _, msg -> MessageBubble(msg) }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Écris ton message…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5
            )
            FilledIconButton(
                onClick = {
                    if (generating) vm.cancelGeneration() else {
                        vm.send(input)
                        input = ""
                    }
                },
                enabled = generating || input.isNotBlank()
            ) {
                Icon(if (generating) Icons.Default.Stop else Icons.Default.Send, null)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val user = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (user) .84f else .94f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (user) MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f)
            )
        ) {
            Text(
                text = if (msg.content.isBlank()) "…" else msg.content,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun EngineCard(vm: ChatViewModel, status: ServerStatus) {
    val model = MODEL_PRESETS[vm.settings.modelIndex]
    val detail by vm.engineDetail.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Moteur local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(model.subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
            if (detail.isNotBlank()) Text(detail)
            if (status == ServerStatus.Loading) TextButton(onClick = vm::stopServer) { Text("Annuler") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineStartButton(vm, enabled = status != ServerStatus.Loading)
                OutlinedButton(onClick = vm::refreshStatus) {
                    Icon(Icons.Default.Refresh, null)
                    Text(" Vérifier")
                }
            }
        }
    }
}

@Composable
private fun EngineStartButton(vm: ChatViewModel, enabled: Boolean = true) {
    var confirmDownload by remember { mutableStateOf(false) }
    Button(
        enabled = enabled,
        onClick = {
            if (vm.modelIsDownloaded()) vm.startServer() else confirmDownload = true
        }
    ) {
        Icon(Icons.Default.PlayArrow, null)
        Text(" Démarrer")
    }
    if (confirmDownload) {
        val model = MODEL_PRESETS[vm.settings.modelIndex]
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("Télécharger le modèle") },
            text = { Text("${model.title}\n${model.subtitle}\n\nLe modèle est téléchargé depuis Hugging Face. Utilise de préférence le Wi-Fi. Ensuite, il reste sur ton téléphone et fonctionne hors ligne.") },
            confirmButton = { TextButton(onClick = { confirmDownload = false; vm.startServer() }) { Text("Télécharger et démarrer") } },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Annuler") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: ChatViewModel, status: ServerStatus) {
    var modelIndex by remember { mutableIntStateOf(vm.settings.modelIndex) }
    var temp by remember { mutableStateOf(vm.settings.temperature) }
    var topP by remember { mutableStateOf(vm.settings.topP) }
    var anti by remember { mutableStateOf(vm.settings.antiHallucination) }
    var maxTokens by remember { mutableStateOf(vm.settings.maxTokens.toFloat()) }
    var showSetup by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Réglages", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Moteur intégré • CPU", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            }
            StatusPill(status)
        }

        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Modèle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (status != ServerStatus.Offline) Text("Arrête le moteur avant de changer de modèle.")
                MODEL_PRESETS.forEachIndexed { index, model ->
                    Card(
                        onClick = {
                            if (status == ServerStatus.Offline) {
                                modelIndex = index
                                vm.settings.modelIndex = index
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = if (modelIndex == index) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else Color.Transparent)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(model.title, fontWeight = FontWeight.Medium)
                            Text(model.subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Anti-hallucination", fontWeight = FontWeight.SemiBold)
                        Text("Force le modèle à reconnaître ce qu’il ne sait pas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                    }
                    Switch(checked = anti, onCheckedChange = { anti = it; vm.settings.antiHallucination = it })
                }
                HorizontalDivider()
                Text("Température : ${"%.2f".format(temp)}")
                Slider(value = temp, onValueChange = { temp = it; vm.settings.temperature = it }, valueRange = 0f..1.2f)
                Text("Top‑p : ${"%.2f".format(topP)}")
                Slider(value = topP, onValueChange = { topP = it; vm.settings.topP = it }, valueRange = .1f..1f)
                Text("Réponse max : ${maxTokens.toInt()} tokens")
                Slider(value = maxTokens, onValueChange = { maxTokens = it; vm.settings.maxTokens = it.toInt() }, valueRange = 128f..1024f, steps = 6)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EngineStartButton(vm, enabled = status == ServerStatus.Offline)
            OutlinedButton(onClick = vm::stopServer) {
                Icon(Icons.Default.Stop, null)
                Text(" Arrêter")
            }
            IconButton(onClick = vm::clearChat) { Icon(Icons.Default.Delete, contentDescription = "Effacer le chat") }
        }

        TextButton(onClick = { showSetup = true }) { Text("Utilisation hors ligne") }
    }

    if (showSetup) {
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text("Moteur intégré") },
            text = {
                Text("Choisis un modèle, puis touche Démarrer. Le premier lancement nécessite Internet pour télécharger le modèle. Les lancements suivants utilisent la copie locale. Le moteur utilise actuellement le processeur du téléphone. Arrêter libère sa mémoire.")
            },
            confirmButton = { TextButton(onClick = { showSetup = false }) { Text("Compris") } }
        )
    }
}

