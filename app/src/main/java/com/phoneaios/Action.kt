package com.phoneaios

enum class ActionType {
    OPEN_APP,
    CLICK_TEXT,
    TYPE_TEXT,
    TAP,
    SWIPE,
    WAIT,
    ENTER,
    SCROLL_FORWARD,
    GLOBAL_BACK,
    LONG_PRESS
}

data class Action(
    val type: ActionType,
    val text: String? = null,
    val packageName: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val endX: Float? = null,
    val endY: Float? = null,
    val durationMs: Long = 600L,
    val isSensitive: Boolean = false,
    val spokenSummary: String = type.name
)
