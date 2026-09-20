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
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import java.util.regex.Pattern
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator

enum class ParcelStatus { IN_TRANSIT, READY, PICKED_UP, CANCELLED }
data class Parcel(val code: String, val name: String = "未提供商品名", var found: Boolean = false, var status: ParcelStatus = ParcelStatus.READY, val source: String = "截图识别", val location: String = "未识别位置", val parcelType: String = "未知类型", val carrier: String = "未知快递", val trackingNumber: String = "未知运单号", val updatedAt: String = "未知时间", val imagePath: String = "")

class MainActivity : Activity() {
    private val parcels = mutableListOf<Parcel>()
    private lateinit var list: LinearLayout
    private lateinit var completedList: LinearLayout
    private lateinit var statusTabs: LinearLayout
    private var selectedStatus: ParcelStatus? = null
    private var locationSummary = "取件点以截图识别结果为准"
    private lateinit var summary: TextView
    private lateinit var homePage: View
    private lateinit var minePage: View
    private lateinit var handoffNote: View
    private lateinit var handoff: View
    private lateinit var homeNavItem: TextView
    private lateinit var mineNavItem: TextView
    private lateinit var navPill: View
    private var pillAnimator: ValueAnimator? = null
    private val iosSpringInterpolator by lazy { PathInterpolator(0.32f, 0.72f, 0.35f, 1f) }
    private val storage by lazy { getSharedPreferences("parcels", MODE_PRIVATE) }
    private val codePattern = Pattern.compile("(?<![A-Z0-9])[A-Z]{1,3}\\s*[-—–－]?\\s*\\d{1,4}(?:\\s*[-—–－]\\s*\\d{1,4}){1,2}(?![A-Z0-9])")
    private val updateReceiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { parcels.clear(); load(); refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); load(); render() }
    override fun onResume() {
        super.onResume()
        registerReceiver(updateReceiver, IntentFilter(ParcelNotificationListener.ACTION_UPDATED), RECEIVER_NOT_EXPORTED)
        render()
    }
    override fun onPause() { unregisterReceiver(updateReceiver); super.onPause() }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16); setBackgroundColor(Color.rgb(247,248,252)) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val topInset = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            view.setPadding(dp(20), topInset + dp(16), dp(20), dp(16))
            insets
        }
        root.requestApplyInsets()
        val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(2, 5, 2, 15) }
        title.addView(ImageView(this).apply { setImageResource(com.bianqujian.app.R.drawable.ic_bqj_app); scaleType = ImageView.ScaleType.CENTER_INSIDE }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(10) })
        title.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply { text = "便取件"; textSize = 20f; setTextColor(Color.rgb(28,28,30)); setTypeface(null, 1) })
            addView(TextView(this@MainActivity).apply { text = "下楼前，先看清楚要找什么"; textSize = 10f; setTextColor(Color.rgb(142,142,147)); setPadding(0, 2, 0, 0) })
        })
        val activePickupPoint = parcels.firstOrNull { it.status == ParcelStatus.READY && it.location != "未识别位置" }?.location
        val frequentPickupPoint = storage.getString("frequent_pickup_point", "").orEmpty()
        val pickupTitle = if (activePickupPoint == null && frequentPickupPoint.isNotBlank()) "常用取件点" else "我的取件点"
        val pickupPoint = activePickupPoint ?: frequentPickupPoint.ifBlank { "暂无待取件" }
        val pickupTip = when { activePickupPoint != null -> "当前待取件的截图识别结果"; frequentPickupPoint.isNotBlank() -> "基于本机历史取件记录自动学习"; else -> "导入取件截图后自动识别" }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14); background = rounded(Color.WHITE, 16f); elevation = dp(1).toFloat() }
        hero.addView(TextView(this).apply { text = pickupTitle; textSize = 11f; setTextColor(Color.rgb(66,99,235)); setTypeface(null, 1) })
        hero.addView(TextView(this).apply { text = pickupPoint; textSize = 15f; setTextColor(Color.rgb(28,28,30)); setTypeface(null, 1); setPadding(0, 5, 0, 8) })
        hero.addView(TextView(this).apply { text = "ⓘ  $pickupTip"; textSize = 10f; setTextColor(Color.rgb(66,99,235)); background = rounded(Color.rgb(242,242,247), 16f); setPadding(9, 6, 9, 6) })
        val import = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(16, 14, 10, 14); background = rounded(Color.rgb(232,238,255), 22f) }
        import.addView(TextView(this).apply { text = "第一步  导入到件截图\n识别后确认，再加入找件清单"; textSize = 13f; setTypeface(null, 1); setTextColor(Color.rgb(28,48,92)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        import.addView(Button(this).apply { text = "开始识别"; textSize = 13f; setTypeface(null, 1); minHeight = dp(48); setTextColor(Color.WHITE); background = rounded(Color.rgb(66,99,235), 18f); elevation = 0f; stateListAnimator = null; setOnClickListener { chooseText() } })
        val simulate = Button(this).apply { text = "模拟到件数据（测试）"; textSize = 10f; setTextColor(Color.rgb(104,119,146)); background = rounded(Color.rgb(242,245,250), 16f); elevation = 0f; stateListAnimator = null; setOnClickListener { simulateArrival() }; visibility = if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) View.VISIBLE else View.GONE }
        val simulatePicked = Button(this).apply { text = "模拟拼多多已取件通知（测试）"; textSize = 10f; setTextColor(Color.rgb(104,119,146)); background = rounded(Color.rgb(242,245,250), 16f); elevation = 0f; stateListAnimator = null; setOnClickListener { simulatePickedUpNotification() }; visibility = if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) View.VISIBLE else View.GONE }
        val autoSync = Button(this).apply { text = if (isNotificationAccessEnabled()) "通知自动同步已开启" else "开启通知自动同步"; textSize = 11f; setTextColor(Color.rgb(66,99,235)); background = rounded(Color.rgb(237,241,255), 16f); elevation = 0f; stateListAnimator = null; setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
        statusTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 14, 0, 4) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        completedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addStatusTabs()
        summary = TextView(this).apply { textSize = 14f; setTextColor(Color.rgb(23,35,61)); setTypeface(null, 1); setPadding(12, 18, 2, 8) }
        handoffNote = TextView(this).apply { text = "取到包裹后，再进行最后一步"; textSize = 12f; setTextColor(Color.rgb(92,103,126)); setPadding(2, 16, 2, 6) }
        handoff = Button(this).apply { text = "第三步  打开拼多多扫描取件"; textSize = 15f; setTypeface(null, 1); setTextColor(Color.WHITE); background = rounded(Color.rgb(23,35,61), 24f); elevation = 0f; stateListAnimator = null; setPadding(16, 16, 16, 16); setOnClickListener { choosePddOpenMode() } }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 12) }
        content.addView(title); content.addView(hero); content.addView(import, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 14 }); content.addView(autoSync, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 7 }); content.addView(simulate, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 7 }); content.addView(simulatePicked, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 7 }); content.addView(statusTabs); content.addView(summary); content.addView(list)
        homePage = ScrollView(this).apply { isFillViewport = true; addView(content) }
        minePage = buildMinePage()
        minePage.visibility = View.GONE
        val bottomNav = buildBottomNavigation()
        root.addView(homePage, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(minePage, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(handoffNote)
        root.addView(handoff, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 6 })
        root.addView(bottomNav, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(dp(20), bars.top + dp(24), dp(20), bars.bottom + dp(8))
            insets
        }
        setContentView(root); root.requestApplyInsets(); refresh()
    }

    private fun buildMinePage(): View {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(16)) }
        content.addView(TextView(this).apply { text = "我的"; textSize = 26f; setTypeface(null, 1); setTextColor(Color.rgb(28,28,30)); setPadding(dp(2), dp(8), dp(2), dp(18)) })
        content.addView(TextView(this).apply { text = "便取件设置"; textSize = 13f; setTypeface(null, 1); setTextColor(Color.rgb(104,119,146)); setPadding(dp(2), 0, dp(2), dp(8)) })
        val notification = TextView(this).apply { text = if (isNotificationAccessEnabled()) "通知自动同步　已开启" else "通知自动同步　未开启"; textSize = 15f; setTextColor(Color.rgb(23,35,61)); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = rounded(Color.WHITE, 16f); setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
        content.addView(notification, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })
        val point = storage.getString("frequent_pickup_point", "").orEmpty().ifBlank { "暂无记录" }
        content.addView(TextView(this).apply { text = "常用取件点\n$point"; textSize = 15f; setTextColor(Color.rgb(23,35,61)); setPadding(dp(16), dp(14), dp(16), dp(14)); background = rounded(Color.WHITE, 16f) }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(TextView(this).apply { text = "更多设置将在后续版本逐步加入"; textSize = 12f; setTextColor(Color.rgb(142,142,147)); setPadding(dp(2), dp(24), dp(2), 0) })
        return ScrollView(this).apply { isFillViewport = true; addView(content) }
    }

    private fun buildBottomNavigation(): FrameLayout {
        val nav = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(60))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(Color.argb(210, 255, 255, 255), 24f)
            elevation = dp(8).toFloat()
        }
        navPill = GlassPillView(this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP))
            }
        }
        nav.addView(navPill, FrameLayout.LayoutParams(dp(84), dp(46)).apply { gravity = Gravity.CENTER_VERTICAL })
        val items = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun item(label: String, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = label; textSize = 12f; gravity = Gravity.CENTER; includeFontPadding = true; setTypeface(null, 1); setTextColor(if (selected) Color.rgb(0,0,0) else Color.rgb(104,119,146)); setPadding(0, dp(4), 0, dp(4)); setOnClickListener { onClick() }
        }
        homeNavItem = item("⌂\n首页", true) { showPage(true) }
        mineNavItem = item("○\n我的", false) { showPage(false) }
        items.addView(homeNavItem, LinearLayout.LayoutParams(0, dp(60), 1f))
        items.addView(mineNavItem, LinearLayout.LayoutParams(0, dp(60), 1f))
        nav.addView(items, FrameLayout.LayoutParams(-1, -1))
        nav.post { updatePillPosition(true) }
        return nav
    }

    private fun showPage(home: Boolean) {
        val entering = if (home) homePage else minePage
        val leaving = if (home) minePage else homePage
        if (leaving.visibility == View.VISIBLE && entering.visibility == View.GONE) {
            entering.translationX = if (home) -dp(22).toFloat() else dp(22).toFloat()
            entering.alpha = 0f
            entering.visibility = View.VISIBLE
            leaving.animate().translationX(if (home) dp(22).toFloat() else -dp(22).toFloat()).alpha(0f).setDuration(320).setInterpolator(iosSpringInterpolator).withEndAction {
                leaving.visibility = View.GONE
                leaving.translationX = 0f
            }.start()
            entering.animate().translationX(0f).alpha(1f).setDuration(360).setInterpolator(iosSpringInterpolator).start()
        } else {
            homePage.visibility = if (home) View.VISIBLE else View.GONE
            minePage.visibility = if (home) View.GONE else View.VISIBLE
        }
        handoffNote.visibility = if (home) View.VISIBLE else View.GONE
        handoff.visibility = if (home) View.VISIBLE else View.GONE
        homeNavItem.setTextColor(if (home) Color.rgb(0,0,0) else Color.rgb(104,119,146))
        mineNavItem.setTextColor(if (home) Color.rgb(104,119,146) else Color.rgb(0,0,0))
        updatePillPosition(home)
    }

    private fun updatePillPosition(home: Boolean) {
        val nav = navPill.parent as? FrameLayout ?: return
        if (nav.width == 0) { nav.post { updatePillPosition(home) }; return }
        val navWidth = nav.width - nav.paddingLeft - nav.paddingRight
        val pillWidth = navPill.width.takeIf { it > 0 } ?: dp(80)
        val targetX = nav.paddingLeft + (if (home) navWidth * 0.25f else navWidth * 0.75f) - pillWidth / 2f
        pillAnimator?.cancel()
        val startX = navPill.translationX
        val startScale = navPill.scaleX.coerceAtLeast(0.1f)
        pillAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = iosSpringInterpolator
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                navPill.translationX = startX + (targetX - startX) * t
                val scale = startScale + (1f - startScale) * t - 0.04f * kotlin.math.sin(t * Math.PI).toFloat()
                navPill.scaleX = scale.coerceIn(0.92f, 1.08f)
                navPill.scaleY = scale.coerceIn(0.92f, 1.08f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    navPill.translationX = targetX
                    navPill.scaleX = 1f
                    navPill.scaleY = 1f
                }
            })
            start()
        }
    }

    private fun addStatusTabs() {
        val tabs = listOf(null to "全部", ParcelStatus.READY to "待取件", ParcelStatus.PICKED_UP to "已取件", ParcelStatus.CANCELLED to "已取消")
        tabs.forEach { (status, label) ->
            statusTabs.addView(Button(this).apply {
                text = label; textSize = 12f; setTypeface(null, 1); minWidth = 0; minimumWidth = 0; minHeight = dp(46); setPadding(2, 7, 2, 7); elevation = 0f; stateListAnimator = null; background = rounded(if (status == ParcelStatus.READY) Color.rgb(66,99,235) else Color.rgb(242,244,249), 16f); setTextColor(if (status == ParcelStatus.READY) Color.WHITE else Color.rgb(64,81,112))
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
    private fun simulateArrival() {
        val code = "A-302-8"
        val index = parcels.indexOfFirst { it.code == code }
        val item = Parcel(code, "洗衣液", false, ParcelStatus.READY, "模拟到件", "妈妈驿站", "普通快递", "圆通", "YT000000000000", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()), "")
        if (index < 0) parcels.add(item) else parcels[index] = item
        save(); refresh(); Toast.makeText(this, "已加入一条模拟到件数据", Toast.LENGTH_SHORT).show()
    }
    private fun simulatePickedUpNotification() {
        val code = "A-302-8"
        val index = parcels.indexOfFirst { it.code == code }
        val item = Parcel(code, "洗衣液", true, ParcelStatus.PICKED_UP, "拼多多通知（模拟）", "妈妈驿站", "普通快递", "圆通", "YT000000000000", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()), "")
        if (index < 0) parcels.add(item) else parcels[index] = item
        save(); refresh(); Toast.makeText(this, "已模拟拼多多通知：洗衣液已取件", Toast.LENGTH_LONG).show()
    }

    private fun chooseText() { AlertDialog.Builder(this).setTitle("选择到件截图").setMessage("便取件只会读取你选择的截图，用于识别商品信息和取件码，不会读取其他照片。").setNegativeButton("取消", null).setPositiveButton("选择截图") { _, _ -> startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*" }, 9) }.show() }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == 9 && resultCode == RESULT_OK) data?.data?.let { importImage(it) } }
    private fun importImage(uri: Uri) { val image = InputImage.fromFilePath(this, uri); val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()); recognizer.process(image).addOnSuccessListener { result -> val rawText = result.text; val text = rawText.uppercase().replace('－', '-').replace('—', '-').replace('–', '-'); val normalizedText = text.replace(Regex("\\s+"), ""); val found = codePattern.matcher(normalizedText); val location = extractOcrLocation(rawText); val carrier = extractOcrCarrier(rawText); val tracking = extractOcrTracking(rawText); val updatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()); val imagePath = saveProductImage(uri, result); var added = 0; var updated = 0; while (found.find()) { val code = found.group().replace(Regex("\\s+"), "").replace(Regex("-+"), "-"); val index = parcels.indexOfFirst { it.code == code }; val oldName = if (index >= 0) parcels[index].name else ""; val oldImagePath = if (index >= 0) parcels[index].imagePath else ""; val recognizedName = extractOcrName(rawText); val name = if (recognizedName != "商品名待确认") recognizedName else if (oldName.isBlank() || oldName == "未提供商品名") "商品名待确认" else oldName; val item = Parcel(code, name, false, ParcelStatus.READY, "截图识别", location, "普通快递", carrier, tracking, updatedAt, imagePath); if (index < 0) { parcels.add(item); added++ } else { parcels[index] = item; if (oldImagePath.isNotBlank() && oldImagePath != imagePath) deleteImage(oldImagePath); updated++ } }; if (added + updated > 0 && location != "未识别位置") storage.edit().putString("frequent_pickup_point", location).apply(); save(); refresh(); val message = when { added == 0 && updated == 0 -> "未识别到取件码，请确认截图包含类似 B8-5-153 或 A-302-8 的编码"; updated > 0 -> "识别完成，已更新 $updated 个包裹，商品图和物流信息已保存"; else -> "识别完成，已导入 $added 个取件码，商品图和物流信息已保存" }; Toast.makeText(this, message, Toast.LENGTH_LONG).show() }.addOnFailureListener { Toast.makeText(this, "图片识别失败，请重试", Toast.LENGTH_LONG).show() } }
    private fun saveProductImage(uri: Uri, result: com.google.mlkit.vision.text.Text): String {
        val source = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        val file = File(filesDir, "product_${System.currentTimeMillis()}.jpg")
        if (source == null) return ""
        val anchor = result.textBlocks.flatMap { it.lines }.firstOrNull { line ->
            val value = line.text.uppercase()
            value.contains("快递") || value.contains("运单") || value.contains("${extractOcrTracking(result.text)}")
        }
        val box = anchor?.boundingBox
        val crop = if (box != null) {
            val size = (box.height() * 2.8f).toInt().coerceAtLeast(80)
            val left = 0
            val top = (box.centerY() - size / 2).coerceIn(0, (source.height - 1).coerceAtLeast(0))
            val width = size.coerceAtMost(source.width)
            val height = size.coerceAtMost(source.height - top)
            Bitmap.createBitmap(source, left, top, width, height)
        } else null
        (crop ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)).compress(Bitmap.CompressFormat.JPEG, 90, FileOutputStream(file))
        if (crop == null) file.delete()
        source.recycle()
        crop?.recycle()
        return if (crop == null) "" else file.absolutePath
    }
    private fun extractOcrLocation(text: String): String = Regex("(?:取件点|驿站|收货地址|地址)[：:]?\\s*([^\\n]{4,60})").find(text)?.groupValues?.get(1)?.trim()?.replace(Regex("^[1Il|丨]+(?=北区|妈妈驿站|驿站)"), "")?.trim()?.ifBlank { "未识别位置" } ?: "未识别位置"
    private fun extractOcrCarrier(text: String): String = Regex("(顺丰|中通|圆通|申通|韵达|极兔|邮政|京东|德邦|菜鸟)").find(text)?.groupValues?.get(1) ?: "未知快递"
    private fun extractOcrTracking(text: String): String = Regex("(?<![A-Z0-9])(?:SF|YT|ZT|JD|JT)?[A-Z0-9]{8,20}(?![A-Z0-9])").find(text.uppercase())?.value ?: "未知运单号"
    private fun extractOcrName(text: String): String {
        val blocked = Regex("收货地址|快递员|取件码|订单编号|待取件|已取件|已签收|您的快件|快件己|快件已|到达|代收点|复制|分享取件|拨打电话|导航|支持退换货|号\\s*码保护|品牌|包装|后天达|可伶可俐|全店|销量|正品|^\\d{1,2}:\\d{2}|^\\d{1,3}%?$|^5G$|^Wi-?Fi$|^¥?[\\d.]+$")
        val productHints = Regex("毽球|羽毛球|乒乓球|洗衣液|洗发水|沐浴露|牙膏|纸巾|吸油纸|面膜|零食|水杯|衣服|鞋|袜|耳机|充电|玩具|文具|清洁|日用品|护肤|化妆|食品|手机|油控|控油|oil|clean|film", RegexOption.IGNORE_CASE)
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val productArea = lines.dropWhile { !it.contains("订单编号") }.drop(1)
        val candidates = (productArea + lines).distinct()
            .filter { it.length in 4..80 && !blocked.containsMatchIn(it) && it.any { ch -> ch in '\u4e00'..'\u9fff' } }
            .filterNot { it.contains("全店回购") || it.contains("本店已拼") || it.contains("销量") || it.contains("退货包运费") || it.contains("驿站") || it.contains("文具店") }
        val productCandidates = candidates.filter { candidate ->
            productHints.containsMatchIn(candidate) &&
                candidate.count { it in '\u4e00'..'\u9fff' } >= 2 &&
                !candidate.matches(Regex(".*(取件|地址|快递|驿站|导航|电话|订单|编号|昨天|今天|明天|后天).*"))
        }
        return productCandidates.maxByOrNull { candidate ->
            (if (productArea.contains(candidate)) 1000 else 0) +
                (if (productHints.containsMatchIn(candidate)) 200 else 0) +
                candidate.count { it in '\u4e00'..'\u9fff' } * 10 + candidate.length
        } ?: "商品名待确认"
    }
    private fun refresh() {
        list.removeAllViews()
        val current = if (selectedStatus == null) parcels.filter { it.status != ParcelStatus.IN_TRANSIT } else parcels.filter { it.status == selectedStatus }
        summary.text = "第二步  ${statusLabel(selectedStatus)} · ${current.size} 件"
        if (current.isEmpty()) list.addView(TextView(this).apply { text = "暂无${statusLabel(selectedStatus)}包裹"; textSize = 13f; setTextColor(Color.rgb(104,119,146)); gravity = Gravity.CENTER; setPadding(4, 24, 4, 24) })
        current.forEach { addParcelRow(list, it) }
        val filters = listOf<ParcelStatus?>(null, ParcelStatus.READY, ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED)
        for (index in 0 until statusTabs.childCount) { val filter = filters[index]; val view = statusTabs.getChildAt(index) as Button; val count = if (filter == null) parcels.count { it.status != ParcelStatus.IN_TRANSIT } else parcels.count { it.status == filter }; view.text = "${statusLabel(filter)}（$count）"; view.background = rounded(if (filter == selectedStatus) Color.rgb(66,99,235) else Color.rgb(242,245,250), 16f); view.setTextColor(if (filter == selectedStatus) Color.WHITE else Color.rgb(64,81,112)) }
    }
    private fun statusLabel(status: ParcelStatus?) = when (status) { null -> "全部"; ParcelStatus.READY -> "待取件"; ParcelStatus.PICKED_UP -> "已取件"; ParcelStatus.CANCELLED -> "已取消"; ParcelStatus.IN_TRANSIT -> "运输中" }
    private fun addParcelRow(container: LinearLayout, parcel: Parcel) {
        val checked = parcel.status == ParcelStatus.PICKED_UP
        val card = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(12, 12, 12, 12); alpha = 1f; background = rounded(if (checked) Color.rgb(241,255,248) else Color.WHITE, 26f) }
        val product: View = if (parcel.name.contains("毽球")) ProductIllustrationView(this, "毽球") else if (parcel.imagePath.isNotBlank() && File(parcel.imagePath).exists()) ImageView(this).apply { setImageBitmap(BitmapFactory.decodeFile(parcel.imagePath)); scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(if (checked) Color.rgb(225,248,235) else Color.rgb(246,243,231), 18f) } else ProductIllustrationView(this, "商品待确认")
        product.layoutParams = LinearLayout.LayoutParams(dp(74), dp(92)).apply { rightMargin = dp(12) }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        info.addView(TextView(this).apply { text = parcel.name; textSize = 15f; setTypeface(null, 1); setTextColor(Color.rgb(23,35,61)) })
        info.addView(TextView(this).apply { text = "${parcel.parcelType} · ${parcel.carrier}"; textSize = 11f; setTextColor(Color.rgb(104,119,146)); setPadding(0, 5, 0, 4) })
        info.addView(TextView(this).apply { text = "${parcel.source} · ${parcel.location} · ${parcel.updatedAt}"; textSize = 10f; setTextColor(Color.rgb(104,119,146)); setPadding(0, 0, 0, 6) })
        info.addView(TextView(this).apply { text = parcel.code; textSize = 16f; setTypeface(null, 1); setTextColor(Color.rgb(49,76,126)) })
        card.addView(product); card.addView(info)
        container.addView(card, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })
    }
    private class ProductIllustrationView(context: Context, private val label: String) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            paint.color = Color.rgb(246, 243, 231); canvas.drawRoundRect(0f, 0f, w, h, 18f, 18f, paint)
            if (label.contains("毽球")) {
                paint.color = Color.rgb(78, 180, 92); canvas.drawOval(w * .32f, h * .56f, w * .68f, h * .78f, paint)
                paint.color = Color.rgb(241, 94, 89); canvas.drawCircle(w * .5f, h * .68f, w * .11f, paint)
                paint.color = Color.WHITE
                val feather = Path().apply { moveTo(w * .5f, h * .58f); lineTo(w * .27f, h * .2f); lineTo(w * .4f, h * .27f); lineTo(w * .5f, h * .1f); lineTo(w * .6f, h * .27f); lineTo(w * .73f, h * .2f); close() }
                canvas.drawPath(feather, paint)
                paint.color = Color.rgb(66, 99, 235); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; canvas.drawLine(w * .5f, h * .58f, w * .5f, h * .16f, paint); paint.style = Paint.Style.FILL
            } else {
                paint.color = Color.rgb(66, 99, 235); canvas.drawRoundRect(w * .23f, h * .25f, w * .77f, h * .67f, 8f, 8f, paint)
                paint.color = Color.rgb(255, 218, 104); canvas.drawRect(w * .23f, h * .25f, w * .77f, h * .35f, paint)
                paint.color = Color.WHITE; canvas.drawCircle(w * .5f, h * .5f, w * .08f, paint)
            }
            paint.color = Color.rgb(66, 99, 235); paint.textSize = 10f; paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER; canvas.drawText(label, w / 2f, h * .92f, paint); paint.textAlign = Paint.Align.LEFT
        }
    }
    private class GlassPillView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat(); val r = h / 2f
            shadowPaint.color = Color.argb(45, 0, 0, 0)
            canvas.drawRoundRect(2f, 4f, w + 2f, h + 4f, r, r, shadowPaint)
            paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.argb(205, 255, 255, 255), Color.argb(165, 245, 245, 250), Color.argb(145, 230, 230, 235)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            paint.shader = LinearGradient(0f, 0f, 0f, h * 0.55f, Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(1.5f, 1.5f, w - 1.5f, h * 0.55f, r, r, paint)
            paint.shader = LinearGradient(0f, h * 0.72f, 0f, h - 1.5f, Color.argb(0, 255, 255, 255), Color.argb(70, 255, 255, 255), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(1.5f, h * 0.72f, w - 1.5f, h - 1.5f, r, r, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f; paint.color = Color.argb(50, 255, 255, 255)
            canvas.drawRoundRect(1.5f, 1.5f, w - 1.5f, h - 1.5f, r, r, paint); paint.style = Paint.Style.FILL
        }
    }
    private fun deleteImage(path: String) { if (path.isNotBlank()) runCatching { File(path).takeIf { it.isFile }?.delete() } }
    private fun pruneHistory() {
        val terminal = parcels.filter { it.status == ParcelStatus.PICKED_UP || it.status == ParcelStatus.CANCELLED }
        val excess = terminal.sortedByDescending { it.updatedAt }.drop(MAX_TERMINAL_HISTORY)
        excess.forEach { deleteImage(it.imagePath); parcels.remove(it) }
    }
    private fun save() { pruneHistory(); storage.edit().putString("items", parcels.joinToString("\n") { "${it.code}|${it.name}|${it.found}|${it.status.name}|${it.source}|${it.location}|${it.parcelType}|${it.carrier}|${it.trackingNumber}|${it.updatedAt}|${it.imagePath}" }).apply() }
    private fun load() { storage.getString("items", "")?.lines()?.filter { it.isNotBlank() }?.forEach { val p = it.split("|"); if (p.size >= 3) parcels.add(Parcel(p[0], p[1], p[2] == "true", if (p.size >= 4) runCatching { ParcelStatus.valueOf(p[3]) }.getOrDefault(if (p[2] == "true") ParcelStatus.PICKED_UP else ParcelStatus.READY) else if (p[2] == "true") ParcelStatus.PICKED_UP else ParcelStatus.READY, p.getOrElse(4) { "截图识别" }, p.getOrElse(5) { "未识别位置" }, p.getOrElse(6) { "未知类型" }, p.getOrElse(7) { "未知快递" }, p.getOrElse(8) { "未知运单号" }, p.getOrElse(9) { "未知时间" }, p.getOrElse(10) { "" })) }; pruneHistory() }
    companion object { private const val MAX_TERMINAL_HISTORY = 100 }
}
