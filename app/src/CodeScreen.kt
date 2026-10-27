package com.harpreet.omniai.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun CodeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var mode by remember { mutableStateOf("Explain") }
    var lang by remember { mutableStateOf("Kotlin") }
    var input by remember { mutableStateOf("") }
    val listen = rememberVoiceInput(vm.voiceLang) { input = if (input.isBlank()) it else "$input $it" }
    val modes = listOf("Explain", "Debug", "Write", "Review", "Tests", "Convert")
    val langs = listOf(
        "Kotlin", "Java", "Python", "JavaScript", "TypeScript", "C++", "C", "C#", "Go", "Rust", "SQL", "Bash"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(tr("Mode"), style = MaterialTheme.typography.labelLarge)
        ChipRow(modes, mode) { mode = it }
        Text(
            if (mode == "Convert") tr("Convert to language") else tr("Language"),
            style = MaterialTheme.typography.labelLarge
        )
        ChipRow(langs, lang) { lang = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = {
                    Text(tr(if (mode == "Write") "Describe the code you need" else "Paste your code or error here"))
                },
                minLines = 6
            )
            TextButton(onClick = listen) { Text("🎤") }
        }
        Button(
            onClick = { vm.runCode(mode, lang, input) },
            enabled = input.isNotBlank() && !vm.codeLoading
        ) { Text(if (vm.codeLoading) tr("Working…") else tr("Run")) }

        if (vm.codeResult.isNotBlank()) {
            HorizontalDivider()
            SelectionContainer {
                Text(vm.codeResult, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(vm.codeResult))
                    Toast.makeText(context, tr("Copied"), Toast.LENGTH_SHORT).show()
                }) { Text(tr("Copy")) }
                OutlinedButton(onClick = { shareText(context, vm.codeResult) }) { Text(tr("Share")) }
            }
        }
    }
}
