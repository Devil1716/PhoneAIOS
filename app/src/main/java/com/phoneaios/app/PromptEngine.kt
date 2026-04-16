package com.phoneaios.app

class PromptEngine {
    fun parseCommand(cmd: String): List<Action> {
        val lowerCmd = cmd.lowercase()
        return when {
            lowerCmd.contains("download") || lowerCmd.contains("install") -> {
                val appName = extractAppName(lowerCmd, listOf("download", "install"))
                listOf(
                    Action(ActionType.OPEN_APP, packageName = "com.android.vending"), // Play Store
                    Action(ActionType.WAIT, duration = 2000),
                    Action(ActionType.CLICK_TEXT, text = "Search"),
                    Action(ActionType.TYPE_TEXT, text = appName),
                    Action(ActionType.ENTER),
                    Action(ActionType.WAIT, duration = 3000),
                    Action(ActionType.CLICK_TEXT, text = "Install"),
                    Action(ActionType.INSTALL_CLICK) // Signal to monitor install
                )
            }
            lowerCmd.contains("whatsapp") && (lowerCmd.contains("send") || lowerCmd.contains("hello") || lowerCmd.contains("morning")) -> {
                listOf(
                    Action(ActionType.OPEN_APP, packageName = "com.whatsapp"),
                    Action(ActionType.WAIT, duration = 2000),
                    Action(ActionType.CLICK_TEXT, text = "New chat"), // or search
                    Action(ActionType.CLICK_TEXT, text = "Mom"), // Heuristic or extracted
                    Action(ActionType.TYPE_TEXT, text = "Good morning!"),
                    Action(ActionType.CLICK_TEXT, text = "Send")
                )
            }
            lowerCmd.contains("cab") || lowerCmd.contains("taxi") || lowerCmd.contains("ola") -> {
                listOf(
                    Action(ActionType.OPEN_APP, packageName = "com.olacabs.customer"),
                    Action(ActionType.WAIT, duration = 3000),
                    Action(ActionType.CLICK_TEXT, text = "Where to?"),
                    Action(ActionType.TYPE_TEXT, text = "Airport")
                )
            }
            lowerCmd.contains("play") && lowerCmd.contains("songs") -> {
                listOf(
                    Action(ActionType.OPEN_APP, packageName = "com.google.android.apps.youtube.music"),
                    Action(ActionType.WAIT, duration = 2000),
                    Action(ActionType.CLICK_TEXT, text = "Search"),
                    Action(ActionType.TYPE_TEXT, text = "Arijit Singh"),
                    Action(ActionType.ENTER)
                )
            }
            lowerCmd.contains("battery") -> {
                listOf(
                    Action(ActionType.OPEN_APP, packageName = "com.android.settings"),
                    Action(ActionType.WAIT, duration = 1000),
                    Action(ActionType.CLICK_TEXT, text = "Battery")
                )
            }
            lowerCmd.contains("pnr") || lowerCmd.contains("train") -> {
                listOf(
                    Action(ActionType.OPEN_APP, packageName = "cris.org.in.prs.ima"), // IRCTC
                    Action(ActionType.WAIT, duration = 3000),
                    Action(ActionType.CLICK_TEXT, text = "PNR Enquiry")
                )
            }
            lowerCmd.contains("settings") -> {
                listOf(Action(ActionType.OPEN_APP, packageName = "com.android.settings"))
            }
            else -> emptyList()
        }
    }

    private fun extractAppName(cmd: String, keywords: List<String>): String {
        var result = cmd
        for (kw in keywords) {
            if (result.contains(kw)) {
                result = result.substringAfter(kw).trim()
            }
        }
        return result
    }
}
