package com.harpreet.omniai.ui

import com.harpreet.omniai.data.Persona

object Personas {
    val builtIn = listOf(
        Persona("default", "Assistant", "", true),
        Persona("teacher", "Teacher",
            "You are a patient teacher. Explain step by step in simple words, give a small example, then check understanding.", true),
        Persona("coder", "Coder",
            "You are an expert software engineer. Give correct, concise code with short explanations and mention edge cases.", true),
        Persona("mech", "Mechanical Engineer",
            "You are a senior mechanical engineer. Show formulas, units and assumptions clearly, and remind the user to verify against standards.", true),
        Persona("career", "Career Coach",
            "You are a practical career coach. Give honest, actionable advice for CVs, interviews and career decisions.", true),
        Persona("punjabi", "Punjabi Helper",
            "Always reply in Punjabi written in Gurmukhi script, in simple everyday language, unless the user asks for another language.", true)
    )
}

object PromptLibrary {
    class Item(val category: String, val title: String, val text: String)

    val categories = listOf("Study", "Coding", "Business", "Social", "Engineering")

    val items = listOf(
        Item("Study", "Explain with an example", "Explain this topic step by step with an example: "),
        Item("Study", "7-day study plan", "Make a 7-day study plan for: "),
        Item("Study", "Quiz me", "Quiz me with 5 questions (one at a time) on: "),
        Item("Coding", "Review my code", "Review this code and suggest improvements:\n"),
        Item("Coding", "Explain an error", "Explain this error and how to fix it:\n"),
        Item("Coding", "Write unit tests", "Write unit tests for this function:\n"),
        Item("Business", "Professional email", "Write a professional email about: "),
        Item("Business", "Business plan outline", "Create a one-page business plan outline for: "),
        Item("Business", "Polite follow-up", "Draft a polite follow-up message about: "),
        Item("Social", "Instagram captions", "Write 5 catchy Instagram captions about: "),
        Item("Social", "30-day content calendar", "Create a 30-day content calendar for: "),
        Item("Social", "LinkedIn post", "Write an engaging LinkedIn post about: "),
        Item("Engineering", "Beam deflection", "Calculate and explain the maximum deflection of a beam with these details: "),
        Item("Engineering", "Compare materials", "Compare two materials for a shaft and recommend one: "),
        Item("Engineering", "Bolted joint checks", "List the design checks for a bolted joint under this loading: ")
    )
}
