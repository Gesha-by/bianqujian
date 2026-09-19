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
import android.content.pm.ApplicationInfo
import android.graphics.drawable.GradientDrawable
import java.util.regex.Pattern

enum class ParcelStatus { WAITING, STORED, PICKED_UP, CANCELLED }
data class Parcel(val code: String, val name: String = "未提供商品名", var found: Boolean = false, var status: ParcelStatus = ParcelStatus.WAITING)

class MainActivity : Activity() {
    private val parcels = mutableListOf<Parcel>()
    private lateinit var list: LinearLayout
    private lateinit var completedList: LinearLayout
    private lateinit var statusTabs: LinearLayout
    private var selectedStatus = ParcelStatus.WAITING
    private lateinit var summary: TextView
    private val storage by lazy { getSharedPreferences("parcels", MODE_PRIVATE) }
    private val codePattern = Pattern.compile("(?<![A-Z0-9])[A-Z]{1,3}\\s*[-—–－]?\\s*\\d{1,4}\\s*[-—–－]\\s*\\d{1,4}(?![A-Z0-9])")
    private val updateReceiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { parcels.clear(); load(); refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); load(); render() }
    override fun onResume() { super.onResume(); registerReceiver(updateReceiver, IntentFilter(ParcelNotificationListener.ACTION_UPDATED), RECEIVER_NOT_EXPORTED) }
    override fun onPause() { unregisterReceiver(updateReceiver); super.onPause() }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 16); setBackgroundColor(Color.rgb(247,248,252)) }
        val title = TextView(this).apply { text = "便取件"; textSize = 22f; setTextColor(Color.rgb(23,35,61)); setTypeface(null, 1); setPadding(2, 8, 2, 16) }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 17, 18, 17); background = rounded(Color.rgb(66,99,235), 28f) }
        hero.addView(TextView(this).apply { text = "下楼前，先看清楚要找什么"; textSize = 12f; setTextColor(Color.WHITE) })
        hero.addView(TextView(this).apply { text = "我的取件点 · 暂无包裹"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(null, 1); setPadding(0, 7, 0, 11) })
        hero.addView(TextView(this).apply { text = "● 今天取件 · 预计 3 分钟"; textSize = 11f; setTextColor(Color.WHITE); background = rounded(Color.argb(45,255,255,255), 16f); setPadding(10, 7, 10, 7) })
        val import = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(14, 11, 8, 11); background = rounded(Color.WHITE, 22f) }
        import.addView(TextView(this).apply { text = "导入订单长截图\n识别后确认，再加入找件清单"; textSize = 12f; setTextColor(Color.rgb(42,55,82)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        import.addView(Button(this).apply { text = "识别图片"; textSize = 11f; setTextColor(Color.rgb(66,99,235)); background = rounded(Color.rgb(237,241,255), 16f); elevation = 0f; stateListAnimator = null; setOnClickListener { chooseText() } })
        val simulate = Button(this).apply { text = "模拟到件数据（测试）"; textSize = 10f; setTextColor(Color.rgb(104,119,146)); background = rounded(Color.rgb(242,245,250), 16f); elevation = 0f; stateListAnimator = null; setOnClickListener { simulateArrival() }; visibility = if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) View.VISIBLE else View.GONE }
        statusTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 14, 0, 4) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        completedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addStatusTabs()
        summary = TextView(this).apply { textSize = 14f; setTextColor(Color.rgb(23,35,61)); setTypeface(null, 1); setPadding(12, 18, 2, 8) }
        val handoffNote = TextView(this).apply { text = "取到包裹后，打开拼多多完成扫码出库"; textSize = 12f; setTextColor(Color.rgb(92,103,126)); setPadding(2, 10, 2, 4) }
        val handoff = Button(this).apply { text = "打开拼多多扫码出库"; textSize = 14f; setTextColor(Color.WHITE); background = rounded(Color.rgb(23,35,61), 24f); elevation = 0f; stateListAnimator = null; setPadding(16, 16, 16, 16); setOnClickListener { Toast.makeText(this@MainActivity, "请在拼多多完成扫码出库", Toast.LENGTH_SHORT).show() } }
        root.addView(title); root.addView(hero); root.addView(import, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 14 }); root.addView(simulate, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 7 }); root.addView(statusTabs); root.addView(summary); root.addView(list); root.addView(handoffNote); root.addView(handoff, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 6 }); setContentView(root); refresh()
    }

    private fun addStatusTabs() {
        val tabs = listOf(
            ParcelStatus.WAITING to "待入库",
            ParcelStatus.STORED to "已入库",
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
    private fun simulateArrival() { if (parcels.none { it.code == "A-302-8" }) parcels.add(Parcel("A-302-8", "洗衣液")); save(); refresh(); Toast.makeText(this, "已加入一条模拟到件数据", Toast.LENGTH_SHORT).show() }

    private fun chooseText() { AlertDialog.Builder(this).setTitle("选择到件截图").setMessage("便取件只会读取你选择的截图，用于识别商品信息和取件码，不会读取其他照片。").setNegativeButton("取消", null).setPositiveButton("选择截图") { _, _ -> startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*" }, 9) }.show() }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == 9 && resultCode == RESULT_OK) data?.data?.let { importImage(it) } }
    private fun importImage(uri: Uri) { val image = InputImage.fromFilePath(this, uri); val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()); recognizer.process(image).addOnSuccessListener { result -> val text = result.text.uppercase().replace('－', '-').replace('—', '-').replace('–', '-'); val found = codePattern.matcher(text); var added = 0; while (found.find()) { val code = found.group().replace(Regex("\\s+"), "").replace(Regex("-+"), "-"); if (parcels.none { it.code == code }) { parcels.add(Parcel(code)); added++ } }; save(); refresh(); Toast.makeText(this, if (added == 0) "未识别到货架取件码，请确认截图清晰并包含类似 A-302-8 的编码" else "识别完成，已导入 $added 个取件码", Toast.LENGTH_LONG).show() }.addOnFailureListener { Toast.makeText(this, "图片识别失败，请重试", Toast.LENGTH_LONG).show() } }
    private fun refresh() {
        list.removeAllViews()
        val current = parcels.filter { it.status == selectedStatus }
        summary.text = "${statusLabel(selectedStatus)} · ${current.size} 件"
        if (current.isEmpty()) list.addView(TextView(this).apply { text = "暂无${statusLabel(selectedStatus)}包裹"; textSize = 13f; setTextColor(Color.rgb(104,119,146)); gravity = Gravity.CENTER; setPadding(4, 24, 4, 24) })
        current.forEach { addParcelRow(list, it) }
        for (index in 0 until statusTabs.childCount) { val view = statusTabs.getChildAt(index); view.background = rounded(if (ParcelStatus.values()[index] == selectedStatus) Color.rgb(66,99,235) else Color.rgb(242,245,250), 10f); (view as Button).setTextColor(if (ParcelStatus.values()[index] == selectedStatus) Color.WHITE else Color.rgb(64,81,112)) }
    }
    private fun statusLabel(status: ParcelStatus) = mapOf(ParcelStatus.WAITING to "待入库", ParcelStatus.STORED to "已入库", ParcelStatus.PICKED_UP to "已取件", ParcelStatus.CANCELLED to "已取消")[status]!!
    private fun addParcelRow(container: LinearLayout, parcel: Parcel) {
        val checked = parcel.status == ParcelStatus.PICKED_UP
        val card = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(12, 12, 12, 12); alpha = 0f; background = rounded(if (checked) Color.rgb(241,255,248) else Color.WHITE, 26f); animate().alpha(1f).setDuration(180).start() }
        val product = TextView(this).apply { text = "件"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.rgb(66,99,235)); background = rounded(if (checked) Color.rgb(225,248,235) else Color.rgb(246,243,231), 18f); layoutParams = LinearLayout.LayoutParams(dp(74), dp(92)).apply { rightMargin = dp(12) } }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        info.addView(TextView(this).apply { text = parcel.name; textSize = 15f; setTypeface(null, 1); setTextColor(Color.rgb(23,35,61)) })
        info.addView(TextView(this).apply { text = "截图识别 · 请核对"; textSize = 11f; setTextColor(Color.rgb(104,119,146)); setPadding(0, 5, 0, 10) })
        info.addView(TextView(this).apply { text = parcel.code; textSize = 16f; setTypeface(null, 1); setTextColor(Color.rgb(49,76,126)) })
        val action = Button(this).apply { text = "修改状态"; textSize = 11f; setSingleLine(true); minWidth = 0; minimumWidth = 0; setTextColor(Color.rgb(64,81,112)); background = rounded(Color.rgb(246,248,252), 18f); elevation = 0f; stateListAnimator = null; setPadding(dp(6), dp(4), dp(6), dp(4)); setOnClickListener { chooseStatus(parcel) }; layoutParams = LinearLayout.LayoutParams(dp(94), dp(42)).apply { leftMargin = dp(8) } }
        card.addView(product); card.addView(info); card.addView(action)
        container.addView(card, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })
    }
    private fun chooseStatus(parcel: Parcel) { val labels = arrayOf("待入库", "已入库", "已取件", "已取消"); AlertDialog.Builder(this).setTitle("选择包裹状态").setSingleChoiceItems(labels, parcel.status.ordinal) { dialog, which -> parcel.status = ParcelStatus.values()[which]; parcel.found = parcel.status == ParcelStatus.PICKED_UP; save(); refresh(); dialog.dismiss() }.show() }
    private fun save() { storage.edit().putString("items", parcels.joinToString("\n") { "${it.code}|${it.name}|${it.found}|${it.status.name}" }).apply() }
    private fun load() { storage.getString("items", "")?.lines()?.filter { it.isNotBlank() }?.forEach { val p = it.split("|"); if (p.size >= 3) parcels.add(Parcel(p[0], p[1], p[2] == "true", if (p.size >= 4) runCatching { ParcelStatus.valueOf(p[3]) }.getOrDefault(if (p[2] == "true") ParcelStatus.PICKED_UP else ParcelStatus.WAITING) else if (p[2] == "true") ParcelStatus.PICKED_UP else ParcelStatus.WAITING)) } }
}
