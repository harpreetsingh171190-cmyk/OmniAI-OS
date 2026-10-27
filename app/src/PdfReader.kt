package com.harpreet.omniai.ai

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfReader {
    /** Extracts selectable text. Scanned (image-only) PDFs return empty text. */
    suspend fun extractText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open file")
        input.use { stream ->
            PDDocument.load(stream).use { doc -> PDFTextStripper().getText(doc) }
        }
    }
}
