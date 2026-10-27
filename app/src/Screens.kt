package com.harpreet.omniai.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.harpreet.omniai.data.Conversation

private enum class Tab(val label: String) {
    Chat("Chat"), Tools("Tools"), History("History"), Favorites("Favorites"), Settings("Settings")
}

@Composable
fun OmniApp(vm: AppViewModel) {
    var showAuth by remember { mutableStateOf(false) }
    if (vm.locked) {
        LockScreen(vm)
    } else if (showAuth) {
        BackHandler { showAuth = false }
        AuthScreen(vm) { showAuth = false }
    } else {
        MainScaffold(vm) { showAuth = true }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: AppViewModel, onOpenAuth: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.Chat) }
    var tool by remember { mutableStateOf<Tool?>(null) }
    val context = LocalContext.current
    BackHandler(enabled = tab == Tab.Tools && tool != null) { tool = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.Chat -> vm.current?.title ?: tr("New chat")
                            Tab.Tools -> tool?.title?.let { tr(it) } ?: tr("Tools")
                            else -> tr(tab.label)
                        }
                    )
                },
                navigationIcon = {
                    if (tab == Tab.Tools && tool != null) {
                        IconButton(onClick = { tool = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                    }
                },
                actions = {
                    if (tab == Tab.Chat) {
                        if (vm.current != null) {
                            IconButton(onClick = { vm.exportCurrent()?.let { shareText(context, it) } }) {
                                Icon(Icons.Default.Share, "Share chat")
                            }
                        }
                        IconButton(onClick = { vm.newChat() }) { Icon(Icons.Default.Add, "New chat") }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.Chat -> Icons.AutoMirrored.Filled.Send
                                    Tab.Tools -> Icons.Default.Build
                                    Tab.History -> Icons.Default.List
                                    Tab.Favorites -> Icons.Default.Favorite
                                    Tab.Settings -> Icons.Default.Settings
                                }, t.label
                            )
                        },
                        label = { Text(tr(t.label)) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.Chat -> ChatScreen(vm)
                Tab.Tools -> when (tool) {
                    null -> ToolsMenu { tool = it }
                    Tool.Writer -> WriterScreen(vm)
                    Tool.PdfChat -> PdfChatScreen(vm)
                    Tool.ImageGen -> ImageScreen(vm)
                    Tool.Translator -> TranslatorScreen(vm)
                    Tool.Vision -> VisionScreen(vm)
                    Tool.Summarizer -> SummarizerScreen(vm)
                    Tool.Study -> StudyScreen(vm)
                    Tool.Prompts -> PromptLibraryScreen(vm) { tab = Tab.Chat }
                    Tool.Personas -> PersonasScreen(vm)
                    Tool.Code -> CodeScreen(vm)
                    Tool.Search -> SearchScreen(vm)
                    Tool.Memory -> MemoryScreen(vm)
                    Tool.Scanner -> ScannerScreen(vm) { tool = Tool.PdfChat }
                }
                Tab.History -> ConversationList(vm, vm.conversations, tr("No chats yet."), searchable = true) { vm.open(it); tab = Tab.Chat }
                Tab.Favorites -> ConversationList(
                    vm, vm.conversations.filter { it.favorite },
                    tr("No favorites yet.\nTap the heart on any chat.")
                ) { vm.open(it); tab = Tab.Chat }
                Tab.Settings -> SettingsScreen(vm, onOpenAuth)
            }
        }
    }
}

@Composable
private fun ChatScreen(vm: AppViewModel) {
    var input by remember { mutableStateOf("") }
    val speaker = rememberSpeaker()
    val listen = rememberVoiceInput(vm.voiceLang) { input = if (input.isBlank()) it else "$input $it" }
    val messages = vm.current?.messages ?: emptyList()
    val listState = rememberLazyListState()
    LaunchedEffect(vm.draft) {
        if (vm.draft.isNotEmpty()) { input = vm.draft; vm.consumeDraft() }
    }
    LaunchedEffect(messages.size, vm.loading) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            ChipRow(vm.allPersonas.map { it.name }, vm.persona.name) { n ->
                vm.allPersonas.firstOrNull { it.name == n }?.let { vm.selectPersona(it.id) }
            }
        }
        if (vm.offlineMode) {
            Text(tr("📴 Offline mode"), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
        }
        if (!vm.offlineMode && vm.apiKey.isNotBlank() && vm.modelOptions.size > 1) {
            Box(Modifier.padding(horizontal = 12.dp)) {
                ChipRow(vm.modelOptions, vm.model.trim()) { vm.selectModel(it) }
            }
        }
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    vm.user?.let { "${tr("Hi")} ${it.displayName} 👋\n${tr("Ask OmniAI anything")}" } ?: tr("🤖 Ask OmniAI anything"),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { m ->
                    Bubble(m.text, m.isUser,
                        onSpeak = if (m.isUser) null else ({ speaker.speak(m.text, vm.voiceLang) }))
                }
                if (vm.loading) item { Text(tr("Thinking…"), style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text(tr("Message")) }, maxLines = 4
            )
            TextButton(onClick = listen) { Text("🎤") }
            IconButton(
                onClick = { vm.send(input); input = "" },
                enabled = input.isNotBlank() && !vm.loading
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
    }
}

