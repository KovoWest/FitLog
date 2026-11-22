package com.syw.figlogsample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.tvTodayDate).text =
            SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN).format(Date())

        findViewById<Button>(R.id.btnStartWorkout).setOnClickListener { startFirstRoutine() }
        findViewById<Button>(R.id.btnCreateRoutine).setOnClickListener {
            startActivity(Intent(this, S03RoutineEditActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.layoutChestRoutine).setOnClickListener { openRoutines() }
        findViewById<LinearLayout>(R.id.layoutLegRoutine).setOnClickListener { openRoutines() }
        findViewById<LinearLayout>(R.id.nav_home).setOnClickListener { }
        findViewById<LinearLayout>(R.id.nav_routine).setOnClickListener { openRoutines() }
        findViewById<LinearLayout>(R.id.nav_record).setOnClickListener {
            navigateOptional("S06WorkoutHistoryActivity")
        }
        findViewById<LinearLayout>(R.id.nav_schedule).setOnClickListener {
            navigateOptional("S08WorkoutScheduleActivity")
        }
        findViewById<ImageView>(R.id.btnNotification).setOnClickListener {
            notice("알림 기능은 준비 중입니다.")
        }
        findViewById<TextView>(R.id.btnProfile).setOnClickListener {
            notice("프로필 기능은 준비 중입니다.")
        }
    }

    private fun openRoutines() = startActivity(Intent(this, S02RoutineListActivity::class.java))

    private fun startFirstRoutine() {
        val routine = try { FitLogDbHelper(this).use { it.listRoutines().firstOrNull() } }
        catch (e: Exception) { notice("운동 루틴 조회 실패: ${e.message}"); null }
        if (routine == null || routine.exercises.isEmpty()) {
            notice("먼저 운동 루틴을 등록해 주세요.")
            openRoutines()
            return
        }
        startActivity(Intent(this, WorkoutRecordActivity::class.java).apply {
            putExtra("routineId", routine.id)
            putExtra("routineName", routine.name)
            putExtra("exerciseName", routine.exercises.first())
        })
    }

    private fun navigateOptional(simpleName: String) {
        try {
            val cls = Class.forName("com.syw.figlogsample.$simpleName")
                .asSubclass(android.app.Activity::class.java)
            startActivity(Intent(this, cls))
        } catch (e: Exception) { notice("해당 화면은 다음 개발 단계에서 연결합니다.") }
    }

    private fun notice(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}