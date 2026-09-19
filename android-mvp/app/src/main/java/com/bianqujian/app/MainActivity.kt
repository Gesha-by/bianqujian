package com.bianqujian.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.provider.Settings
import android.content.pm.ApplicationInfo
import android.graphics.drawable.GradientDrawable
import java.util.regex.Pattern
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory

enum class ParcelStatus { IN_TRANSIT, READY, PICKED_UP, CANCELLED }
data class Parcel(val code: String, val name: String = "未提供商品名", var found: Boolean = false, var status: ParcelStatus = ParcelStatus.READY, val source: String = "截图识别", val location: String = "未识别位置", val parcelType: String = "未知类型", val carrier: String = "未知快递", val trackingNumber: String = "未知运单号", val updatedAt: String = "未知时间", val imagePath: String = "")

class MainActivity : Activity() {
    private val parcels = mutableListOf<Parcel>()
    private lateinit var list: LinearLayout
    private lateinit var completedList: LinearLayout
    private lateinit var statusTabs: LinearLayout
    private var selectedStatus = ParcelStatus.READY
    private lateinit var summary: TextView
    private val storage by lazy { getSharedPreferences("parcels", MODE_PRIVATE) }
    private val codePattern = Pattern.compile("(?<![A-Z0-9])[A-Z]{1,3}\\s*[-—–－]?\\s*\\d{1,4}(?:\\s*[-—–－]\\s*\\d{1,4}){1,2}(?![A-Z0-9])")
    private val updateReceiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { parcels.clear(); load(); refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); load(); render() }
    override fun onResume() { super.onResume(); registerReceiver(updateReceiver, IntentFilter(ParcelNotificationListener.ACTION_UPDATED), RECEIVER_NOT_EXPORTED) }
    override fun onPause() { unregisterReceiver(updateReceiver); super.onPause() }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 16); setBackgroundColor(Color.rgb(247,248,252)) }
        val title = TextView(this).apply { text = "便取件"; textSize = 22f; setTextColor(Color.rgb(23,35,61)); setTypeface(null, 1); setPadding(2, 8, 2, 16) }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 17, 18, 17); background = rounded(Color.rgb(66,99,235), 28f) }
        hero.addView(TextView(this).apply { text = "下楼前，先看清楚要找什么"; textSize = 12f; setTextColor(Color.WHITE) })
        hero.addView(TextView(this).apply { text = "我的取件点 · ${parcels.firstOrNull { it.location != "未识别位置" }?.location ?: "待识别"}"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(null, 1); setPadding(0, 7, 0, 11) })
        hero.addView(TextView(this).apply { text = "● 今天取件 · 预计 3 分钟"; textSize = 11f; setTextColor(Color.WHITE); background = rounded(Color.argb(45,255,255,255), 16f); setPadding(10, 7, 10, 7) })
        val import = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(14, 11, 8, 11); background = rounded(Color.WHITE, 22f) }
        import.addView(TextView(this).apply { text = "导入订单长截图\n识别后确认，再加入找件清单"; textSize = 12f; setTextColor(Color.rgb(42,55,82)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        import.addView(Button(this).apply { text = "识别图片"; textSize = 11f; setTextColor(Color.rgb(66,99,235)); background = rounded(Color.rgb(237,241,255), 16f); elevation = 0f; stateListAnimator = null; setOnClickListener { chooseText() } })
        val simulate = Button(this).apply { text = "模拟到件数据（测试）"; textSize = 10f; setTextColor(Color.rgb(104,119,146)); background = rounded(Color.rgb(242,245,250), 16f); elevation = 0f; stateListAnimator = null; setOnClickListener { simulateArrival() }; visibility = if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) View.VISIBLE else View.GONE }
        val autoSync = Button(this).apply { text = if (isNotificationAccessEnabled()) "通知自动同步已开启" else "开启通知自动同步"; textSize = 11f; setTextColor(Color.rgb(66,99,235)); background = rounded(Color.rgb(237,241,255), 16f); elevation = 0f; stateListAnimator = null; setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
        statusTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 14, 0, 4) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        completedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addStatusTabs()
        summary = TextView(this).apply { textSize = 14f; setTextColor(Color.rgb(23,35,61)); setTypeface(null, 1); setPadding(12, 18, 2, 8) }
        val handoffNote = TextView(this).apply { text = "取到包裹后，再进行最后一步"; textSize = 12f; setTextColor(Color.rgb(92,103,126)); setPadding(2, 16, 2, 6) }
        val handoff = Button(this).apply { text = "打开拼多多扫描取件"; textSize = 14f; setTextColor(Color.WHITE); background = rounded(Color.rgb(23,35,61), 24f); elevation = 0f; stateListAnimator = null; setPadding(16, 16, 16, 16); setOnClickListener { choosePddOpenMode() } }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 12) }
        content.addView(title); content.addView(hero); content.addView(import, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 14 }); content.addView(autoSync, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 7 }); content.addView(simulate, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 7 }); content.addView(statusTabs); content.addView(summary); content.addView(list)
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(handoffNote); root.addView(handoff, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 6 }); setContentView(root); refresh()
    }

    private fun addStatusTabs() {
        val tabs = listOf(
            ParcelStatus.IN_TRANSIT to "运输中",
            ParcelStatus.READY to "待取件",
            ParcelStatus.PICKED_UP to "已取件",
            ParcelStatus.CANCELLED to "已取消"
        )
        tabs.forEach { (status, label) ->
            statusTabs.addView(Button(this).apply {
                text = label; textSize = 11f; minWidth = 0; minimumWidth = 0; setPadding(2, 7, 2, 7)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
                setOnClickListener { selectedStatus = status; refresh() }
            })
        }
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun choosePddOpenMode() {
        if (packageManager.getLaunchIntentForPackage("com.xunmeng.pinduoduo") == null) {
            openPdd()
            return
        }
        val saved = storage.getString("pdd_open_mode", null)
        if (saved == "always") { openPdd(); return }
        val labels = arrayOf("以后都打开", "仅打开一次", "不打开")
        var selected = 1
        val choices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        val buttons = mutableListOf<TextView>()
        labels.forEachIndexed { index, label ->
            val row = TextView(this).apply { text = label; textSize = 15f; gravity = Gravity.CENTER_VERTICAL; setTextColor(Color.rgb(23,35,61)); setPadding(dp(16), dp(14), dp(16), dp(14)); background = rounded(Color.rgb(246,248,252), 16f); setOnClickListener { selected = index; buttons.forEachIndexed { i, item -> item.background = rounded(if (i == selected) Color.rgb(232,238,255) else Color.rgb(246,248,252), 16f); item.setTextColor(if (i == selected) Color.rgb(66,99,235) else Color.rgb(23,35,61)) } } }
            row.minHeight = dp(48); buttons.add(row); choices.addView(row, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }
        val remember = CheckBox(this).apply { text = "记住此设置"; textSize = 12f; isChecked = saved == "once"; setTextColor(Color.rgb(104,119,146)); buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(66,99,235)); setPadding(0, dp(6), 0, 0) }
        choices.addView(remember, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        buttons[1].performClick()
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(choices) }
        AlertDialog.Builder(this).setTitle("打开拼多多扫描取件").setMessage("请选择这次如何处理取件操作").setView(scroll).setNegativeButton("取消", null).setPositiveButton("确定") { _, _ ->
            when (selected) {
                0 -> { if (remember.isChecked) storage.edit().putString("pdd_open_mode", "always").apply(); openPdd() }
                1 -> { if (remember.isChecked) storage.edit().putString("pdd_open_mode", "once").apply(); openPdd() }
                else -> Unit
            }
        }.show()
    }
    private fun openPdd() {
        val intent = packageManager.getLaunchIntentForPackage("com.xunmeng.pinduoduo")
        if (intent == null) {
            AlertDialog.Builder(this)
                .setTitle("未安装拼多多")
                .setMessage("设备中没有检测到拼多多，是否打开应用商店下载安装？")
                .setNegativeButton("取消", null)
                .setPositiveButton("打开应用商店") { _, _ -> openPddStore() }
                .show()
        } else {
            val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("pinduoduo://com.xunmeng.pinduoduo/https://m.pinduoduo.net/mdkd/identificationCode?entry_source=1&idcode_entry_source=1"))
            deepLink.setPackage("com.xunmeng.pinduoduo")
            try { startActivity(deepLink) } catch (_: Exception) { startActivity(intent) }
        }
    }
    private fun openPddStore() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=拼多多"))
        try { startActivity(market) }
        catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sj.qq.com/app/search?key=拼多多"))) }
    }
    private fun isNotificationAccessEnabled(): Boolean = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
    private fun simulateArrival() { if (parcels.none { it.code == "A-302-8" }) parcels.add(Parcel("A-302-8", "洗衣液")); save(); refresh(); Toast.makeText(this, "已加入一条模拟到件数据", Toast.LENGTH_SHORT).show() }

    private fun chooseText() { AlertDialog.Builder(this).setTitle("选择到件截图").setMessage("便取件只会读取你选择的截图，用于识别商品信息和取件码，不会读取其他照片。").setNegativeButton("取消", null).setPositiveButton("选择截图") { _, _ -> startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*" }, 9) }.show() }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == 9 && resultCode == RESULT_OK) data?.data?.let { importImage(it) } }
    private fun importImage(uri: Uri) { val image = InputImage.fromFilePath(this, uri); val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()); recognizer.process(image).addOnSuccessListener { result -> val rawText = result.text; val text = rawText.uppercase().replace('－', '-').replace('—', '-').replace('–', '-'); val normalizedText = text.replace(Regex("\\s+"), ""); val found = codePattern.matcher(normalizedText); val location = extractOcrLocation(rawText); val carrier = extractOcrCarrier(rawText); val tracking = extractOcrTracking(rawText); val updatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()); val imagePath = saveImportedImage(uri); var added = 0; var updated = 0; while (found.find()) { val code = found.group().replace(Regex("\\s+"), "").replace(Regex("-+"), "-"); val item = Parcel(code, extractOcrName(rawText), false, ParcelStatus.READY, "截图识别", location, "普通快递", carrier, tracking, updatedAt, imagePath); val index = parcels.indexOfFirst { it.code == code }; if (index < 0) { parcels.add(item); added++ } else { parcels[index] = item; updated++ } }; save(); refresh(); val message = when { added == 0 && updated == 0 -> "未识别到取件码，请确认截图包含类似 B8-5-153 或 A-302-8 的编码"; updated > 0 -> "识别完成，已更新 $updated 个包裹，商品图和物流信息已保存"; else -> "识别完成，已导入 $added 个取件码，商品图和物流信息已保存" }; Toast.makeText(this, message, Toast.LENGTH_LONG).show() }.addOnFailureListener { Toast.makeText(this, "图片识别失败，请重试", Toast.LENGTH_LONG).show() } }
    private fun saveImportedImage(uri: Uri): String { val file = File(filesDir, "parcel_${System.currentTimeMillis()}.jpg"); contentResolver.openInputStream(uri).use { input -> FileOutputStream(file).use { output -> input?.copyTo(output) } }; return file.absolutePath }
    private fun extractOcrLocation(text: String): String = Regex("(?:取件点|驿站|收货地址|地址)[：:]?\\s*([^\\n]{4,60})").find(text)?.groupValues?.get(1)?.trim() ?: "未识别位置"
    private fun extractOcrCarrier(text: String): String = Regex("(顺丰|中通|圆通|申通|韵达|极兔|邮政|京东|德邦|菜鸟)").find(text)?.groupValues?.get(1) ?: "未知快递"
    private fun extractOcrTracking(text: String): String = Regex("(?<![A-Z0-9])(?:SF|YT|ZT|JD|JT)?[A-Z0-9]{8,20}(?![A-Z0-9])").find(text.uppercase())?.value ?: "未知运单号"
    private fun extractOcrName(text: String): String {
        val blocked = Regex("收货地址|快递员|取件码|订单编号|待取件|已取件|已签收|复制|分享取件|拨打电话|导航|支持退换货|号\\s*码保护|^\\d{1,2}:\\d{2}|^\\d{1,3}%?$|^5G$|^Wi-?Fi$|^¥?[\\d.]+$")
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val productArea = lines.dropWhile { !it.contains("订单编号") }.drop(1)
        val candidates = (productArea + lines).distinct()
            .filter { it.length in 4..80 && !blocked.containsMatchIn(it) && it.any { ch -> ch in '\u4e00'..'\u9fff' } }
            .filterNot { it.contains("全店回购") || it.contains("本店已拼") || it.contains("销量") || it.contains("退货包运费") }
        return candidates.maxByOrNull { candidate -> (if (productArea.contains(candidate)) 1000 else 0) + candidate.count { it in '\u4e00'..'\u9fff' } * 10 + candidate.length } ?: "商品名待确认"
    }
    private fun refresh() {
        list.removeAllViews()
        val current = parcels.filter { it.status == selectedStatus }
        summary.text = "${statusLabel(selectedStatus)} · ${current.size} 件"
        if (current.isEmpty()) list.addView(TextView(this).apply { text = "暂无${statusLabel(selectedStatus)}包裹"; textSize = 13f; setTextColor(Color.rgb(104,119,146)); gravity = Gravity.CENTER; setPadding(4, 24, 4, 24) })
        current.forEach { addParcelRow(list, it) }
        for (index in 0 until statusTabs.childCount) { val view = statusTabs.getChildAt(index); view.background = rounded(if (ParcelStatus.values()[index] == selectedStatus) Color.rgb(66,99,235) else Color.rgb(242,245,250), 10f); (view as Button).setTextColor(if (ParcelStatus.values()[index] == selectedStatus) Color.WHITE else Color.rgb(64,81,112)) }
    }
    private fun statusLabel(status: ParcelStatus) = mapOf(ParcelStatus.IN_TRANSIT to "运输中", ParcelStatus.READY to "待取件", ParcelStatus.PICKED_UP to "已取件", ParcelStatus.CANCELLED to "已取消")[status]!!
    private fun addParcelRow(container: LinearLayout, parcel: Parcel) {
        val checked = parcel.status == ParcelStatus.PICKED_UP
        val card = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(12, 12, 12, 12); alpha = 0f; background = rounded(if (checked) Color.rgb(241,255,248) else Color.WHITE, 26f); animate().alpha(1f).setDuration(180).start() }
        val product: View = if (parcel.imagePath.isNotBlank() && File(parcel.imagePath).exists()) ImageView(this).apply { setImageBitmap(BitmapFactory.decodeFile(parcel.imagePath)); scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(if (checked) Color.rgb(225,248,235) else Color.rgb(246,243,231), 18f); layoutParams = LinearLayout.LayoutParams(dp(74), dp(92)).apply { rightMargin = dp(12) } } else TextView(this).apply { text = "📦"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.rgb(66,99,235)); background = rounded(if (checked) Color.rgb(225,248,235) else Color.rgb(246,243,231), 18f); layoutParams = LinearLayout.LayoutParams(dp(74), dp(92)).apply { rightMargin = dp(12) } }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        info.addView(TextView(this).apply { text = parcel.name; textSize = 15f; setTypeface(null, 1); setTextColor(Color.rgb(23,35,61)) })
        info.addView(TextView(this).apply { text = "${parcel.parcelType} · ${parcel.carrier}"; textSize = 11f; setTextColor(Color.rgb(104,119,146)); setPadding(0, 5, 0, 4) })
        info.addView(TextView(this).apply { text = "${parcel.source} · ${parcel.location} · ${parcel.updatedAt}"; textSize = 10f; setTextColor(Color.rgb(104,119,146)); setPadding(0, 0, 0, 6) })
        info.addView(TextView(this).apply { text = parcel.code; textSize = 16f; setTypeface(null, 1); setTextColor(Color.rgb(49,76,126)) })
        card.addView(product); card.addView(info)
        container.addView(card, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })
    }
    private fun save() { storage.edit().putString("items", parcels.joinToString("\n") { "${it.code}|${it.name}|${it.found}|${it.status.name}|${it.source}|${it.location}|${it.parcelType}|${it.carrier}|${it.trackingNumber}|${it.updatedAt}|${it.imagePath}" }).apply() }
    private fun load() { storage.getString("items", "")?.lines()?.filter { it.isNotBlank() }?.forEach { val p = it.split("|"); if (p.size >= 3) parcels.add(Parcel(p[0], p[1], p[2] == "true", if (p.size >= 4) runCatching { ParcelStatus.valueOf(p[3]) }.getOrDefault(if (p[2] == "true") ParcelStatus.PICKED_UP else ParcelStatus.READY) else if (p[2] == "true") ParcelStatus.PICKED_UP else ParcelStatus.READY, p.getOrElse(4) { "截图识别" }, p.getOrElse(5) { "未识别位置" }, p.getOrElse(6) { "未知类型" }, p.getOrElse(7) { "未知快递" }, p.getOrElse(8) { "未知运单号" }, p.getOrElse(9) { "未知时间" }, p.getOrElse(10) { "" })) } }
}
