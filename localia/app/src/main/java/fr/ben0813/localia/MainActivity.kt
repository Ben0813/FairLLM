package fr.ben0813.localia

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private var activeVm: LocaliaViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(primary = Color(0xFFB8C2FF), background = Color(0xFF10131E),
                surface = Color(0xFF191D2B), secondaryContainer = Color(0xFF283058)) else
                lightColorScheme(primary = Color(0xFF4058D4), background = Color(0xFFF7F8FC),
                    surface = Color.White, secondaryContainer = Color(0xFFE8ECFF))
            MaterialTheme(colorScheme = colors) {
                val vm: LocaliaViewModel = viewModel()
                activeVm = vm
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.vault.protectScreen) {
                    if (state.vault.protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                LocaliaApp(vm, state)
            }
        }
    }
    override fun onStop() {
        if (!isChangingConfigurations) activeVm?.cancel()
        super.onStop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocaliaApp(vm: LocaliaViewModel, state: LocaliaState) {
    var models by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    if (state.loading) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.vaultError) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Ton coffre est inaccessible", style = MaterialTheme.typography.headlineMedium)
            Text("Localia ne peut pas déchiffrer les données de cet appareil. Elles n’ont pas été écrasées. Une réinitialisation effacera le coffre local et les réglages.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = { erase = true }) { Text("Réinitialiser Localia") }
        }
    } else Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(11.dp)), Alignment.Center) {
                        Text("L", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Text("Localia", fontWeight = FontWeight.Bold)
                }
            }, actions = {
                IconButton(onClick = { help = true }) { Icon(Icons.Default.HelpOutline, "Aide à la connexion") }
                IconButton(onClick = vm::newChat) { Icon(Icons.Default.Add, "Nouvelle conversation") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("Chat" to Icons.Default.ChatBubbleOutline, "Discussions" to Icons.Default.History,
                    "Connexion" to Icons.Default.Computer).forEachIndexed { index, (title, icon) ->
                    NavigationBarItem(selected = state.tab == index, onClick = { vm.tab(index) },
                        icon = { Icon(icon, null) }, label = { Text(title) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (state.tab) {
                0 -> ChatScreen(vm, state, onModels = { models = true })
                1 -> HistoryScreen(vm, state)
                else -> ConnectionScreen(vm, state, { help = true }, { privacy = true }, { erase = true })
            }
        }
    }
    state.notice?.let { message ->
        AlertDialog(onDismissRequest = vm::dismissNotice, title = { Text("Localia") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissNotice) { Text("Compris") } })
    }
    if (models) ModelDialog(vm, state) { models = false }
    if (privacy) InformationDialog("Ta confidentialité", PRIVACY_TEXT) { privacy = false }
    if (help) InformationDialog("Connecter ton PC en toute sécurité", SETUP_TEXT) { help = false }
    if (erase) AlertDialog(onDismissRequest = { erase = false }, title = { Text("Effacer toutes les données locales ?") },
        text = { Text("Les discussions, le jeton et les réglages de Localia seront supprimés de ce téléphone. Les données déjà conservées par LM Studio sur ton PC ne seront pas supprimées.") },
        confirmButton = { TextButton(onClick = { erase = false; vm.eraseAll() }) { Text("Tout effacer") } },
        dismissButton = { TextButton(onClick = { erase = false }) { Text("Annuler") } })
}

@Composable
private fun ChatScreen(vm: LocaliaViewModel, state: LocaliaState, onModels: () -> Unit) {
    val listState = rememberLazyListState()
    val chat = state.chat
    val configured = state.vault.connection.url.isNotBlank()
    val messages = chat?.messages ?: emptyList()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::attach) }
    LaunchedEffect(messages.size, state.streamingText) {
        val count = messages.size + if (state.generating) 2 else 0
        if (count > 0 && (!listState.canScrollForward || state.streamingText.length < 120)) listState.animateScrollToItem(count - 1)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        if (configured) {
            TextButton(onClick = onModels, enabled = !state.generating, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(15.dp))
                Text("  " + (chat?.model ?: state.vault.connection.model).substringAfterLast('/').ifBlank { "Choisir un modèle" }, maxLines = 1)
                Icon(Icons.Default.ExpandMore, null)
            }
        }
        if (messages.isEmpty() && !state.generating) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Center) {
                Row(Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("  TON PC. TES MODÈLES.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(22.dp))
                Text(if (configured) "Qu’aimerais-tu\nexplorer ?" else "Ton IA,\nà portée de main.",
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, lineHeight = 43.sp)
                Spacer(Modifier.height(16.dp))
                Text(if (configured) "Pose une question, partage un document ou explore une idée. Ton PC s’occupe du calcul."
                    else "Retrouve les modèles de LM Studio sur Android, avec une connexion chiffrée et un historique protégé.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(28.dp))
                if (!configured) Button(onClick = { vm.tab(2) }, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(18.dp)) {
                    Icon(Icons.Default.Computer, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecter mon PC")
                } else {
                    listOf("Explique-moi un sujet simplement", "Aide-moi à rédiger un message", "Construisons un plan ensemble").forEach { suggestion ->
                        OutlinedCard(onClick = { vm.draft(suggestion) }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(suggestion, Modifier.weight(1f)); Icon(Icons.Default.NorthEast, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), state = listState,
            verticalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            items(messages) { MessageBubble(it) }
            if (state.generating) {
                item { MessageBubble(Message("user", state.pendingText)) }
                item {
                    if (state.streamingText.isBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  " + (state.activity ?: "Réponse en cours…"), style = MaterialTheme.typography.bodySmall)
                    } else MessageBubble(Message("assistant", state.streamingText), actions = false)
                }
            }
        }
        state.activity?.let { Text(it, Modifier.padding(horizontal = 24.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        state.speed?.let { Text("%.1f tokens/s · calcul sur le PC".format(it), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (chat != null && chat.binding != state.vault.connection.binding()) {
            TextButton(onClick = vm::newChat, Modifier.fillMaxWidth()) { Text("Configuration différente · Nouvelle conversation") }
        }
        state.attachment?.let { attachment ->
            InputChip(selected = true, onClick = vm::removeAttachment, modifier = Modifier.padding(horizontal = 20.dp),
                label = { Text(attachment.name, maxLines = 1) }, trailingIcon = { Icon(Icons.Default.Close, "Retirer la pièce jointe", Modifier.size(16.dp)) })
        }
        if (configured) Surface(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { picker.launch(arrayOf("text/*", "application/json", "image/jpeg", "image/png", "image/webp")) }, enabled = configured && !state.generating && !state.readingFile) {
                    Icon(Icons.Default.AttachFile, "Joindre un fichier")
                }
                TextField(value = state.draft, onValueChange = vm::draft, enabled = !state.generating,
                    placeholder = { Text(if (state.readingFile) "Lecture du fichier…" else "Écris ton message…", maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.weight(1f),
                    maxLines = 5, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                FilledIconButton(onClick = { if (state.generating) vm.cancel() else vm.send() },
                    enabled = state.generating || (!state.readingFile && (state.draft.isNotBlank() || state.attachment != null))) {
                    Icon(if (state.generating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, if (state.generating) "Arrêter la réponse" else "Envoyer le message")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, actions: Boolean = true) {
    val user = message.role == "user"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var report by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(if (user) "VOUS" else "LOCALIA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp))
        if (user) Surface(shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            SelectionContainer { Text(message.text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
        } else Markdown(message.text)
        if (!user && actions) Row {
            IconButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) { Icon(Icons.Default.ContentCopy, "Copier la réponse", Modifier.size(17.dp)) }
            IconButton(onClick = { report = true }) { Icon(Icons.Default.Flag, "Signaler la réponse", Modifier.size(17.dp)) }
        }
    }
    if (report) AlertDialog(onDismissRequest = { report = false }, title = { Text("Signaler cette réponse") },
        text = { Text("Tu peux partager cette réponse avec le destinataire de ton choix pour signaler un problème. Seul le texte de la réponse sera inclus, sans jeton ni historique. Vérifie les données personnelles avant de partager.") },
        confirmButton = { TextButton(onClick = {
            report = false
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Signalement Localia")
                putExtra(Intent.EXTRA_TEXT, "Réponse à signaler dans Localia :\n\n${message.text}")
            }, "Partager le signalement"))
        }) { Text("Choisir le destinataire") } }, dismissButton = { TextButton(onClick = { report = false }) { Text("Annuler") } })
}

@Composable
private fun Markdown(text: String) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary
    val renderer = remember(context) { Markwon.builder(context).usePlugin(object : AbstractMarkwonPlugin() {
        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
            builder.linkResolver { view, link ->
                val uri = link.toUri()
                if (uri.scheme in listOf("https", "http")) try { view.context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
            }
        }
    }).build() }
    AndroidView(factory = { TextView(it).apply { textSize = 17f; setLineSpacing(6f, 1f); setTextIsSelectable(true) } },
        modifier = Modifier.fillMaxWidth(), update = {
            it.setTextColor(android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
            it.setLinkTextColor(android.graphics.Color.rgb((linkColor.red * 255).toInt(), (linkColor.green * 255).toInt(), (linkColor.blue * 255).toInt()))
            renderer.setMarkdown(it, text)
        })
}
