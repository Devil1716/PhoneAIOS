package com.phoneaios

import kotlinx.coroutines.delay

class ActionExecutor {
    interface Callback {
        fun onActionStart(action: Action)
        suspend fun onSensitiveAction(action: Action): Boolean
        fun onComplete()
    }

    suspend fun execute(actions: List<Action>, callback: Callback? = null) {
        val service = PhoneControlService.instance ?: return
        for (action in actions) {
            if (action.isSensitive && callback?.onSensitiveAction(action) == false) {
                break
            }
            callback?.onActionStart(action)
            when (action.type) {
                ActionType.OPEN_APP -> action.packageName?.let(service::openApp)
                ActionType.CLICK_TEXT -> action.text?.let(service::clickByText)
                ActionType.TYPE_TEXT -> action.text?.let(service::typeText)
                ActionType.TAP -> {
                    if (action.x != null && action.y != null) {
                        service.tap(action.x, action.y)
                    }
                }
                ActionType.SWIPE -> {
                    if (action.x != null && action.y != null && action.endX != null && action.endY != null) {
                        service.swipe(action.x, action.y, action.endX, action.endY, action.durationMs)
                    }
                }
                ActionType.WAIT -> delay(action.durationMs)
                ActionType.ENTER -> service.submitCurrentInput()
                ActionType.SCROLL_FORWARD -> service.scrollForward()
                ActionType.GLOBAL_BACK -> service.globalBack()
                ActionType.LONG_PRESS -> {
                    if (action.x != null && action.y != null) {
                        service.longPress(action.x, action.y)
                    }
                }
            }
            delay(650L)
        }
        callback?.onComplete()
    }
}
