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
import android.view.ViewGroup
import android.widget.*
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import java.util.regex.Pattern

data class Parcel(val code: String, val name: String = "未提供商品名", var found: Boolean = false)

class MainActivity : Activity() {
    private val parcels = mutableListOf<Parcel>()
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView
    private val storage by lazy { getSharedPreferences("parcels", MODE_PRIVATE) }
    private val codePattern = Pattern.compile("(?<![A-Z0-9])[A-Z]{1,3}\\s*[-—–－]?\\s*\\d{1,4}\\s*[-—–－]\\s*\\d{1,4}(?![A-Z0-9])")
    private val updateReceiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { parcels.clear(); load(); refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); load(); render() }
    override fun onResume() { super.onResume(); registerReceiver(updateReceiver, IntentFilter(ParcelNotificationListener.ACTION_UPDATED), RECEIVER_NOT_EXPORTED) }
    override fun onPause() { unregisterReceiver(updateReceiver); super.onPause() }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 22, 24, 18); setBackgroundColor(Color.rgb(247,248,252)) }
        val title = TextView(this).apply { text = "便取件                                      ⋯"; textSize = 25f; setTextColor(Color.rgb(23,35,61)); setTypeface(null, 1); setPadding(0, 14, 0, 18) }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 19, 20, 19); background = rounded(Color.rgb(66,99,235), 24f) }
        hero.addView(TextView(this).apply { text = "下楼前，先看清楚要找什么"; textSize = 14f; setTextColor(Color.WHITE) })
        hero.addView(TextView(this).apply { text = "我的取件点 · 暂无包裹"; textSize = 24f; setTextColor(Color.WHITE); setTypeface(null, 1); setPadding(0, 8, 0, 13) })
        hero.addView(TextView(this).apply { text = "● 今天取件 · 预计 3 分钟"; textSize = 12f; setTextColor(Color.WHITE); background = rounded(Color.argb(45,255,255,255), 18f); setPadding(11, 8, 11, 8) })
        val import = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(16, 13, 10, 13); background = rounded(Color.WHITE, 17f) }
        import.addView(TextView(this).apply { text = "导入订单长截图\n一次识别多个商品，确认后加入清单"; textSize = 13f; setTextColor(Color.rgb(42,55,82)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        import.addView(Button(this).apply { text = "识别图片"; textSize = 12f; setTextColor(Color.rgb(66,99,235)); background = rounded(Color.rgb(237,241,255), 11f); setOnClickListener { chooseText() } })
        summary = TextView(this).apply { textSize = 16f; setTextColor(Color.rgb(23,35,61)); setTypeface(null, 1); setPadding(2, 22, 2, 10) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val handoff = Button(this).apply { text = "找到包裹后，再打开拼多多扫码出库"; textSize = 14f; setTextColor(Color.WHITE); background = rounded(Color.rgb(23,35,61), 18f); setPadding(16, 16, 16, 16); setOnClickListener { Toast.makeText(this@MainActivity, "正式版将打开拼多多扫码出库", Toast.LENGTH_SHORT).show() } }
        root.addView(title); root.addView(hero); root.addView(import, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 14 }); root.addView(summary); root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(handoff); setContentView(root); refresh()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun chooseText() { AlertDialog.Builder(this).setTitle("选择到件截图").setMessage("便取件只会读取你选择的截图，用于识别商品信息和取件码，不会读取其他照片。").setNegativeButton("取消", null).setPositiveButton("选择截图") { _, _ -> startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*" }, 9) }.show() }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == 9 && resultCode == RESULT_OK) data?.data?.let { importImage(it) } }
    private fun importImage(uri: Uri) { val image = InputImage.fromFilePath(this, uri); val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()); recognizer.process(image).addOnSuccessListener { result -> val text = result.text.uppercase().replace('－', '-').replace('—', '-').replace('–', '-'); val found = codePattern.matcher(text); var added = 0; while (found.find()) { val code = found.group().replace(Regex("\\s+"), "").replace(Regex("-+"), "-"); if (parcels.none { it.code == code }) { parcels.add(Parcel(code)); added++ } }; save(); refresh(); Toast.makeText(this, if (added == 0) "未识别到货架取件码，请确认截图清晰并包含类似 A-302-8 的编码" else "识别完成，已导入 $added 个取件码", Toast.LENGTH_LONG).show() }.addOnFailureListener { Toast.makeText(this, "图片识别失败，请重试", Toast.LENGTH_LONG).show() } }
    private fun refresh() { list.removeAllViews(); val found = parcels.count { it.found }; summary.text = if (parcels.isEmpty()) "先按货架号找件                         已找到 0 / 0" else "先按货架号找件                         已找到 $found / ${parcels.size}"; parcels.forEach { parcel -> val row = CheckBox(this).apply { text = "${parcel.code} · ${parcel.name}"; textSize = 15f; isChecked = parcel.found; setTextColor(if (parcel.found) Color.rgb(17,132,91) else Color.rgb(23,35,61)); setPadding(8, 16, 8, 16); setBackgroundColor(if (parcel.found) Color.rgb(241,255,248) else Color.WHITE); setOnCheckedChangeListener { _, checked -> parcel.found = checked; save(); refresh() } }; list.addView(row, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }) } }
    private fun save() { storage.edit().putString("items", parcels.joinToString("\n") { "${it.code}|${it.name}|${it.found}" }).apply() }
    private fun load() { storage.getString("items", "")?.lines()?.filter { it.isNotBlank() }?.forEach { val p = it.split("|"); if (p.size >= 3) parcels.add(Parcel(p[0], p[1], p[2] == "true")) } }
}
