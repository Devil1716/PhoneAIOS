package com.phoneaios

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class ScreenParser {
    fun parseScreen(root: AccessibilityNodeInfo?): String {
        if (root == null) {
            return "No accessibility nodes available."
        }
        val seen = linkedSetOf<String>()
        val lines = mutableListOf<String>()
        walk(root, seen, lines)
        return if (lines.isEmpty()) "Screen has no visible text nodes." else lines.joinToString(separator = "\n")
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        seen: MutableSet<String>,
        lines: MutableList<String>
    ) {
        val bounds = Rect().also(node::getBoundsInScreen)
        val parts = listOfNotNull(
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { "text=\"$it\"" },
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { "desc=\"$it\"" },
            node.viewIdResourceName?.takeIf { it.isNotEmpty() }?.let { "id=$it" },
            node.className?.toString()?.substringAfterLast('.')?.let { "class=$it" },
            if (node.isClickable) "clickable=true" else null,
            if (node.isEditable) "editable=true" else null,
            "bounds=${bounds.flattenToString()}"
        )
        if (parts.isNotEmpty()) {
            val line = parts.joinToString(separator = ", ")
            if (seen.add(line)) {
                lines += line
            }
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                walk(child, seen, lines)
                child.recycle()
            }
        }
    }
}
