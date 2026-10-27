package com.harpreet.omniai.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

enum class Tool(val title: String, val desc: String) {
    Writer("✍️ AI Writer", "Blog posts, emails, essays, stories and more"),
    PdfChat("📄 PDF Chat", "Pick a PDF, then ask questions or get a summary"),
    ImageGen("🎨 Image Generator", "Describe a picture and generate it"),
    Translator("🌐 Translator", "Translate text between 10 languages, with voice"),
    Vision("🔍 Image Understanding", "Describe a photo, read its text (OCR) or ask questions about it"),
    Summarizer("📝 Summarizer", "Condense articles, notes or PDFs in your preferred style"),
    Study("🎓 Study Tools", "Make flashcards and quizzes from any text"),
    Prompts("🧩 Prompt Library", "Ready-made prompts for study, coding, business and more"),
    Personas("🧠 Personas", "Switch or create AI assistants with their own style"),
    Code("💻 Code Assistant", "Explain, debug, write, review or convert code"),
    Search("🔎 Web Search", "Search the web and get answers with sources"),
    Memory("🗒️ Memory", "Things the AI should always remember about you"),
    Scanner("📷 Document Scanner", "Scan paper documents, extract text and chat with them")
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) })
        }
    }
}

@Composable
fun Bubble(text: String, user: Boolean, onSpeak: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                SelectionContainer { Text(text) }
                if (onSpeak != null) {
                    TextButton(onClick = onSpeak, contentPadding = PaddingValues(0.dp)) { Text("🔊 Listen") }
                }
            }
        }
    }
}

@Composable
fun ToolsMenu(onOpen: (Tool) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Tool.values().forEach { t ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(t) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr(t.title), style = MaterialTheme.typography.titleMedium)
                    Text(tr(t.desc), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun WriterScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val speaker = rememberSpeaker()
    var topic by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Blog post") }
    var tone by remember { mutableStateOf("Friendly") }
    var length by remember { mutableStateOf("Medium") }
    val listen = rememberVoiceInput(vm.voiceLang) { topic = if (topic.isBlank()) it else "$topic $it" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = topic, onValueChange = { topic = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Topic or instructions") }, minLines = 2
            )
            TextButton(onClick = listen) { Text("🎤") }
        }
        Text("Type", style = MaterialTheme.typography.labelLarge)
        ChipRow(listOf("Blog post", "Email", "Social post", "Essay", "Story", "Poem"), type) { type = it }
        Text("Tone", style = MaterialTheme.typography.labelLarge)
        ChipRow(listOf("Friendly", "Formal", "Casual", "Persuasive", "Funny"), tone) { tone = it }
        Text("Length", style = MaterialTheme.typography.labelLarge)
        ChipRow(listOf("Short", "Medium", "Long"), length) { length = it }
        Button(
            onClick = { vm.write(topic, type, tone, length) },
            enabled = topic.isNotBlank() && !vm.writerLoading
        ) { Text(if (vm.writerLoading) "Writing…" else "Write") }

        if (vm.writerText.isNotBlank()) {
            HorizontalDivider()
            SelectionContainer { Text(vm.writerText) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(vm.writerText))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                OutlinedButton(onClick = { shareText(context, vm.writerText) }) { Text("Share") }
                OutlinedButton(onClick = { speaker.speak(vm.writerText, vm.voiceLang) }) { Text("🔊") }
            }
        }
    }
}

@Composable
fun PdfChatScreen(vm: AppViewModel) {
    val speaker = rememberSpeaker()
    var input by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadPdf(uri)
    }
    val listen = rememberVoiceInput(vm.voiceLang) { input = if (input.isBlank()) it else "$input $it" }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.pdfMessages.size) {
        if (vm.pdfMessages.isNotEmpty()) listState.animateScrollToItem(vm.pdfMessages.size - 1)
    }
    val hasDoc = vm.pdfText.isNotBlank()

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(vm.pdfName ?: "No PDF selected", maxLines = 1)
                when {
                    vm.pdfStatus.isNotBlank() -> Text(vm.pdfStatus, style = MaterialTheme.typography.bodySmall)
                    hasDoc -> Text("${vm.pdfText.length} characters read", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                Text(if (vm.pdfName == null) "Choose PDF" else "Change")
            }
        }
        HorizontalDivider()
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.pdfMessages) { m ->
                Bubble(m.text, m.isUser,
                    onSpeak = if (m.isUser) null else ({ speaker.speak(m.text, vm.voiceLang) }))
            }
            if (vm.pdfLoading) item { Text("Working…", style = MaterialTheme.typography.bodySmall) }
        }
        if (hasDoc) {
            TextButton(
                onClick = { vm.askPdf("Summarize this document in a few clear bullet points.") },
                enabled = !vm.pdfLoading, modifier = Modifier.padding(horizontal = 8.dp)
            ) { Text("📝 Summarize") }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(if (hasDoc) "Ask about the PDF" else "Choose a PDF first") },
                enabled = hasDoc, maxLines = 3
            )
            TextButton(onClick = listen, enabled = hasDoc) { Text("🎤") }
            Button(
                onClick = { vm.askPdf(input); input = "" },
                enabled = hasDoc && input.isNotBlank() && !vm.pdfLoading
            ) { Text("Ask") }
        }
    }
}

