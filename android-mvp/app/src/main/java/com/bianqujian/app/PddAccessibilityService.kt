package com.bianqujian.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PddAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != PDD_PACKAGE) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val root = rootInActiveWindow ?: return
        val targets = listOf("多多代收点", "身份码", "取件码", "代收点")
        for (target in targets) {
            val nodes = root.findAccessibilityNodeInfosByText(target)
            val clickable = nodes.firstOrNull { it.isClickable && it.isEnabled }
                ?: nodes.firstOrNull()?.findClickableParent()
            if (clickable != null) {
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                disableSelf()
                return
            }
        }
    }

    private fun AccessibilityNodeInfo.findClickableParent(): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = this
        while (current != null) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
        }
        return null
    }

    override fun onInterrupt() = Unit

    companion object { const val PDD_PACKAGE = "com.xunmeng.pinduoduo" }
}
