package com.mucephei.app.ai

object SuggestionGenerator {

    /**
     * Builds a dedicated follow-up questions prompt based strictly on the completed AI answer.
     */
    fun buildFollowUpPrompt(responseText: String): String {
        val cleaned = responseText.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        val truncated = if (cleaned.length > 4000) cleaned.take(4000) else cleaned
        return """
Based strictly on the following AI response:
\"\"\"
$truncated
\"\"\"

Task: Generate 2 or 3 highly specific follow-up questions directly related to the exact topics or technical details discussed above.

STRICT RULES:
1. Do NOT generate generic or boilerplate questions.
2. Every question MUST explicitly reference specific topics or terms from the text above.
3. Output only the questions, one per line. Do not include introductory text, numbering, or bullet points.
""".trimIndent()
    }

    /**
     * Parses the follow-up prompt response from the model into clean, distinct suggestion strings.
     */
    fun parseFollowUpQuestions(rawText: String): List<String> {
        val cleaned = rawText.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        if (cleaned.isBlank() || cleaned.startsWith("Error") || cleaned.startsWith("خطأ")) {
            return emptyList()
        }

        return cleaned.lines()
            .map { line ->
                line.trim()
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .replace(Regex("^\\[\\d+]\\s*"), "")
                    .replace(Regex("^\\d+\\.\\s*"), "")
                    .removePrefix("[Suggestion]:")
                    .removePrefix("[Suggestion]")
                    .trim()
                    .removeSurrounding("\"")
            }
            .filter { line ->
                line.length > 5 &&
                !line.equals("Suggested follow-ups:", ignoreCase = true) &&
                !line.equals("Follow-up questions:", ignoreCase = true) &&
                !line.equals("Here are some follow-up questions:", ignoreCase = true)
            }
            .distinct()
            .take(3)
    }
}
