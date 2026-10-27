package com.harpreet.omniai.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.harpreet.omniai.data.Flashcard
import com.harpreet.omniai.data.QuizQuestion

@Composable
fun ResultActions(text: String, langTag: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val speaker = rememberSpeaker()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy") }
        OutlinedButton(onClick = { shareText(context, text) }) { Text("Share") }
        OutlinedButton(onClick = { speaker.speak(text, langTag) }) { Text("🔊") }
    }
}

// ---------------- Summarizer ----------------
@Composable
fun SummarizerScreen(vm: AppViewModel) {
    var input by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("Bullet points") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste an article, notes or any long text") }, minLines = 6, maxLines = 12
        )
        if (vm.pdfText.isNotBlank()) {
            OutlinedButton(onClick = { input = vm.pdfText.take(15000) }) { Text("Use text from the loaded PDF") }
        }
        ChipRow(listOf("Bullet points", "Short paragraph", "Detailed", "Explain simply"), style) { style = it }
        Button(
            onClick = { vm.summarize(input, style) },
            enabled = input.isNotBlank() && !vm.summarizing
        ) { Text(if (vm.summarizing) "Summarizing…" else "Summarize") }
        if (vm.summary.isNotBlank()) {
            HorizontalDivider()
            SelectionContainer { Text(vm.summary) }
            ResultActions(vm.summary, vm.voiceLang)
        }
    }
}

// ---------------- Study tools ----------------
@Composable
fun StudyScreen(vm: AppViewModel) {
    var input by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("5") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste your notes or chapter text") }, minLines = 5, maxLines = 10
        )
        if (vm.pdfText.isNotBlank()) {
            OutlinedButton(onClick = { input = vm.pdfText.take(12000) }) { Text("Use text from the loaded PDF") }
        }
        Text("How many?", style = MaterialTheme.typography.labelLarge)
        ChipRow(listOf("5", "10", "15"), count) { count = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.generateStudy("cards", input, count.toInt()) },
                enabled = input.isNotBlank() && !vm.studyLoading
            ) { Text("Flashcards") }
            Button(
                onClick = { vm.generateStudy("quiz", input, count.toInt()) },
                enabled = input.isNotBlank() && !vm.studyLoading
            ) { Text("Quiz") }
        }
        if (vm.studyLoading) Text("Generating…", style = MaterialTheme.typography.bodySmall)
        vm.studyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        when (vm.studyKind) {
            "cards" -> if (vm.cards.isNotEmpty()) FlashcardViewer(vm.cards.toList())
            "quiz" -> if (vm.quiz.isNotEmpty()) QuizViewer(vm.quiz.toList())
        }
    }
}

@Composable
private fun FlashcardViewer(cards: List<Flashcard>) {
    key(cards.map { it.q }.hashCode()) {
        var i by remember { mutableStateOf(0) }
        var flipped by remember { mutableStateOf(false) }
        val c = cards[i.coerceIn(0, cards.lastIndex)]
        Text("Card ${i + 1} / ${cards.size}", style = MaterialTheme.typography.labelLarge)
        Card(Modifier.fillMaxWidth().heightIn(min = 160.dp).clickable { flipped = !flipped }) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(if (flipped) c.a else c.q, style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            if (flipped) "Answer (tap to flip back)" else "Tap the card to see the answer",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (i > 0) { i--; flipped = false } }, enabled = i > 0) { Text("Previous") }
            Button(
                onClick = { if (i < cards.lastIndex) { i++; flipped = false } },
                enabled = i < cards.lastIndex
            ) { Text("Next") }
        }
    }
}

