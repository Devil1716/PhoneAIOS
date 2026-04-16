package com.phoneaios.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActionExecutor {
    interface ActionCallback {
        fun onActionStarted(action: Action)
        suspend fun onSafetyCheckRequired(action: Action): Boolean
        fun onSequenceComplete()
    }

    suspend fun execute(actions: List<Action>, callback: ActionCallback? = null) {
        val service = PhoneControlService.instance ?: return
        
        for (action in actions) {
            if (action.isSensitive) {
                val approved = callback?.onSafetyCheckRequired(action) ?: true
                if (!approved) break
            }
            callback?.onActionStarted(action)
                val success = when (action.type) {
                    ActionType.OPEN_APP -> { 
                        action.packageName?.let { service.openApp(it) }
                        true 
                    }
                    ActionType.CLICK_TEXT -> action.text?.let { service.clickByText(it) } ?: false
                    ActionType.TYPE_TEXT -> action.text?.let { service.typeText(it) } ?: false
                    ActionType.TAP -> {
                        if (action.x != null && action.y != null) service.tap(action.x, action.y)
                        true
                    }
                    ActionType.SWIPE -> {
                        if (action.x != null && action.y != null) service.swipe(0f, 0f, action.x, action.y, action.duration)
                        true
                    }
                    ActionType.WAIT -> {
                        delay(action.duration)
                        true
                    }
                    ActionType.ENTER -> true
                    ActionType.INSTALL_CLICK -> true
                    ActionType.SCROLL -> {
                        service.scroll()
                        true
                    }
                }
                if (!success) {
                    android.util.Log.w("ActionExecutor", "Action failed: ${action.type}. Attempting recovery/notification.")
                    // In a real scenario, we might trigger a 'back' gesture or wait 
                }
            }
            delay(1000) 
        }
        callback?.onSequenceComplete()
    }
}
