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
            if (index < 0) {
                rows.add("$code|${extractName(text)}|${isCompleted}|${if (isCompleted) "PICKED_UP" else "READY"}|拼多多|${location}")
                changed = true
            } else {
                val fields = rows[index].split("|").toMutableList()
                while (fields.size < 6) fields.add(if (fields.size == 1) "通知识别" else if (fields.size == 2) "false" else if (fields.size == 3) "READY" else if (fields.size == 4) "截图识别" else "未识别位置")
                if (isCompleted) { fields[2] = "true"; fields[3] = "PICKED_UP" }
                fields[4] = "拼多多"
                if (location != "未识别位置") fields[5] = location
                val updated = fields.joinToString("|")
                if (updated != rows[index]) { rows[index] = updated; changed = true }
            }
        }
        if (changed) { prefs.edit().putString("items", rows.joinToString("\n")).apply(); sendBroadcast(Intent(ACTION_UPDATED).setPackage(packageName)) }
    }
    private fun extractName(text: String) = Regex("(?:商品|包裹)[：:]?\\s*([^，。,. ]{2,20})").find(text)?.groupValues?.get(1) ?: "通知识别"
    private fun extractLocation(text: String): String = Regex("(?:取件地址|取件点|驿站|地址|位置)[：:]?\\s*([^，。；;\\n]{2,40})").find(text)?.groupValues?.get(1)?.trim() ?: "未识别位置"
    companion object { const val ACTION_UPDATED = "com.bianqujian.app.PARCELS_UPDATED" }
}
