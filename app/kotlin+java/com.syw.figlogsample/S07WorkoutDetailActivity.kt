package com.syw.figlogsample

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.widget.HorizontalScrollView
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

class S07WorkoutDetailActivity : FitLogScreen() {
    private val db by lazy { FitLogDatabaseHelper(this) }
    private var previousId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen(R.layout.activity_s07_workout_detail, R.id.fitNavHistory) { onBackPressedDispatcher.onBackPressed() }
        setTopIcon(findViewById(R.id.detailEdit), android.R.drawable.ic_menu_edit)
        setTopIcon(findViewById(R.id.detailDelete), android.R.drawable.ic_menu_delete)
        setStartIcon(findViewById(R.id.detailDate), R.drawable.fitlog_schedule, size = 20)
        findViewById<TextView>(R.id.detailLegend).text = SpannableString("무게 (kg)   ● 이전 기록     ● 이번 기록").apply {
            val previous = indexOf('●')
            setSpan(ForegroundColorSpan(0xFF57996C.toInt()), previous, previous + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val current = lastIndexOf('●')
            setSpan(ForegroundColorSpan(green), current, current + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        previousId = savedInstanceState?.getLong("previousId")?.takeIf { it > 0 }
    }

    override fun onResume() {
        super.onResume()
        val id = intent.getLongExtra("recordId", -1L)
        val detail = db.getWorkoutDetail(id)
        findViewById<View>(R.id.detailMissing).visibility = if (detail == null) View.VISIBLE else View.GONE
        for (viewId in listOf(R.id.detailContent, R.id.detailEdit, R.id.detailDelete)) {
            findViewById<View>(viewId).visibility = if (detail == null) View.GONE else View.VISIBLE
        }
        if (detail == null) return
        findViewById<TextView>(R.id.detailName).text = detail.exerciseName
        findViewById<TextView>(R.id.detailSubtitle).text = detail.bodyPart
        findViewById<TextView>(R.id.detailDate).text = detail.workoutDate.localTime().format(fitDate)
        findViewById<ImageView>(R.id.detailImage).setImageResource(exerciseImage(detail.bodyPart))
        findViewById<View>(R.id.detailEdit).setOnClickListener {
            startActivity(Intent(this, WorkoutRecordActivity::class.java).putExtra("editRecordId", id))
        }
        findViewById<View>(R.id.detailDelete).setOnClickListener {
            AlertDialog.Builder(this).setTitle("운동 기록 삭제")
                .setMessage("선택한 운동 기록과 세트를 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제") { _, _ ->
                    try {
                        db.deleteWorkout(id)
                        finish()
                    } catch (e: android.database.sqlite.SQLiteException) {
                        message("운동 기록을 삭제하지 못했습니다.")
                    }
                }.show()
        }
        val sets = findViewById<LinearLayout>(R.id.detailSets)
        sets.removeAllViews()
        tableRow(sets, listOf("세트", "무게 (kg)", "횟수 (회)"), true)
        for (set in detail.sets) tableRow(sets, listOf("${set.setNumber}세트", set.weight.displayWeight(), set.reps.toString()))
        val earlier = db.getPreviousRecords(detail.exerciseId, detail.workoutDate, detail.recordId)
        val picker = findViewById<TextView>(R.id.detailPrevious)
        picker.isEnabled = earlier.isNotEmpty()
        val selected = earlier.firstOrNull { it.recordId == previousId } ?: earlier.firstOrNull()
        renderComparison(detail, selected)
        picker.setOnClickListener {
            val dates = earlier.map { it.workoutDate.localTime().format(fitDate) + " " + it.workoutDate.localTime().format(fitTime) }
            AlertDialog.Builder(this).setTitle("이전 기록 선택")
                .setItems(dates.toTypedArray()) { _, index -> renderComparison(detail, earlier[index]) }
                .setNegativeButton("취소", null).show()
        }
    }

    private fun renderComparison(current: WorkoutDetail, previous: WorkoutDetail?) {
        previousId = previous?.recordId
        findViewById<TextView>(R.id.detailPrevious).text = previous?.workoutDate?.localTime()?.format(fitDate)?.plus("  ⌄")
            ?: "이전 기록 없음"
        val table = findViewById<LinearLayout>(R.id.detailComparison)
        val graph = findViewById<LinearLayout>(R.id.detailGraph)
        table.removeAllViews()
        graph.removeAllViews()
        (graph.parent as View).visibility = if (previous == null) View.GONE else View.VISIBLE
        if (previous == null) {
            table.addView(label("비교할 이전 운동 기록이 없습니다.", color = muted))
            return
        }
        val before = previous.sets.associateBy { it.setNumber }
        val after = current.sets.associateBy { it.setNumber }
        val numbers = (before.keys + after.keys).sorted()
        val chart = WorkoutComparisonChart(this, numbers.map { number ->
            WorkoutComparisonChart.Sets(number, before[number]?.weight, after[number]?.weight)
        })
        graph.addView(HorizontalScrollView(this).apply {
            isFillViewport = true
            addView(chart, android.widget.FrameLayout.LayoutParams(dp(42 + numbers.size * 62), dp(152)))
        })
        val isSameDay = current.workoutDate.localTime().toLocalDate() == previous.workoutDate.localTime().toLocalDate()
        val date = java.time.format.DateTimeFormatter.ofPattern(if (isSameDay) "MM.dd HH:mm" else "MM.dd")
        tableRow(table, listOf("세트", "이전 기록\n${previous.workoutDate.localTime().format(date)}",
            "이번 기록\n${current.workoutDate.localTime().format(date)}", "변화"), true)
        for (number in numbers) {
            val old = before[number]
            val now = after[number]
            val change = if (old == null || now == null) "—" else formatChange(old, now)
            tableRow(table, listOf("${number}세트", summary(old), summary(now), change))
        }
    }

    private fun formatChange(old: WorkoutSet, now: WorkoutSet): CharSequence {
        val weight = java.math.BigDecimal.valueOf(now.weight).subtract(java.math.BigDecimal.valueOf(old.weight))
        val reps = now.reps.toLong() - old.reps
        val first = "${if (weight.signum() > 0) "+" else ""}${weight.stripTrailingZeros().toPlainString()}kg"
        val second = "${if (reps > 0) "+" else ""}${reps}회"
        return SpannableString("$first\n$second").apply {
            setSpan(ForegroundColorSpan(changeColor(weight.signum())), 0, first.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(changeColor(reps.compareTo(0L))), first.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun changeColor(sign: Int) = when {
        sign > 0 -> 0xFFB62732.toInt()
        sign < 0 -> 0xFF245DA8.toInt()
        else -> muted
    }

    private fun summary(set: WorkoutSet?) = set?.let { "${it.weight.displayWeight()}kg × ${it.reps}회" } ?: "—"

    private fun tableRow(table: LinearLayout, cells: List<CharSequence>, header: Boolean = false) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (header) Color.rgb(222, 240, 231) else Color.WHITE)
            dividerDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFE9EDF0.toInt())
                setSize(dp(1), 0)
            }
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        }
        for (cell in cells) row.addView(label("", 12f).apply {
            text = cell
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            if (header) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        table.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(1) })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        previousId?.let { outState.putLong("previousId", it) }
    }

    override fun onDestroy() { db.close(); super.onDestroy() }
}
