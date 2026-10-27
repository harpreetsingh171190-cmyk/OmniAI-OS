package com.harpreet.omniai.data

data class Message(val role: String, val text: String, val time: Long = System.currentTimeMillis()) {
    val isUser get() = role == "user"
}

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<Message>,
    val favorite: Boolean = false,
    val updated: Long = System.currentTimeMillis()
)

data class Persona(val id: String, val name: String, val prompt: String, val builtIn: Boolean = false)

data class Flashcard(val q: String, val a: String)

data class QuizQuestion(val q: String, val options: List<String>, val answer: Int, val why: String)
