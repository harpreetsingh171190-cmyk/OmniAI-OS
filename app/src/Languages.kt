package com.harpreet.omniai.ui

object Languages {
    const val AUTO = "Auto-detect"

    class Lang(val label: String, val promptName: String, val tag: String)

    val all = listOf(
        Lang("English", "English", "en-US"),
        Lang("हिन्दी", "Hindi", "hi-IN"),
        Lang("ਪੰਜਾਬੀ", "Punjabi (Gurmukhi script)", "pa-IN"),
        Lang("اردو", "Urdu", "ur-PK"),
        Lang("Español", "Spanish", "es-ES"),
        Lang("Français", "French", "fr-FR"),
        Lang("Deutsch", "German", "de-DE"),
        Lang("العربية", "Arabic", "ar-SA"),
        Lang("日本語", "Japanese", "ja-JP"),
        Lang("中文", "Chinese (Simplified)", "zh-CN")
    )

    fun byLabel(label: String): Lang = all.firstOrNull { it.label == label } ?: all[0]
}
