package com.syw.figlogsample

import android.app.DatePickerDialog
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.doOnLayout
import java.time.LocalDate

class S06WorkoutHistoryActivity : FitLogScreen() {
    private val db by lazy { FitLogDbHelper(this) }
    private var start: LocalDate? = null
    private var end: LocalDate? = null
    private var filterStart: LocalDate? = null
    private var filterEnd: LocalDate? = null
    private var query = ""
    private var newest = true
    private var restoredScroll: Int? = null
    private val shortDate = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREAN)
    private val weekday = DateTimeFormatter.ofPattern("(E)", Locale.KOREAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen(R.layout.activity_s06_workout_history, R.id.fitNavHistory)
        start = savedInstanceState?.getString("start")?.let(LocalDate::parse)
        end = savedInstanceState?.getString("end")?.let(LocalDate::parse)
        filterStart = savedInstanceState?.getString("filterStart")?.let(LocalDate::parse)
        filterEnd = savedInstanceState?.getString("filterEnd")?.let(LocalDate::parse)
        query = savedInstanceState?.getString("appliedQuery").orEmpty()
        newest = savedInstanceState?.getBoolean("newest", true) ?: true
        val input = findViewById<EditText>(R.id.historyQuery)
        setStartIcon(input, android.R.drawable.ic_menu_search)
        setStartIcon(findViewById(R.id.historyStart), R.drawable.fitlog_schedule, size = 17)
        setStartIcon(findViewById(R.id.historyEnd), R.drawable.fitlog_schedule, size = 17)
        findViewById<TextView>(R.id.historyStart).setOnClickListener { pickDate(true) }
        findViewById<TextView>(R.id.historyEnd).setOnClickListener { pickDate(false) }
        findViewById<TextView>(R.id.historySearch).setOnClickListener { search(input.text.toString()) }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { search(input.text.toString()); true } else false
        }
        findViewById<TextView>(R.id.historyReset).setOnClickListener {
            start = null
            end = null
            input.text.clear()
            showDates()
            search("")
        }
        findViewById<TextView>(R.id.historySort).setOnClickListener { newest = !newest; render() }
        showDates()
        findViewById<ScrollView>(R.id.fitNextScroll).isSaveEnabled = false
        restoredScroll = savedInstanceState?.getInt("scroll")
    }

    override fun onResume() {
        super.onResume()
        val scroll = findViewById<ScrollView>(R.id.fitNextScroll)
        val position = restoredScroll ?: scroll.scrollY
        restoredScroll = null
        render()
        scroll.doOnLayout { scroll.scrollTo(0, position) }
    }

    private fun pickDate(isStart: Boolean) {
        val selected = (if (isStart) start else end) ?: LocalDate.now()
        DatePickerDialog(this, { _, year, month, day ->
            val value = LocalDate.of(year, month + 1, day)
            if (isStart) start = value else end = value
            showDates()
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
    }

    private fun showDates() {
        findViewById<TextView>(R.id.historyStart).text = start?.format(shortDate) ?: "시작 날짜"
        findViewById<TextView>(R.id.historyEnd).text = end?.format(shortDate) ?: "종료 날짜"
    }

    private fun search(value: String) {
        val first = start
        val last = end
        if (first != null && last != null && first.isAfter(last)) {
            message("시작 날짜는 종료 날짜보다 늦을 수 없습니다.")
            return
        }
        val input = findViewById<EditText>(R.id.historyQuery)
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        query = value.trim()
        filterStart = first
        filterEnd = last
        findViewById<ScrollView>(R.id.fitNextScroll).scrollTo(0, 0)
        render()
    }

    private fun render() {
        val list = findViewById<LinearLayout>(R.id.historyRows)
        list.removeAllViews()
        findViewById<TextView>(R.id.historySort).text = if (newest) "최신순 ⌄" else "오래된순 ⌄"
        val conditions = mutableListOf("instr(lower(e.exerciseName), lower(?)) > 0 OR ? = ''")
        val args = mutableListOf(query, query)
        filterStart?.let { conditions += "w.workoutDate >= ?"; args += it.startMillis().toString() }
        filterEnd?.let { conditions += "w.workoutDate < ?"; args += it.plusDays(1).startMillis().toString() }
        val where = conditions.joinToString(" AND ") { "($it)" }
        val order = if (newest) "DESC" else "ASC"
        val count = db.readableDatabase.rawQuery("""
            SELECT w.recordId, w.workoutDate, e.exerciseName, e.bodyPart,
                (SELECT count(*) FROM SetRecord WHERE recordId = w.recordId),
                s.weight, s.reps
            FROM WorkoutRecord w JOIN Exercise e ON e.exerciseId = w.exerciseId
            LEFT JOIN SetRecord s ON s.setRecordId = (
                SELECT setRecordId FROM SetRecord WHERE recordId = w.recordId
                ORDER BY setNumber, setRecordId LIMIT 1
            )
            WHERE $where
            ORDER BY w.workoutDate $order, w.recordId $order
        """.trimIndent(), args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val recordId = cursor.getLong(0)
                val performed = cursor.getLong(1).localTime()
                val date = performed.format(shortDate) + "\n" + performed.format(weekday)
                val name = cursor.getString(2)
                val part = cursor.getString(3)
                val summary = if (cursor.isNull(5)) "세트 기록 없음"
                    else "${cursor.getInt(4)}세트\n${cursor.getDouble(5).displayWeight()}kg × ${cursor.getInt(6)}회"
                card(list).apply {
                    addView(label(date, 12f, muted), LinearLayout.LayoutParams(dp(79), -2))
                    addView(thumbnail(part))
                    addView(LinearLayout(this@S06WorkoutHistoryActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(label(name, 17f).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) })
                        addView(label(summary, 13f, muted))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(label("›", 24f, muted))
                    setOnClickListener {
                        startActivity(Intent(this@S06WorkoutHistoryActivity, S07WorkoutDetailActivity::class.java)
                            .putExtra("recordId", recordId))
                    }
                }
            }
            cursor.count
        }
        findViewById<TextView>(R.id.historyCount).text = "검색 결과 (${count}개)"
        if (count == 0) list.addView(label("검색 결과가 없습니다.", color = muted))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("start", start?.toString())
        outState.putString("end", end?.toString())
        outState.putString("filterStart", filterStart?.toString())
        outState.putString("filterEnd", filterEnd?.toString())
        outState.putString("appliedQuery", query)
        outState.putBoolean("newest", newest)
        outState.putInt("scroll", findViewById<ScrollView>(R.id.fitNextScroll).scrollY)
    }

    override fun onDestroy() { db.close(); super.onDestroy() }
}
