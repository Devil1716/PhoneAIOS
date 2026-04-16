package com.phoneaios.app

import android.view.accessibility.AccessibilityNodeInfo

class ScreenParser {

    fun parseScreen(root: AccessibilityNodeInfo?): String {
        if (root == null) return "No screen content available"
        
        val builder = StringBuilder()
        builder.append("Current Screen Context:\n")
        val uniqueItems = mutableSetOf<String>()
        collectContent(root, builder, uniqueItems)
        return builder.toString()
    }

    private fun collectContent(node: AccessibilityNodeInfo, builder: StringBuilder, uniqueItems: MutableSetOf<String>) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val id = node.viewIdResourceName ?: ""
        
        if (text.isNotBlank() && !uniqueItems.contains(text)) {
            builder.append("- Text: \"$text\"")
            if (node.isClickable) builder.append(" [Clickable]")
            builder.append("\n")
            uniqueItems.add(text)
        }
        
        if (contentDesc.isNotBlank() && !uniqueItems.contains(contentDesc)) {
            builder.append("- Icon/Desc: \"$contentDesc\"")
            if (node.isClickable) builder.append(" [Clickable]")
            builder.append("\n")
            uniqueItems.add(contentDesc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectContent(child, builder, uniqueItems)
            child.recycle()
        }
    }
}
