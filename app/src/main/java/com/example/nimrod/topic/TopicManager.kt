package com.example.nimrod.topic

/**
 * Cheap, deterministic topic classification.
 *
 * No network call and no token cost: each incoming utterance is scored against a
 * small keyword set and the highest scoring topic wins. Ties and empty matches
 * fall back to [GENERAL]. Swap [classify] for an embedding-based classifier later
 * without touching the rest of the app.
 */
object TopicManager {

    const val LEGAL = "legal"
    const val FINANCE = "finance"
    const val DEVELOPMENT = "development"
    const val GENERAL = "general"

    private val KEYWORDS: Map<String, List<String>> = mapOf(
        LEGAL to listOf(
            "law", "lawsuit", "legal", "attorney", "lawyer", "court", "contract",
            "clause", "statute", "sue", "liability", "nda", "compliance", "patent"
        ),
        FINANCE to listOf(
            "money", "budget", "invest", "investment", "tax", "invoice", "payment",
            "bank", "finance", "financial", "expense", "revenue", "profit", "loan",
            "interest", "cash", "salary", "401k", "stock"
        ),
        DEVELOPMENT to listOf(
            "code", "bug", "api", "gradle", "kotlin", "java", "build", "deploy",
            "repo", "repository", "database", "function", "compile", "android",
            "server", "commit", "branch", "test", "refactor", "dependency"
        )
    )

    /** Returns one of [LEGAL], [FINANCE], [DEVELOPMENT] or [GENERAL]. */
    fun classify(text: String): String {
        if (text.isBlank()) return GENERAL

        val words = text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toSet()

        if (words.isEmpty()) return GENERAL

        var bestTopic = GENERAL
        var bestScore = 0

        for ((topic, keywords) in KEYWORDS) {
            var score = 0
            for (keyword in keywords) {
                if (keyword in words) score += 2
                // partial hits are worth less but still count ("contracts", "deploying")
                else if (words.any { it.startsWith(keyword) && it.length <= keyword.length + 3 }) score += 1
            }
            if (score > bestScore) {
                bestScore = score
                bestTopic = topic
            }
        }

        return bestTopic
    }

    /** All topics this classifier can emit. */
    fun allTopics(): List<String> = listOf(LEGAL, FINANCE, DEVELOPMENT, GENERAL)
}