@Composable
private fun QuizViewer(qs: List<QuizQuestion>) {
    key(qs.map { it.q }.hashCode()) {
        var i by remember { mutableStateOf(0) }
        var picked by remember { mutableStateOf<Int?>(null) }
        var score by remember { mutableStateOf(0) }
        var done by remember { mutableStateOf(false) }
        if (done) {
            Text("Your score: $score / ${qs.size}", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { i = 0; picked = null; score = 0; done = false }) { Text("Try again") }
        } else {
            val q = qs[i.coerceIn(0, qs.lastIndex)]
            Text("Question ${i + 1} / ${qs.size}", style = MaterialTheme.typography.labelLarge)
            Text(q.q, style = MaterialTheme.typography.titleMedium)
            q.options.forEachIndexed { j, opt ->
                val mark = when {
                    picked == null -> ""
                    j == q.answer -> "✅ "
                    j == picked -> "❌ "
                    else -> ""
                }
                OutlinedButton(
                    onClick = { picked = j; if (j == q.answer) score++ },
                    enabled = picked == null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("$mark${'A' + j}. $opt") }
            }
            if (picked != null) {
                if (q.why.isNotBlank()) Text(q.why, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    if (i < qs.lastIndex) { i++; picked = null } else done = true
                }) { Text(if (i < qs.lastIndex) "Next" else "Finish") }
            }
        }
    }
}

// ---------------- Prompt library ----------------
@Composable
fun PromptLibraryScreen(vm: AppViewModel, onUse: () -> Unit) {
    var cat by remember { mutableStateOf(PromptLibrary.categories[0]) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tap a prompt to open it in Chat, then add your topic.", style = MaterialTheme.typography.bodySmall)
        ChipRow(PromptLibrary.categories, cat) { cat = it }
        PromptLibrary.items.filter { it.category == cat }.forEach { p ->
            Card(Modifier.fillMaxWidth().clickable { vm.useDraft(p.text); onUse() }) {
                Column(Modifier.padding(14.dp)) {
                    Text(p.title, style = MaterialTheme.typography.titleSmall)
                    Text(p.text.trim(), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
}

// ---------------- Personas ----------------
@Composable
fun PersonasScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "The active persona shapes how the AI answers in Chat. You can also switch it at the top of the Chat screen.",
            style = MaterialTheme.typography.bodySmall
        )
        vm.allPersonas.forEach { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    if (p.prompt.isNotBlank()) Text(p.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (vm.persona.id == p.id) {
                            Text("✓ Active", color = MaterialTheme.colorScheme.primary)
                        } else {
                            OutlinedButton(onClick = { vm.selectPersona(p.id) }) { Text("Use") }
                        }
                        if (!p.builtIn) TextButton(onClick = { vm.deletePersona(p.id) }) { Text("Delete") }
                    }
                }
            }
        }
        HorizontalDivider()
        Text("Create your own", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(name, { name = it }, label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(prompt, { prompt = it }, label = { Text("Instructions (e.g. You are a friendly chef…)") },
            modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(
            onClick = { vm.addPersona(name, prompt); name = ""; prompt = "" },
            enabled = name.isNotBlank() && prompt.isNotBlank()
        ) { Text("Add persona") }
    }
}

// ---------------- Lock screen ----------------
@Composable
fun LockScreen(vm: AppViewModel) {
    var pin by remember { mutableStateOf("") }
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val canBio = vm.biometricEnabled && activity != null && Biometric.available(context)
    val title = tr("Unlock OmniAI-OS")
    val usePin = tr("Use PIN")
    LaunchedEffect(canBio) {
        if (canBio) Biometric.prompt(activity!!, title, usePin) { vm.unlockWithBiometric() }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tr("🔒 OmniAI-OS is locked"), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("PIN") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
            vm.lockError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { if (!vm.unlock(pin)) pin = "" }, enabled = pin.length >= 4) { Text(tr("Unlock")) }
            if (canBio) {
                OutlinedButton(onClick = {
                    Biometric.prompt(activity!!, title, usePin) { vm.unlockWithBiometric() }
                }) { Text(tr("👆 Use fingerprint / face")) }
            }
        }
    }
}
