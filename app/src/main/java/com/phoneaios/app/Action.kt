package com.phoneaios.app

enum class ActionType {
    OPEN_APP,
    CLICK_TEXT,
    TYPE_TEXT,
    TAP,
    SWIPE,
    WAIT,
    ENTER,
    INSTALL_CLICK,
    SCROLL,
    MEMORIZE
}

data class Action(
    val type: ActionType,
    val text: String? = null,
    val packageName: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val duration: Long = 0,
    val isSensitive: Boolean = false
)
