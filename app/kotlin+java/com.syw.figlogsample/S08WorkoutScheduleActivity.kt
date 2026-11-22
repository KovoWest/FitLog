package com.syw.figlogsample

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class S08WorkoutScheduleActivity : FitLogScreen() {
    private data class Plan(val id: Long, val routineId: Long?, val name: String?, val part: String?, val date: Long, val done: Boolean)

    private val db by lazy { FitLogDbHelper(this) }
    private var chosen = LocalDate.now()
    private var month = YearMonth.from(chosen)
    private val monthFormat = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen(R.layout.activity_s08_workout_schedule, R.id.fitNavSchedule)
        savedInstanceState?.getString("chosen")?.let { chosen = LocalDate.parse(it) }
        month = YearMonth.from(chosen)
        findViewById<TextView>(R.id.schedulePrev).setOnClickListener { changeMonth(-1) }
        findViewById<TextView>(R.id.scheduleNext).setOnClickListener { changeMonth(1) }
        findViewById<TextView>(R.id.scheduleAdd).setOnClickListener { editPlan(null) }
        findViewById<TextView>(R.id.scheduleComplete).setOnClickListener {
            changePlans {
                db.writableDatabase.update("Schedule", ContentValues().apply { put("completionStatus", 1) },
                    "scheduleDate >= ? AND scheduleDate < ? AND completionStatus = 0",
                    arrayOf(chosen.startMillis().toString(), chosen.plusDays(1).startMillis().toString()))
            }
        }
    }

    override fun onResume() { super.onResume(); render() }

    private fun changeMonth(offset: Long) {
        month = month.plusMonths(offset)
        chosen = month.atDay(1)
        render()
    }

    private fun render() {
        val first = month.atDay(1)
        val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
        val gridEnd = gridStart.plusDays(((first.dayOfWeek.value % 7 + month.lengthOfMonth() + 6) / 7 * 7).toLong())
        val plans = db.readableDatabase.rawQuery("""
            SELECT s.scheduleId, s.routineId, r.routineName, r.bodyPart, s.scheduleDate, s.completionStatus
            FROM Schedule s LEFT JOIN Routine r ON r.routineId = s.routineId
            WHERE s.scheduleDate >= ? AND s.scheduleDate < ?
            ORDER BY s.scheduleDate, s.scheduleId
        """.trimIndent(), arrayOf(gridStart.startMillis().toString(), gridEnd.startMillis().toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Plan(cursor.getLong(0), cursor.getLong(1).takeUnless { cursor.isNull(1) },
                    cursor.getString(2), cursor.getString(3), cursor.getLong(4), cursor.getInt(5) != 0))
            }
        }.groupBy { it.date.localTime().toLocalDate() }
        findViewById<TextView>(R.id.scheduleMonth).text = month.format(monthFormat)
        renderCalendar(plans.keys)
        findViewById<TextView>(R.id.scheduleDay).text = chosen.format(fitDate)
        val dayPlans = plans[chosen].orEmpty()
        val rows = findViewById<LinearLayout>(R.id.scheduleRows)
        rows.removeAllViews()
        if (dayPlans.isEmpty()) rows.addView(label("등록된 운동 일정이 없습니다.", color = muted))
        for (plan in dayPlans) card(rows).apply {
            addView(thumbnail(plan.part.orEmpty()))
            addView(LinearLayout(this@S08WorkoutScheduleActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(plan.name ?: "연결된 루틴 없음", 17f).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) })
                plan.part?.let { addView(label(it, 13f, muted)) }
                addView(label(plan.date.localTime().format(fitTime), 13f, muted).apply {
                    setPadding(0, dp(4), 0, 0)
                    setStartIcon(this, R.drawable.figlog_main_timeicon, size = 15)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(if (plan.done) "완료" else "진행 전", 12f, if (plan.done) green else ink).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = roundedFill(if (plan.done) 0xFFE4F3EA.toInt() else 0xFFF0F2F4.toInt(), 20)
            })
            addView(label(" ›", 23f, muted))
            setOnClickListener { planActions(plan) }
        }
        val done = dayPlans.count { it.done }
        findViewById<TextView>(R.id.scheduleComplete).apply {
            isEnabled = done < dayPlans.size
            alpha = if (isEnabled) 1f else 0.46f
        }
        findViewById<TextView>(R.id.scheduleProgressText).text = "$done / ${dayPlans.size}"
        findViewById<ProgressBar>(R.id.scheduleProgress).apply {
            max = dayPlans.size.coerceAtLeast(1)
            progress = done
        }
    }

    private fun renderCalendar(marked: Set<LocalDate>) {
        val calendar = findViewById<LinearLayout>(R.id.scheduleCalendar)
        calendar.removeAllViews()
        val weekday = LinearLayout(this)
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { index, day ->
            val color = when (index) { 0 -> 0xFFAD302C.toInt(); 6 -> 0xFF245ABA.toInt(); else -> ink }
            weekday.addView(label(day, color = color).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(28), 1f))
        }
        calendar.addView(weekday)
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value % 7
        val weeks = (offset + month.lengthOfMonth() + 6) / 7
        for (week in 0 until weeks) {
            val row = LinearLayout(this)
            for (day in 0..6) {
                val date = first.plusDays((week * 7 + day - offset).toLong())
                val selected = date == chosen
                val inMonth = YearMonth.from(date) == month
                val cell = android.widget.FrameLayout(this).apply {
                    setBackgroundColor(0xFFFBFCFD.toInt())
                    contentDescription = date.format(fitDate) + if (date in marked) ", 일정 있음" else ""
                    isSelected = selected
                    isFocusable = true
                }
                val dayLabel = label(date.dayOfMonth.toString(), 14f,
                    if (selected) android.graphics.Color.WHITE else if (inMonth) ink else muted).apply {
                    gravity = Gravity.CENTER
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    if (selected) background = roundedFill(green, 40)
                    if (date in marked) setPadding(0, 0, 0, dp(5))
                }
                cell.addView(dayLabel, android.widget.FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
                if (date in marked) {
                    cell.addView(android.view.View(this).apply {
                        background = roundedFill(if (selected) android.graphics.Color.WHITE else green, 4)
                    }, android.widget.FrameLayout.LayoutParams(dp(4), dp(4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                        bottomMargin = dp(6)
                    })
                }
                cell.setOnClickListener { chosen = date; month = YearMonth.from(date); render() }
                row.addView(cell, LinearLayout.LayoutParams(0, dp(37), 1f).apply {
                    setMargins(0, 0, dp(1), dp(1))
                })
            }
            calendar.addView(row)
        }
    }

    private fun planActions(plan: Plan) {
        AlertDialog.Builder(this).setTitle(plan.name ?: "운동 일정")
            .setItems(arrayOf(if (plan.done) "진행 전으로 변경" else "완료로 표시", "일정 수정", "일정 삭제")) { _, action ->
                when (action) {
                    0 -> changePlans {
                        db.writableDatabase.update("Schedule", ContentValues().apply { put("completionStatus", if (plan.done) 0 else 1) },
                            "scheduleId = ?", arrayOf(plan.id.toString()))
                    }
                    1 -> editPlan(plan)
                    2 -> AlertDialog.Builder(this).setTitle("운동 일정 삭제").setMessage("이 일정을 삭제할까요?")
                        .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                            changePlans { db.writableDatabase.delete("Schedule", "scheduleId = ?", arrayOf(plan.id.toString())) }
                        }.show()
                }
            }.setNegativeButton("닫기", null).show()
    }

    private fun editPlan(plan: Plan?) {
        val routines = db.listRoutines()
        if (routines.isEmpty()) {
            message("운동 루틴을 먼저 등록해 주세요.")
            return
        }
        var date = plan?.date?.localTime()?.toLocalDate() ?: chosen
        var time = plan?.date?.localTime()?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(9, 0)
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val routinePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@S08WorkoutScheduleActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("운동 루틴을 선택해 주세요.") + routines.map { it.name })
            setSelection(routines.indexOfFirst { it.id == plan?.routineId } + 1)
        }
        fields.addView(routinePicker)
        fields.addView(Button(this).apply {
            text = date.format(fitDate)
            setOnClickListener {
                DatePickerDialog(this@S08WorkoutScheduleActivity, { _, year, month, day ->
                    date = LocalDate.of(year, month + 1, day)
                    text = date.format(fitDate)
                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }
        })
        fields.addView(Button(this).apply {
            text = time.format(fitTime)
            setOnClickListener {
                TimePickerDialog(this@S08WorkoutScheduleActivity, { _, hour, minute ->
                    time = LocalTime.of(hour, minute)
                    text = time.format(fitTime)
                }, time.hour, time.minute, true).show()
            }
        })
        val dialog = AlertDialog.Builder(this).setTitle(if (plan == null) "일정 등록" else "일정 수정")
            .setView(fields).setNegativeButton("취소", null).setPositiveButton("저장", null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val routine = routines.getOrNull(routinePicker.selectedItemPosition - 1)
            if (routine == null) {
                message("운동 루틴을 선택해 주세요.")
                return@setOnClickListener
            }
            changePlans {
                val values = ContentValues().apply {
                    put("routineId", routine.id)
                    put("scheduleDate", date.atTime(time).atZone(fitZone).toInstant().toEpochMilli())
                }
                if (plan == null) db.writableDatabase.insertOrThrow("Schedule", null, values)
                else db.writableDatabase.update("Schedule", values, "scheduleId = ?", arrayOf(plan.id.toString()))
                chosen = date
                month = YearMonth.from(date)
                dialog.dismiss()
            }
        }
    }

    private fun changePlans(action: () -> Unit) {
        try { action(); render() }
        catch (e: android.database.sqlite.SQLiteException) { message("운동 일정 변경을 저장하지 못했습니다.") }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("chosen", chosen.toString())
    }

    override fun onDestroy() { db.close(); super.onDestroy() }
}