@Composable
fun ImageScreen(vm: AppViewModel) {
    var prompt by remember { mutableStateOf("") }
    val listen = rememberVoiceInput(vm.voiceLang) { prompt = if (prompt.isBlank()) it else "$prompt $it" }
    LazyColumn(
        Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = prompt, onValueChange = { prompt = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Describe the image") }, minLines = 2
                    )
                    TextButton(onClick = listen) { Text("🎤") }
                }
                Button(
                    onClick = { vm.generateImage(prompt) },
                    enabled = prompt.isNotBlank() && !vm.imageLoading
                ) { Text(if (vm.imageLoading) "Generating…" else "Generate") }
                vm.imageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        items(vm.images) { bytes -> GeneratedImage(bytes) }
    }
}

@Composable
private fun GeneratedImage(bytes: ByteArray) {
    val context = LocalContext.current
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    if (bitmap != null) {
        Column {
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = "Generated image",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.FillWidth
            )
            TextButton(onClick = {
                val ok = saveToGallery(context, bytes)
                Toast.makeText(
                    context, if (ok) "Saved to Pictures/OmniAI" else "Saving needs Android 10 or newer",
                    Toast.LENGTH_SHORT
                ).show()
            }) { Text("💾 Save to gallery") }
        }
    }
}

@Composable
fun TranslatorScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val speaker = rememberSpeaker()
    var input by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(Languages.AUTO) }
    var to by remember { mutableStateOf("English") }
    val listenTag = if (from == Languages.AUTO) vm.voiceLang else Languages.byLabel(from).tag
    val listen = rememberVoiceInput(listenTag) { input = if (input.isBlank()) it else "$input $it" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("From", style = MaterialTheme.typography.labelLarge)
        ChipRow(listOf(Languages.AUTO) + Languages.all.map { it.label }, from) { from = it }
        Text("To", style = MaterialTheme.typography.labelLarge)
        ChipRow(Languages.all.map { it.label }, to) { to = it }
        if (from != Languages.AUTO) {
            OutlinedButton(onClick = {
                val oldFrom = from
                from = to; to = oldFrom
                if (vm.translation.isNotBlank()) { input = vm.translation; vm.setTranslationText("") }
            }) { Text("⇄ Swap languages") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Type or speak text to translate") }, minLines = 3
            )
            TextButton(onClick = listen) { Text("🎤") }
        }
        Button(
            onClick = { vm.translate(input, from, to) },
            enabled = input.isNotBlank() && !vm.translating
        ) { Text(if (vm.translating) "Translating…" else "Translate") }

        if (vm.translation.isNotBlank()) {
            HorizontalDivider()
            SelectionContainer { Text(vm.translation, style = MaterialTheme.typography.titleMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(vm.translation))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                OutlinedButton(onClick = { shareText(context, vm.translation) }) { Text("Share") }
                OutlinedButton(onClick = { speaker.speak(vm.translation, Languages.byLabel(to).tag) }) { Text("🔊") }
            }
        }
    }
}

@Composable
fun VisionScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val speaker = rememberSpeaker()
    var mode by remember { mutableStateOf("Extract text (OCR)") }
    var question by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.loadVisionImage(uri)
    }
    val listen = rememberVoiceInput(vm.voiceLang) { question = if (question.isBlank()) it else "$question $it" }
    val bytes = vm.visionImage
    val bitmap = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }

    val prompt = when (mode) {
        "Describe" -> "Describe this image in detail."
        "Extract text (OCR)" -> "Extract all text visible in this image exactly as written, keeping line breaks and the " +
            "original language and script. Output only the text. If there is no text, say so."
        else -> question
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = { picker.launch("image/*") }) {
            Text(if (bitmap == null) "Choose image" else "Change image")
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = "Selected image",
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
        }
        ChipRow(listOf("Extract text (OCR)", "Describe", "Ask a question"), mode) { mode = it }
        if (mode == "Ask a question") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = question, onValueChange = { question = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about the image") }, minLines = 2
                )
                TextButton(onClick = listen) { Text("🎤") }
            }
        }
        Button(
            onClick = { vm.analyzeImage(prompt) },
            enabled = bitmap != null && prompt.isNotBlank() && !vm.visionLoading
        ) { Text(if (vm.visionLoading) "Analysing…" else "Analyse") }
        vm.visionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (vm.visionResult.isNotBlank()) {
            HorizontalDivider()
            SelectionContainer { Text(vm.visionResult) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(vm.visionResult))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                OutlinedButton(onClick = { shareText(context, vm.visionResult) }) { Text("Share") }
                OutlinedButton(onClick = { speaker.speak(vm.visionResult, vm.voiceLang) }) { Text("🔊") }
            }
        }
    }
}
