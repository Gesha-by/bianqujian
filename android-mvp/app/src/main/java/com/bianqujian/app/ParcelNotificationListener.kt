package com.bianqujian.app

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.regex.Pattern

class ParcelNotificationListener : NotificationListenerService() {
    private val pddPackage = "com.xunmeng.pinduoduo"
    private val pattern = Pattern.compile("(?<![A-Z0-9])[A-Z]{1,3}\\s*[-—–－]?\\s*\\d{1,4}\\s*[-—–－]\\s*\\d{1,4}(?![A-Z0-9])")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != pddPackage) return
        val extras = sbn.notification.extras
        val text = listOf(extras.getString("android.title"), extras.getCharSequence("android.text")?.toString(), extras.getCharSequence("android.bigText")?.toString()).filterNotNull().joinToString(" ")
        val normalized = text.uppercase().replace('－', '-').replace('—', '-').replace('–', '-')
        val matcher = pattern.matcher(normalized)
        val isCompleted = Regex("已出库|已取件|已领取|取件成功").containsMatchIn(text)
        val prefs = getSharedPreferences("parcels", MODE_PRIVATE)
        val old = prefs.getString("items", "") ?: ""
        val rows = old.split("\n").filter { it.isNotBlank() }.toMutableList()
        var changed = false
        while (matcher.find()) {
            val code = matcher.group().replace(Regex("\\s+"), "").replace(Regex("-+"), "-")
            val index = rows.indexOfFirst { it.startsWith("$code|") }
            val location = extractLocation(text)
            if (index < 0) { rows.add("$code|${extractName(text)}|${isCompleted}|READY|拼多多|${location}"); changed = true }
            else if (isCompleted && !rows[index].endsWith("|true")) { val fields = rows[index].split("|"); rows[index] = "${fields[0]}|${fields.getOrElse(1) { "通知识别" }}|true"; changed = true }
        }
        if (changed) { prefs.edit().putString("items", rows.joinToString("\n")).apply(); sendBroadcast(Intent(ACTION_UPDATED).setPackage(packageName)) }
    }
    private fun extractName(text: String) = Regex("(?:商品|包裹)[：:]?\\s*([^，。,. ]{2,20})").find(text)?.groupValues?.get(1) ?: "通知识别"
    private fun extractLocation(text: String): String = Regex("(?:取件地址|取件点|驿站|地址|位置)[：:]?\\s*([^，。；;\\n]{2,40})").find(text)?.groupValues?.get(1)?.trim() ?: "未识别位置"
    companion object { const val ACTION_UPDATED = "com.bianqujian.app.PARCELS_UPDATED" }
}
