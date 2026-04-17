package com.phoneaios

import java.util.Locale

class PromptEngine {
    private data class CommandRule(
        val regex: Regex,
        val build: (MatchResult) -> List<Action>
    )

    private val appPackages = mapOf(
        "play store" to "com.android.vending",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "settings" to "com.android.settings"
    )

    private val rules = listOf(
        CommandRule(Regex("""(?:download|install)\s+(.+)""", RegexOption.IGNORE_CASE)) { match ->
            val appName = match.groupValues[1].trim()
            listOf(
                Action(ActionType.OPEN_APP, packageName = "com.android.vending", spokenSummary = "Open Play Store"),
                Action(ActionType.WAIT, durationMs = 2000L, spokenSummary = "Wait for Play Store"),
                Action(ActionType.CLICK_TEXT, text = "Search", spokenSummary = "Open search"),
                Action(ActionType.TYPE_TEXT, text = appName, isSensitive = true, spokenSummary = "Type $appName"),
                Action(ActionType.ENTER, spokenSummary = "Submit search"),
                Action(ActionType.WAIT, durationMs = 2500L, spokenSummary = "Wait for results"),
                Action(ActionType.CLICK_TEXT, text = "Install", isSensitive = true, spokenSummary = "Install $appName")
            )
        },
        CommandRule(
            Regex("""send\s+(.+?)\s+to\s+(.+?)\s+on\s+whatsapp""", RegexOption.IGNORE_CASE)
        ) { match ->
            val message = match.groupValues[1].trim()
            val contact = match.groupValues[2].trim()
            listOf(
                Action(ActionType.OPEN_APP, packageName = "com.whatsapp", spokenSummary = "Open WhatsApp"),
                Action(ActionType.WAIT, durationMs = 2500L, spokenSummary = "Wait for WhatsApp"),
                Action(ActionType.CLICK_TEXT, text = "Search", spokenSummary = "Open search"),
                Action(ActionType.TYPE_TEXT, text = contact, isSensitive = true, spokenSummary = "Search for $contact"),
                Action(ActionType.WAIT, durationMs = 1200L, spokenSummary = "Wait for contact"),
                Action(ActionType.CLICK_TEXT, text = contact, isSensitive = true, spokenSummary = "Open $contact"),
                Action(ActionType.TYPE_TEXT, text = message, isSensitive = true, spokenSummary = "Type message"),
                Action(ActionType.ENTER, isSensitive = true, spokenSummary = "Send message")
            )
        },
        CommandRule(Regex("""play\s+(.+?)\s+on\s+youtube""", RegexOption.IGNORE_CASE)) { match ->
            val query = match.groupValues[1].trim()
            listOf(
                Action(ActionType.OPEN_APP, packageName = "com.google.android.youtube", spokenSummary = "Open YouTube"),
                Action(ActionType.WAIT, durationMs = 2500L, spokenSummary = "Wait for YouTube"),
                Action(ActionType.CLICK_TEXT, text = "Search", spokenSummary = "Open search"),
                Action(ActionType.TYPE_TEXT, text = query, spokenSummary = "Search YouTube for $query"),
                Action(ActionType.ENTER, spokenSummary = "Submit search")
            )
        },
        CommandRule(Regex("""(?:check\s+)?battery""", RegexOption.IGNORE_CASE)) {
            listOf(
                Action(ActionType.OPEN_APP, packageName = "com.android.settings", spokenSummary = "Open Settings"),
                Action(ActionType.WAIT, durationMs = 1200L, spokenSummary = "Wait for Settings"),
                Action(ActionType.CLICK_TEXT, text = "Battery", spokenSummary = "Open Battery")
            )
        },
        CommandRule(Regex("""open\s+(.+)""", RegexOption.IGNORE_CASE)) { match ->
            val appLabel = match.groupValues[1].trim().lowercase(Locale.US)
            val pkg = appPackages[appLabel]
            if (pkg == null) emptyList() else listOf(
                Action(ActionType.OPEN_APP, packageName = pkg, spokenSummary = "Open ${match.groupValues[1].trim()}")
            )
        }
    )

    fun parseCommand(command: String): List<Action> {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return emptyList()
        return rules.firstNotNullOfOrNull { rule ->
            rule.regex.matchEntire(trimmed)?.let(rule.build)
        } ?: emptyList()
    }
}
