package com.bianqujian.app

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.regex.Pattern
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val status = when {
            isCompleted -> "PICKED_UP"
            Regex("退回|退货|取消").containsMatchIn(text) -> "CANCELLED"
            Regex("已到站|已入库|待取|取件码").containsMatchIn(text) -> "READY"
            else -> return
        }
        val parcelType = extractType(text)
        val carrier = extractCarrier(text)
        val trackingNumber = extractTrackingNumber(text)
        val updatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val prefs = getSharedPreferences("parcels", MODE_PRIVATE)
        val old = prefs.getString("items", "") ?: ""
        val rows = old.split("\n").filter { it.isNotBlank() }.toMutableList()
        var changed = false
        while (matcher.find()) {
            val code = matcher.group().replace(Regex("\\s+"), "").replace(Regex("-+"), "-")
            val index = rows.indexOfFirst { it.startsWith("$code|") }
            val location = extractLocation(text)
            if (location != "未识别位置") prefs.edit().putString("frequent_pickup_point", location).apply()
            if (index < 0) {
                rows.add("$code|${extractName(text)}|${isCompleted}|$status|拼多多|${location}|$parcelType|$carrier|$trackingNumber|$updatedAt")
                changed = true
            } else {
                val fields = rows[index].split("|").toMutableList()
                while (fields.size < 10) fields.add(when (fields.size) { 1 -> "通知识别"; 2 -> "false"; 3 -> "READY"; 4 -> "截图识别"; 5 -> "未识别位置"; 6 -> "未知类型"; 7 -> "未知快递"; 8 -> "未知运单号"; else -> updatedAt })
                fields[2] = (status == "PICKED_UP").toString()
                fields[3] = status
                fields[4] = "拼多多"
                if (location != "未识别位置") fields[5] = location
                if (parcelType != "未知类型") fields[6] = parcelType
                if (carrier != "未知快递") fields[7] = carrier
                if (trackingNumber != "未知运单号") fields[8] = trackingNumber
                fields[9] = updatedAt
                val updated = fields.joinToString("|")
                if (updated != rows[index]) { rows[index] = updated; changed = true }
            }
        }
        if (changed) { prefs.edit().putString("items", rows.joinToString("\n")).apply(); sendBroadcast(Intent(ACTION_UPDATED).setPackage(packageName)) }
    }
    private fun extractName(text: String) = Regex("(?:商品|包裹)[：:]?\\s*([^，。,. ]{2,20})").find(text)?.groupValues?.get(1) ?: "通知识别"
    private fun extractLocation(text: String): String = Regex("(?:取件地址|取件点|驿站|地址|位置)[：:]?\\s*([^，。；;\\n]{2,40})").find(text)?.groupValues?.get(1)?.trim() ?: "未识别位置"
    private fun extractType(text: String): String = when {
        Regex("买菜|生鲜|菜场|自提点").containsMatchIn(text) -> "拼多多买菜"
        Regex("外卖|即时配送|餐饮").containsMatchIn(text) -> "即时配送"
        Regex("退货|退回|寄回").containsMatchIn(text) -> "退货件"
        else -> "普通快递"
    }
    private fun extractCarrier(text: String): String = Regex("(顺丰|中通|圆通|申通|韵达|极兔|邮政|京东|德邦|菜鸟|百世)").find(text)?.groupValues?.get(1) ?: "未知快递"
    private fun extractTrackingNumber(text: String): String = Regex("(?<![A-Z0-9])(?:SF|YT|ZT|JD|JT)?[A-Z0-9]{8,20}(?![A-Z0-9])").find(text.uppercase())?.value ?: "未知运单号"
    companion object { const val ACTION_UPDATED = "com.bianqujian.app.PARCELS_UPDATED" }
}
