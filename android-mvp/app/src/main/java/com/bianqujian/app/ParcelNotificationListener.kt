package com.bianqujian.app

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.regex.Pattern

class ParcelNotificationListener : NotificationListenerService() {
    private val pattern = Pattern.compile("(?<![A-Z0-9])[A-Z]{1,3}\\s*[-—–－]?\\s*\\d{1,4}\\s*[-—–－]\\s*\\d{1,4}(?![A-Z0-9])")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val text = listOf(extras.getString("android.title"), extras.getCharSequence("android.text")?.toString(), extras.getCharSequence("android.bigText")?.toString()).filterNotNull().joinToString(" ")
        val normalized = text.uppercase().replace('－', '-').replace('—', '-').replace('–', '-')
        val matcher = pattern.matcher(normalized)
        val prefs = getSharedPreferences("parcels", MODE_PRIVATE)
        val old = prefs.getString("items", "") ?: ""
        val rows = old.split("\n").filter { it.isNotBlank() }.toMutableList()
        var changed = false
        while (matcher.find()) {
            val code = matcher.group().replace(Regex("\\s+"), "").replace(Regex("-+"), "-")
            if (rows.none { it.startsWith("$code|") }) { rows.add("$code|${extractName(text)}|false"); changed = true }
        }
        if (changed) { prefs.edit().putString("items", rows.joinToString("\n")).apply(); sendBroadcast(Intent(ACTION_UPDATED).setPackage(packageName)) }
    }
    private fun extractName(text: String) = Regex("(?:商品|包裹)[：:]?\\s*([^，。,. ]{2,20})").find(text)?.groupValues?.get(1) ?: "通知识别"
    companion object { const val ACTION_UPDATED = "com.bianqujian.app.PARCELS_UPDATED" }
}