@Composable
private fun ConversationList(
    vm: AppViewModel, items: List<Conversation>, empty: String,
    searchable: Boolean = false, onOpen: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) items else items.filter { c ->
        c.title.contains(query, ignoreCase = true) || c.messages.any { it.text.contains(query, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        if (searchable && items.isNotEmpty()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text(tr("Search all chats")) }, singleLine = true
            )
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (items.isEmpty()) empty else "No chats match \"$query\".")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(shown, key = { it.id }) { c ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(c.id) },
                        headlineContent = { Text(c.title, maxLines = 1) },
                        supportingContent = { Text("${c.messages.size} messages") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { vm.toggleFavorite(c.id) }) {
                                    Icon(if (c.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite")
                                }
                                IconButton(onClick = { vm.delete(c.id) }) { Icon(Icons.Default.Delete, "Delete") }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: AppViewModel, onOpenAuth: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes this account and its chats from this device.") },
            confirmButton = { TextButton(onClick = { vm.deleteAccount(); confirmDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    val langs = linkedMapOf("English" to "en-US", "हिन्दी" to "hi-IN", "ਪੰਜਾਬੀ" to "pa-IN")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(tr("Account"), style = MaterialTheme.typography.titleSmall)
        val account = vm.user
        if (account == null) {
            Text(tr("Guest mode: chats are saved on this device only."), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onOpenAuth) { Text(tr("Sign in / Create account")) }
        } else {
            Text("Signed in as ${account.displayName} (@${account.username})")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.signOut() }) { Text(tr("Log out")) }
                OutlinedButton(onClick = { confirmDelete = true }) { Text(tr("Delete account")) }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Dark mode"), Modifier.weight(1f))
            Switch(checked = vm.dark, onCheckedChange = { vm.setDark(it) })
        }
        HorizontalDivider()
        Text(tr("App language"), style = MaterialTheme.typography.titleSmall)
        ChipRow(I18n.options.keys.toList(), I18n.options.entries.firstOrNull { it.value == vm.uiLang }?.key ?: "English") {
            vm.setUiLang(I18n.options[it] ?: "en")
        }
        HorizontalDivider()
        Text(tr("Voice language (speech input & read-aloud)"), style = MaterialTheme.typography.titleSmall)
        ChipRow(langs.keys.toList(), langs.entries.firstOrNull { it.value == vm.voiceLang }?.key ?: "English") {
            vm.voiceLang = langs[it] ?: "en-US"; vm.saveSettings()
        }
        HorizontalDivider()
        Text(tr("App lock"), style = MaterialTheme.typography.titleSmall)
        if (vm.hasPin) {
            Text(tr("PIN lock is on. The app locks after 30 seconds in the background."), style = MaterialTheme.typography.bodySmall)
            val ctx = LocalContext.current
            val activity = ctx.findFragmentActivity()
            if (activity != null && Biometric.available(ctx)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Fingerprint / face unlock"), Modifier.weight(1f))
                    Switch(checked = vm.biometricEnabled, onCheckedChange = { on ->
                        if (on) {
                            Biometric.prompt(activity, tr("Unlock OmniAI-OS"), tr("Use PIN")) { vm.setBiometric(true) }
                        } else vm.setBiometric(false)
                    })
                }
            }
            OutlinedButton(onClick = { vm.removePin() }) { Text(tr("Remove PIN")) }
        } else {
            var newPin by remember { mutableStateOf("") }
            OutlinedTextField(
                value = newPin, onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text(tr("New PIN (4–6 digits)")) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
            vm.lockError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { if (vm.setPin(newPin)) newPin = "" }, enabled = newPin.length >= 4) { Text(tr("Set PIN")) }
        }
        HorizontalDivider()
        Text(tr("AI provider (OpenAI-compatible)"), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(vm.baseUrl, { vm.baseUrl = it; saved = false }, label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(vm.model, { vm.model = it; saved = false }, label = { Text(tr("Chat model")) },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(tr("Saved models"), style = MaterialTheme.typography.labelLarge)
        ChipRow(vm.modelOptions, vm.model.trim()) { vm.selectModel(it); saved = false }
        var newModel by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(newModel, { newModel = it }, label = { Text(tr("Model name")) },
                modifier = Modifier.weight(1f), singleLine = true)
            OutlinedButton(onClick = { vm.addModel(newModel); newModel = "" }, enabled = newModel.isNotBlank()) {
                Text(tr("Add model"))
            }
        }
        TextButton(onClick = { vm.removeSelectedModel() }) { Text(tr("Remove selected")) }
        OutlinedTextField(vm.apiKey, { vm.apiKey = it; saved = false }, label = { Text(tr("API key")) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(vm.imageModel, { vm.imageModel = it; saved = false }, label = { Text(tr("Image model")) },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(tr("Image size"), style = MaterialTheme.typography.labelLarge)
        ChipRow(listOf("1024x1024", "1024x1792", "1792x1024"), vm.imageSize) { vm.imageSize = it; saved = false }
        Text(tr("Web search"), style = MaterialTheme.typography.titleSmall)
        Text(tr("Search provider"), style = MaterialTheme.typography.labelLarge)
        ChipRow(com.harpreet.omniai.ai.SearchService.providers, vm.searchProvider) { vm.searchProvider = it; saved = false }
        OutlinedTextField(vm.searchKey, { vm.searchKey = it; saved = false }, label = { Text(tr("Search API key")) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation())
        Button(onClick = { vm.saveSettings(); saved = true }) { Text(tr(if (saved) "Saved ✓" else "Save")) }
        Text(
            "Leave the API key empty to use demo mode (image generation needs a key). " +
                "The key is stored only on this device.",
            style = MaterialTheme.typography.bodySmall
        )
        HorizontalDivider()
        Text(tr("Offline mode"), style = MaterialTheme.typography.titleSmall)
        val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importOfflineModel(uri)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Offline mode"), Modifier.weight(1f))
            Switch(checked = vm.offlineMode, onCheckedChange = { vm.setOfflineMode(it) })
        }
        Text(tr("Offline mode uses an on-device model for chat, and offline translation."),
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }, enabled = !vm.offlineBusy) {
            Text(tr("Choose model file"))
        }
        Text(
            when {
                vm.offlineStatus.isNotBlank() -> tr(vm.offlineStatus)
                vm.offlineModelPath.isNotBlank() -> tr("Model ready ✓")
                else -> tr("No model selected")
            },
            style = MaterialTheme.typography.bodySmall
        )
        HorizontalDivider()
        Text(tr("Usage on this device"), style = MaterialTheme.typography.titleSmall)
        listOf(
            "Chats" to "chats", "Messages sent" to "messages", "Code assistant" to "code", "Web searches" to "search", "Scans" to "scans", "Writer" to "writer",
            "Translations" to "translations", "PDF questions" to "pdf", "Images generated" to "images",
            "Image analyses" to "vision", "Summaries" to "summaries", "Study sets" to "study",
            "Approx. tokens" to "tokens"
        ).forEach { (label, key) ->
            Row(Modifier.fillMaxWidth()) {
                Text(label, Modifier.weight(1f))
                Text("${vm.usage[key] ?: 0}")
            }
        }
        HorizontalDivider()
        val exportCtx = LocalContext.current
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                try {
                    exportCtx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportAllJson().toByteArray()) }
                    Toast.makeText(exportCtx, tr("Exported ✓"), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(exportCtx, e.message ?: "Export failed", Toast.LENGTH_LONG).show()
                }
            }
        }
        Text(tr("Export my data"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("omniai-export.json") }) { Text(tr("Save as JSON file")) }
            OutlinedButton(onClick = { shareText(exportCtx, vm.exportAllMarkdown()) }) { Text(tr("Share as Markdown")) }
        }
        Text(tr("Export includes chats and personas. Your API key is never exported."),
            style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        OutlinedButton(onClick = { vm.clearAll() }) { Text(tr("Delete all chats")) }
    }
}
