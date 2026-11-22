package com.syw.figlogsample

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** S04: activity_s04.xml의 기존 화면을 유지하며 실제 SQLite 저장/조회 수행 */
class WorkoutRecordActivity : AppCompatActivity() {
    private lateinit var dbHelper: FitLogDbHelper
    private lateinit var setContainer: LinearLayout
    private lateinit var previousContainer: LinearLayout
    private var routineId: Long = -1L
    private var exerciseId: Long = -1L
    private var exerciseName = ""
    private data class SetInputs(val row: LinearLayout, val weight: EditText, val reps: EditText)
    private val inputs = mutableListOf<SetInputs>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_s04)
        dbHelper = FitLogDbHelper(this)
        setContainer = findViewById(R.id.layoutSetContainer)
        previousContainer = findViewById(R.id.layoutPreviousSets)
        routineId = intent.getLongExtra("routineId", intent.getLongExtra("ROUTINE_ID", -1L))
        exerciseName = intent.getStringExtra("exerciseName") ?: ""
        attachExistingRows()
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAddSet).setOnClickListener { addRow() }
        findViewById<View>(R.id.btnChangeExercise).setOnClickListener { chooseExercise() }
        findViewById<View>(R.id.btnSave).setOnClickListener { saveWorkout() }
        findViewById<View>(R.id.navHome).setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        findViewById<View>(R.id.navRoutine).setOnClickListener { startActivity(Intent(this, S02RoutineListActivity::class.java)) }
        findViewById<View>(R.id.navRecord).setOnClickListener { toast("기록 목록은 S06에서 구현합니다.") }
        findViewById<View>(R.id.navSchedule).setOnClickListener { toast("운동 일정은 S08에서 구현합니다.") }
        if (exerciseName.isBlank()) {
            exerciseName = dbHelper.readableDatabase.rawQuery("SELECT exerciseName FROM Exercise ORDER BY exerciseId LIMIT 1", null)
                .use { if (it.moveToFirst()) it.getString(0) else "" }
        }
        updateExercise()
    }

    private fun attachExistingRows() {
        listOf(Triple(R.id.etWeight1, R.id.etReps1, R.id.btnDeleteSet1),
               Triple(R.id.etWeight2, R.id.etReps2, R.id.btnDeleteSet2),
               Triple(R.id.etWeight3, R.id.etReps3, R.id.btnDeleteSet3)).forEach { (w, r, del) ->
            val weight = findViewById<EditText>(w)
            val reps = findViewById<EditText>(r)
            val row = weight.parent as LinearLayout
            // XML의 예시 수치는 실제 저장 값으로 오해되지 않도록 초기화
            weight.setText(""); reps.setText("")
            val input = SetInputs(row, weight, reps)
            inputs.add(input)
            findViewById<View>(del).setOnClickListener { removeRow(input) }
        }
        renumber()
    }

    private fun addRow() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(58)
        }
        val number = TextView(this).apply { gravity = Gravity.CENTER; setTextColor(Color.DKGRAY) }
        row.addView(number, LinearLayout.LayoutParams(dp(70), dp(48)))
        val weight = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "kg"; textSize = 14f; setSingleLine(true) }
        val reps = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; hint = "회"; textSize = 14f; setSingleLine(true) }
        row.addView(weight, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(reps, LinearLayout.LayoutParams(0, dp(48), 1f))
        val delete = ImageView(this).apply {
            setImageResource(R.drawable.s03_delete_icon)
            contentDescription = "운동 세트 삭제"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        row.addView(
            delete,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )
        val input = SetInputs(row, weight, reps)
        delete.setOnClickListener { removeRow(input) }
        setContainer.addView(row)
        inputs.add(input)
        renumber()
    }

    private fun removeRow(input: SetInputs) {
        if (inputs.size == 1) { toast("최소 1세트가 필요합니다."); return }
        input.row.visibility = View.GONE // 기존 XML 자리/모양 보존
        inputs.remove(input)
        renumber()
    }

    private fun renumber() {
        inputs.forEachIndexed { index, entry ->
            (entry.row.getChildAt(0) as? TextView)?.text = "${index + 1}세트"
        }
    }

    private fun updateExercise() {
        val db = dbHelper.readableDatabase
        val name = exerciseName
        db.rawQuery("SELECT exerciseId FROM Exercise WHERE exerciseName=? LIMIT 1", arrayOf(name)).use {
            exerciseId = if (it.moveToFirst()) it.getLong(0) else -1L
        }
        findViewById<TextView>(R.id.tvExerciseName).text = if (name.isBlank()) "운동 종목 없음" else name
        showPrevious()
    }

    private fun chooseExercise() {
        val options = mutableListOf<Pair<Long, String>>()
        dbHelper.readableDatabase.rawQuery("SELECT exerciseId, exerciseName FROM Exercise ORDER BY exerciseName", null).use { cur ->
            while (cur.moveToNext()) options.add(cur.getLong(0) to cur.getString(1))
        }
        if (options.isEmpty()) { toast("먼저 S03에서 루틴을 등록하세요."); return }
        AlertDialog.Builder(this).setTitle("운동 종목 변경")
            .setItems(options.map { it.second }.toTypedArray()) { _, which ->
                exerciseId = options[which].first
                exerciseName = options[which].second
                updateExercise()
            }.show()
    }

    private fun showPrevious() {
        previousContainer.removeAllViews()
        if (exerciseId < 0) { findViewById<TextView>(R.id.tvPreviousDate).text = "기록 없음"; return }
        val db = dbHelper.readableDatabase
        val idDate = db.rawQuery(
            "SELECT recordId, workoutDate FROM WorkoutRecord WHERE exerciseId=? ORDER BY workoutDate DESC, recordId DESC LIMIT 1",
            arrayOf(exerciseId.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }
        if (idDate == null) { findViewById<TextView>(R.id.tvPreviousDate).text = "기록 없음"; addPreviousLabel("이전 운동 기록이 없습니다."); return }
        findViewById<TextView>(R.id.tvPreviousDate).text = SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN).format(Date(idDate.second))
        db.rawQuery("SELECT setNumber,weight,reps FROM SetRecord WHERE recordId=? ORDER BY setNumber", arrayOf(idDate.first.toString())).use { cur ->
            while (cur.moveToNext()) {
                addPreviousLabel("${cur.getInt(0)}세트     ${cur.getDouble(1)}kg     ${cur.getInt(2)}회")
            }
        }
    }

    private fun addPreviousLabel(value: String) {
        previousContainer.addView(TextView(this).apply {
            text = value; textSize = 14f; setTextColor(Color.DKGRAY)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        })
    }

    private fun saveWorkout() {
        if (exerciseId < 0) { toast("운동 종목을 먼저 선택하세요."); return }
        val sets = mutableListOf<Pair<Double, Int>>()
        inputs.forEach { entry ->
            val w = entry.weight.text.toString().trim().toDoubleOrNull()
            val r = entry.reps.text.toString().trim().toIntOrNull()
            if (w == null || !w.isFinite() || w < 0) { entry.weight.error = "무게는 0 이상의 숫자로 입력하세요."; entry.weight.requestFocus(); return }
            if (r == null || r < 1) { entry.reps.error = "횟수는 1 이상의 정수로 입력하세요."; entry.reps.requestFocus(); return }
            sets.add(w to r)
        }
        if (sets.isEmpty()) { toast("저장할 세트가 없습니다."); return }
        val db = dbHelper.writableDatabase
        try {
            db.beginTransaction()
            val recordId = db.insertOrThrow("WorkoutRecord", null, ContentValues().apply {
                put("exerciseId", exerciseId)
                if (routineId >= 0) put("routineId", routineId) else putNull("routineId")
                put("workoutDate", System.currentTimeMillis())
            })
            sets.forEachIndexed { idx, (weight, reps) ->
                db.insertOrThrow("SetRecord", null, ContentValues().apply {
                    put("recordId", recordId); put("setNumber", idx + 1)
                    put("weight", weight); put("reps", reps)
                })
            }
            db.setTransactionSuccessful()
            toast("${sets.size}세트가 저장되었습니다.")
            showPrevious()
            startActivity(Intent(this, S05RestTimerActivity::class.java).putExtra("restSeconds", 60))
        } catch (e: Exception) {
            toast("저장 실패: ${e.localizedMessage}")
        } finally { if (db.inTransaction()) db.endTransaction() }
    }

    private fun dp(px: Int) = (px * resources.displayMetrics.density + .5f).toInt()
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    override fun onDestroy() { dbHelper.close(); super.onDestroy() }
}
