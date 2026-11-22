package com.syw.figlogsample

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val fitZone: ZoneId get() = ZoneId.systemDefault()
internal val fitDate = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN)
internal val fitTime = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
internal fun LocalDate.startMillis() = atStartOfDay(fitZone).toInstant().toEpochMilli()
internal fun Long.localTime() = Instant.ofEpochMilli(this).atZone(fitZone)
internal fun Double.displayWeight() = java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

abstract class FitLogScreen : AppCompatActivity() {
    protected val green = Color.rgb(8, 121, 79)
    protected val ink = Color.rgb(22, 32, 47)
    protected val muted = Color.rgb(89, 100, 119)

    protected fun showScreen(layout: Int, active: Int, activeAction: () -> Unit = {}) {
        setContentView(layout)
        findViewById<View>(R.id.fitScreenBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fitNextRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val tabs = listOf(
            Triple(R.id.fitNavHome, R.drawable.fitlog_home, MainActivity::class.java),
            Triple(R.id.fitNavRoutine, R.drawable.fitlog_routin, S02RoutineListActivity::class.java),
            Triple(R.id.fitNavHistory, R.drawable.fitlog_record, S06WorkoutHistoryActivity::class.java),
            Triple(R.id.fitNavSchedule, R.drawable.fitlog_schedule, S08WorkoutScheduleActivity::class.java)
        )
        for ((id, icon, target) in tabs) {
            val tab = findViewById<TextView>(id)
            val tint = if (id == active) green else muted
            tab.setTextColor(tint)
            tab.typeface = if (id == active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tab.setCompoundDrawablesRelative(null, iconMask(icon, tint, 29), null, null)
            tab.isSelected = id == active
            tab.setOnClickListener {
                if (id == active) activeAction()
                else {
                    startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
            }
        }
    }

    protected fun setStartIcon(view: TextView, resource: Int, color: Int = muted, size: Int = 19) {
        view.compoundDrawablePadding = dp(7)
        view.setCompoundDrawablesRelative(iconMask(resource, color, size), null, null, null)
    }

    protected fun setTopIcon(view: TextView, resource: Int, size: Int = 20) {
        view.setCompoundDrawablesRelative(null, iconMask(resource, muted, size), null, null)
    }

    private fun iconMask(resource: Int, color: Int, size: Int): BitmapDrawable? {
        val source = ContextCompat.getDrawable(this, resource)?.mutate() ?: return null
        val side = dp(size)
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        source.setBounds(0, 0, side, side)
        source.draw(Canvas(bitmap))
        // The original PNGs have white backgrounds. Convert their ink to an alpha mask before coloring.
        val pixels = IntArray(side * side)
        bitmap.getPixels(pixels, 0, side, 0, 0, side, side)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val lightness = (Color.red(pixel) * 3 + Color.green(pixel) * 6 + Color.blue(pixel)) / 10
            val opacity = ((245 - lightness) * 255 / 137).coerceIn(0, 255)
            pixels[index] = (color and 0x00FFFFFF) or ((opacity * Color.alpha(pixel) / 255) shl 24)
        }
        bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
        return BitmapDrawable(resources, bitmap).apply { setBounds(0, 0, side, side) }
    }

    protected fun roundedFill(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    protected fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    protected fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    protected fun label(value: String, size: Float = 14f, color: Int = ink) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
    }

    protected fun card(parent: LinearLayout) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(10))
        minimumHeight = dp(70)
        setBackgroundResource(R.drawable.fit_next_card)
        val attributes = theme.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        try { foreground = attributes.getDrawable(0) } finally { attributes.recycle() }
        isFocusable = true
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }

    protected fun exerciseImage(part: String) = when (part) {
        "가슴" -> R.drawable.s02_chest_icon
        "하체" -> R.drawable.s02_lower_icon
        "등" -> R.drawable.s02_back_icon
        "어깨" -> R.drawable.s02_shoulder_icon
        else -> R.drawable.figlog_main_dumbell
    }

    protected fun thumbnail(part: String) = ImageView(this).apply {
        setImageResource(exerciseImage(part))
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundResource(R.drawable.fit_next_thumb)
        clipToOutline = true
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) }
    }
}
