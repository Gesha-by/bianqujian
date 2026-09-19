package com.bianqujian.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.GradientDrawable
import java.util.regex.Pattern

data class Parcel(val code: String, val name: String = "未提供商品名", var found: Boolean = false)

class MainActivity : Activity() {
    private val parcels = mutableListOf<Parcel>()
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView
    private val storage by lazy { getSharedPreferences("parcels", MODE_PRIVATE) }
    private val codePattern = Pattern.compile("\\b[A-Z]{1,3}[- ]?\\d{1,4}[- ]?\\d{1,4}\\b")

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); load(); render() }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 34, 28, 24); setBackgroundColor(Color.rgb(246,248,253)) }
        val back = Button(this).apply { text = "返回介绍"; setTextColor(Color.rgb(49,92,255)); background = rounded(Color.rgb(233,239,255), 18f); setOnClickListener { finish() } }
        val title = TextView(this).apply { text = "便取件"; textSize = 30f; setTextColor(Color.rgb(20,35,70)); setTypeface(null, 1); setPadding(0, 22, 0, 8) }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 20, 22, 20); background = rounded(Color.rgb(73,105,247), 28f) }
        hero.addView(TextView(this).apply { text = "下楼前，先看清楚要找什么"; textSize = 14f; setTextColor(Color.WHITE) })
        hero.addView(TextView(this).apply { text = "我的取件点 · 暂无包裹"; textSize = 25f; setTextColor(Color.WHITE); setTypeface(null, 1); setPadding(0, 8, 0, 14) })
        hero.addView(TextView(this).apply { text = "● 暂无待取件"; textSize = 13f; setTextColor(Color.WHITE); background = rounded(Color.argb(45,255,255,255), 18f); setPadding(12, 9, 12, 9) })
        val import = Button(this).apply { text = "导入到件长截图"; textSize = 16f; setTextColor(Color.rgb(25,38,70)); background = rounded(Color.WHITE, 18f); setOnClickListener { chooseText() }; setPadding(16, 18, 16, 18) }
        summary = TextView(this).apply { textSize = 16f; setTextColor(Color.rgb(20,35,70)); setPadding(0, 22, 0, 10) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val handoff = TextView(this).apply { text = "导入包裹后开始找件\n\n找到包裹后，再打开拼多多扫码出库"; textSize = 16f; setTextColor(Color.WHITE); setPadding(20, 20, 20, 20); background = rounded(Color.rgb(20,35,70), 24f) }
        root.addView(back); root.addView(title); root.addView(hero); root.addView(import, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 14 }); root.addView(summary); root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(handoff); setContentView(root); refresh()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun chooseText() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "text/plain"; addCategory(Intent.CATEGORY_OPENABLE) }, 9) }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == 9 && resultCode == RESULT_OK) data?.data?.let { importText(it) } }
    private fun importText(uri: Uri) { val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return; val found = codePattern.matcher(text); var added = 0; while (found.find()) { val code = found.group().replace(" ", "-"); if (parcels.none { it.code == code }) { parcels.add(Parcel(code)); added++ } }; save(); refresh(); Toast.makeText(this, "已导入 $added 个取件码", Toast.LENGTH_SHORT).show() }
    private fun refresh() { list.removeAllViews(); val found = parcels.count { it.found }; summary.text = if (parcels.isEmpty()) "暂无包裹，请先导入到件截图" else "已找到 $found / ${parcels.size}"; parcels.forEach { parcel -> val row = CheckBox(this).apply { text = "${parcel.code} · ${parcel.name}"; textSize = 17f; isChecked = parcel.found; setPadding(8, 18, 8, 18); setOnCheckedChangeListener { _, checked -> parcel.found = checked; save(); summary.text = "已找到 ${parcels.count { it.found }} / ${parcels.size}" } }; list.addView(row) } }
    private fun save() { storage.edit().putString("items", parcels.joinToString("\n") { "${it.code}|${it.name}|${it.found}" }).apply() }
    private fun load() { storage.getString("items", "")?.lines()?.filter { it.isNotBlank() }?.forEach { val p = it.split("|"); if (p.size >= 3) parcels.add(Parcel(p[0], p[1], p[2] == "true")) } }
}
