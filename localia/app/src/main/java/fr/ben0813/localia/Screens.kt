package fr.ben0813.localia

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun HistoryScreen(vm: LocaliaViewModel, state: LocaliaState) {
    var search by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf<Conversation?>(null) }
    var title by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<Conversation?>(null) }
    var exportText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { scope.launch {
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(it)?.use { out -> out.write(exportText.toByteArray(Charsets.UTF_8)) } ?: error("Écriture impossible") }
                vm.notice("Conversation exportée. Ce fichier texte n’est pas chiffré : conserve-le dans un emplacement sûr.")
            } catch (_: Exception) { vm.notice("Impossible d’exporter la conversation.") }
            finally { exportText = "" }
        } }
    }
    val chats = state.vault.chats.filter { it.title.contains(search, ignoreCase = true) || it.messages.any { m -> m.text.contains(search, ignoreCase = true) } }
        .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updated })
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Tes discussions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Sauvegardées et chiffrées sur ce téléphone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Rechercher une discussion") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(16.dp))
        if (chats.isEmpty()) Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Forum, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            Text(if (search.isBlank()) "Tout commence par une question." else "Aucune discussion trouvée.", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = vm::newChat) { Text("Nouvelle conversation") }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(chats, key = { it.id }) { chat ->
                var menu by remember { mutableStateOf(false) }
                Card(onClick = { vm.openChat(chat.id) }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (chat.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(14.dp))
                                Text(chat.title, maxLines = 2, fontWeight = FontWeight.SemiBold)
                            }
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(chat.updated)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(chat.messages.lastOrNull()?.text?.replace('\n', ' ') ?: "", maxLines = 2,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Actions de la discussion") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(if (chat.pinned) "Désépingler" else "Épingler") }, onClick = { menu = false; vm.pinChat(chat.id) })
                                DropdownMenuItem(text = { Text("Renommer") }, onClick = { menu = false; title = chat.title; rename = chat })
                                DropdownMenuItem(text = { Text("Exporter en texte") }, onClick = { menu = false; exportText = chat.exportText(); export.launch("Localia-${chat.id.take(8)}.txt") })
                                DropdownMenuItem(text = { Text("Supprimer du téléphone") }, onClick = { menu = false; delete = chat })
                            }
                        }
                    }
                }
            }
        }
    }
    rename?.let { chat -> AlertDialog(onDismissRequest = { rename = null }, title = { Text("Renommer la discussion") },
        text = { OutlinedTextField(title, { title = it }, label = { Text("Titre") }) },
        confirmButton = { TextButton(onClick = { vm.renameChat(chat.id, title); rename = null }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = { rename = null }) { Text("Annuler") } }) }
    delete?.let { chat -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Supprimer cette discussion ?") },
        text = { Text("Elle sera effacée du téléphone. Les éventuelles copies sur ton PC restent gérées par LM Studio.") },
        confirmButton = { TextButton(onClick = { vm.deleteChat(chat.id); delete = null }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Annuler") } }) }
}

@Composable
internal fun ConnectionScreen(vm: LocaliaViewModel, state: LocaliaState, help: () -> Unit, privacy: () -> Unit, erase: () -> Unit) {
    val c = state.vault.connection
    var url by remember(c) { mutableStateOf(c.url) }
    var token by remember(c) { mutableStateOf(c.token) }
    var showToken by remember { mutableStateOf(false) }
    var extensions by remember(c) { mutableStateOf(c.extensionsEnabled) }
    var names by remember(c) { mutableStateOf(c.extensionNames) }
    var prompt by remember(c) { mutableStateOf(c.prompt) }
    var temperature by remember(c) { mutableFloatStateOf(c.temperature.toFloat()) }
    var maxTokens by remember(c) { mutableFloatStateOf(c.maxTokens.toFloat()) }
    var showModels by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Ton PC, connecté.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Une connexion privée à tes modèles LM Studio.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Section("Serveur LM Studio", Icons.Default.Computer) {
            OutlinedTextField(url, { url = it.take(500) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Adresse HTTPS du PC") }, placeholder = { Text("https://mon-pc.mon-reseau.ts.net") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
            OutlinedTextField(token, { token = it.take(4096) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Jeton API LM Studio") }, visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = { IconButton(onClick = { showToken = !showToken }) { Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showToken) "Masquer le jeton" else "Afficher le jeton") } })
            Button(onClick = { vm.connect(c.copy(url = url, token = token)) }, enabled = !state.connecting && !state.generating && url.isNotBlank() && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                if (state.connecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Text(if (state.connecting) "  Vérification…" else "Vérifier et enregistrer")
            }
            Text("HTTPS uniquement. Le jeton est chiffré sur le téléphone et envoyé uniquement à cette adresse.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = help) { Text("Comment obtenir une adresse sécurisée ?") }
            if (state.verified) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("  Connexion HTTPS vérifiée", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (c.model.isNotBlank()) Section("Modèle du PC", Icons.Default.AutoAwesome) {
            Text(c.model, fontWeight = FontWeight.Medium)
            OutlinedButton(onClick = { showModels = true }, enabled = !state.generating) { Text("Choisir un modèle") }
        }
        Section("Outils et recherche Web", Icons.Default.TravelExplore) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Utiliser les extensions du PC", Modifier.weight(1f))
                Switch(checked = extensions, onCheckedChange = { extensions = it }, enabled = !state.generating && !state.connecting)
            }
            Text("DuckDuckGo et les autres extensions sont exécutés sur ton PC. Active seulement les serveurs MCP auxquels tu fais confiance.", style = MaterialTheme.typography.bodySmall)
            if (extensions) {
                OutlinedTextField(names, { names = it.take(1000) }, Modifier.fillMaxWidth(),
                    label = { Text("Noms des extensions") }, placeholder = { Text("duckduckgo") },
                    supportingText = { Text("Les noms sous mcpServers dans LM Studio. Sépare-les par des virgules. Tous les outils de ces serveurs pourront être utilisés.") })
                Text("Dans LM Studio : paramètres du serveur → Allow calling servers from mcp.json. Autorise également les extensions pour ton jeton.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Masquer les réglages du modèle" else "Instructions et longueur des réponses") }
            if (advanced) {
                OutlinedTextField(prompt, { prompt = it.take(12000) }, Modifier.fillMaxWidth(), label = { Text("Instructions personnelles") }, maxLines = 6)
                Text("Créativité : ${"%.1f".format(temperature)}", style = MaterialTheme.typography.labelLarge)
                Slider(temperature, { temperature = it }, valueRange = 0f..1f)
                Text("Réponse maximale : ${maxTokens.toInt()} tokens", style = MaterialTheme.typography.labelLarge)
                Slider(maxTokens, { maxTokens = it }, valueRange = 256f..8192f, steps = 30)
            }
            OutlinedButton(onClick = { vm.saveOptions(extensions, names, prompt, temperature.toDouble(), maxTokens.toInt()) }, enabled = !state.generating && !state.connecting) { Text("Enregistrer les préférences") }
        }
        Section("Confidentialité", Icons.Default.Shield) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Protéger l’écran", fontWeight = FontWeight.Medium)
                    Text("Bloquer les captures et masquer l’aperçu des apps récentes.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(state.vault.protectScreen, vm::screenProtection, modifier = Modifier.testTag("screenProtection"))
            }
            Text("Historique et jeton chiffrés · aucune publicité · aucun suivi intégré", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = privacy) { Text("Comprendre où vont mes données") }
            TextButton(onClick = erase, enabled = !state.generating && !state.connecting) { Text("Effacer les données de Localia", color = MaterialTheme.colorScheme.error) }
        }
        Text("Localia ${BuildConfig.VERSION_NAME} · Application indépendante, non affiliée à LM Studio.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
    }
    if (showModels) ModelDialog(vm, state) { showModels = false }
}

@Composable
private fun Section(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
internal fun ModelDialog(vm: LocaliaViewModel, state: LocaliaState, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Les modèles de ton PC") },
        text = {
            if (state.models.isEmpty()) Text("Vérifie la connexion pour récupérer les modèles disponibles.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.models, key = { it.key }) { model ->
                    OutlinedCard(onClick = { vm.selectModel(model.key); dismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(model.name, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(if (model.loaded) "Chargé" else "Chargement au premier message", if (model.vision) "Vision" else null, if (model.tools) "Outils" else null).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            if (model.key == state.vault.connection.model) Text("Sélectionné", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text("Fermer") } },
        dismissButton = { TextButton(onClick = { vm.connect(state.vault.connection) }, enabled = !state.connecting && !state.generating) { Text("Actualiser") } })
}

@Composable
internal fun InformationDialog(title: String, text: String, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) },
        text = { SelectionContainer { Text(text, Modifier.verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = dismiss) { Text("Compris") } })
}

internal const val SETUP_TEXT = """1. Installe Tailscale sur ton PC et ton téléphone, puis connecte-les au même réseau Tailscale.

2. Dans LM Studio 0.4 ou plus récent, démarre le serveur sur 127.0.0.1:1234 et active Require Authentication. Crée un jeton API. Tu peux laisser l’exposition au réseau local désactivée.

3. Sur le PC, ouvre un terminal et lance :

tailscale serve --bg http://127.0.0.1:1234

Suis la demande éventuelle d’activation HTTPS de Tailscale. Utilise Serve, qui reste privé au réseau Tailscale, pas Funnel.

4. Copie l’adresse HTTPS affichée par Tailscale dans Localia. Ajoute le jeton LM Studio puis touche Vérifier et enregistrer.

5. Le PC doit rester allumé, avec LM Studio et Tailscale actifs. Choisis un modèle et commence le chat.

Pour DuckDuckGo : dans LM Studio, onglet Program à droite → Install → Edit mcp.json. Recopie seulement le nom sous mcpServers dans les préférences de Localia. Autorise l’utilisation des serveurs MCP dans LM Studio et pour le jeton.

Localia utilise l’API publique de LM Studio. Elle ne s’appaire pas au réseau officiel LM Link."""

internal const val PRIVACY_TEXT = """Localia communique avec le serveur HTTPS que tu configures. La connexion TLS vérifie son certificat. Avec Tailscale Serve, les appareils communiquent en plus à travers ton réseau privé Tailscale.

Les discussions et le jeton API sont stockés chiffrés avec AES-256-GCM. La clé est conservée dans Android Keystore. Les sauvegardes automatiques et transferts de ce coffre sont désactivés : une désinstallation efface les données locales.

Les messages et pièces jointes sont envoyés à ton PC lorsque tu appuies sur Envoyer. LM Studio conserve le contexte des conversations sur le PC. Supprimer une discussion du téléphone n’efface pas les éventuelles données sur le PC.

Si tu actives des extensions comme DuckDuckGo, le PC peut envoyer des requêtes aux services correspondants. Elles ne sont donc pas purement hors ligne. Les droits des extensions se règlent dans LM Studio.

Aucun serveur Localia, publicité, service d’analyse ou collecte automatique n’est intégré. La configuration de ton réseau Tailscale et du serveur reste sous ton contrôle.

Les exports, copies et signalements partagés sortent volontairement du coffre chiffré. Vérifie leur contenu et leur destinataire avant de les partager.

Les réponses des modèles peuvent être incorrectes. Tu peux signaler une réponse via son icône de drapeau. Le circuit de support public sera défini avant la publication sur Google Play."""
