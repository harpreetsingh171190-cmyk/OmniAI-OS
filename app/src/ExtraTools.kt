package com.harpreet.omniai.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

// ---------------- Web search ----------------
@Composable
fun SearchScreen(vm: AppViewModel) {
    val uriHandler = LocalUriHandler.current
    var query by remember { mutableStateOf("") }
    val listen = rememberVoiceInput(vm.voiceLang) { query = if (query.isBlank()) it else "$query $it" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(tr("Ask anything to search the web")) }, minLines = 2
            )
            TextButton(onClick = listen) { Text("🎤") }
        }
        Button(onClick = { vm.webSearch(query) }, enabled = query.isNotBlank() && !vm.searching) {
            Text(if (vm.searching) tr("Searching…") else tr("Search"))
        }
        vm.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (vm.searchAnswer.isNotBlank()) {
            HorizontalDivider()
            SelectionContainer { Text(vm.searchAnswer) }
        }
        if (vm.searchHits.isNotEmpty()) {
            Text(tr("Sources"), style = MaterialTheme.typography.titleSmall)
            vm.searchHits.forEachIndexed { i, h ->
                Card(Modifier.fillMaxWidth().clickable {
                    try { uriHandler.openUri(h.url) } catch (e: Exception) { /* no browser available */ }
                }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("[${i + 1}] ${h.title}", style = MaterialTheme.typography.titleSmall)
                        Text(h.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ---------------- Memory ----------------
@Composable
fun MemoryScreen(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Use memory in chats"), Modifier.weight(1f))
            Switch(checked = vm.memoryEnabled, onCheckedChange = { vm.setMemoryEnabled(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(tr("Add something to remember")) }, maxLines = 3
            )
            Button(onClick = { vm.addMemory(text); text = "" }, enabled = text.isNotBlank()) { Text(tr("Add")) }
        }
        HorizontalDivider()
        if (vm.memories.isEmpty()) {
            Text(tr("Nothing saved yet."), style = MaterialTheme.typography.bodyMedium)
        } else {
            vm.memories.forEachIndexed { i, m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("• $m", Modifier.weight(1f))
                    IconButton(onClick = { vm.deleteMemory(i) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
            OutlinedButton(onClick = { vm.clearMemory() }) { Text(tr("Clear all")) }
        }
    }
}

// ---------------- Document scanner ----------------
@Composable
fun ScannerScreen(vm: AppViewModel, onChat: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data)
            vm.onScanResult(res?.pages?.map { it.imageUri } ?: emptyList(), res?.pdf?.uri)
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { dest ->
        val src = vm.scanPdf
        if (dest != null && src != null) {
            try {
                context.contentResolver.openInputStream(src)?.use { input ->
                    context.contentResolver.openOutputStream(dest)?.use { out -> input.copyTo(out) }
                }
                Toast.makeText(context, tr("Exported ✓"), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Could not save PDF", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = {
            if (activity == null) return@Button
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(10)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { e -> vm.setScanError("Scanner unavailable: ${e.message}") }
        }) { Text("📷 " + tr("Scan document")) }

        vm.scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (vm.scanPages.isNotEmpty()) {
            Text("${vm.scanPages.size} " + tr("pages scanned"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.ocrScan() }, enabled = !vm.scanLoading) {
                    Text(if (vm.scanLoading) tr("Reading…") else tr("Extract text (OCR)"))
                }
                if (vm.scanPdf != null) {
                    OutlinedButton(onClick = { pdfLauncher.launch("scan-${System.currentTimeMillis()}.pdf") }) {
                        Text(tr("Save PDF"))
                    }
                }
            }
        }

        if (vm.scanText.isNotBlank()) {
            HorizontalDivider()
            Text(tr("Text found"), style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(vm.scanText) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.useScanAsDocument(); onChat() }) { Text(tr("Chat with this document")) }
                OutlinedButton(onClick = { shareText(context, vm.scanText) }) { Text(tr("Share text")) }
            }
        }
    }
}
