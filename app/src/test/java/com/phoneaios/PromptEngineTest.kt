package com.phoneaios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptEngineTest {
    private val engine = PromptEngine()

    @Test
    fun parsesDownloadCommand() {
        val actions = engine.parseCommand("download subway surfers")
        assertEquals(ActionType.OPEN_APP, actions.first().type)
        assertTrue(actions.any { it.type == ActionType.CLICK_TEXT && it.text == "Install" })
    }

    @Test
    fun parsesWhatsappCommand() {
        val actions = engine.parseCommand("send hello to mom on whatsapp")
        assertTrue(actions.any { it.type == ActionType.OPEN_APP && it.packageName == "com.whatsapp" })
        assertTrue(actions.any { it.type == ActionType.TYPE_TEXT && it.text == "hello" })
    }

    @Test
    fun parsesYoutubeCommand() {
        val actions = engine.parseCommand("play news on youtube")
        assertTrue(actions.any { it.type == ActionType.OPEN_APP && it.packageName == "com.google.android.youtube" })
        assertTrue(actions.any { it.type == ActionType.TYPE_TEXT && it.text == "news" })
    }
}
