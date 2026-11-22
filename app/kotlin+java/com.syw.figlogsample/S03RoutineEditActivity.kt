package com.syw.figlogsample

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * S03: 운동 루틴 생성 및 수정 화면
 *
 * 주요 기능
 * 1. 운동 루틴 이름 입력
 * 2. 운동 종목 선택
 * 3. 운동 종목 추가 및 삭제
 * 4. 운동 순서 변경
 * 5. SQLite 데이터베이스 저장
 * 6. 기존 운동 루틴 수정
 * 7. 하단 네비게이션 화면 이동
 *
 * S02와 동일한 FitLogDbHelper를 사용합니다.
 */
class S03RoutineEditActivity : Activity() {

    private lateinit var helper: FitLogDbHelper

    private lateinit var nameInput: EditText
    private lateinit var selectedLabel: TextView
    private lateinit var countLabel: TextView
    private lateinit var listContainer: LinearLayout

    private data class Exercise(
        val name: String,
        val part: String
    )

    private val selected = mutableListOf<Exercise>()
    private val catalog = mutableListOf<Exercise>()

    private var pending: Exercise? = null
    private var routineId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_s03)

        // XML과 Kotlin 연결
        nameInput = findViewById(R.id.etRoutineName)
        selectedLabel = findViewById(R.id.tvSelectedExercise)
        countLabel = findViewById(R.id.tvExerciseCount)
        listContainer = findViewById(R.id.layoutExerciseList)

        // 데이터베이스 연결
        helper = FitLogDbHelper(this)

        try {

            // 운동 종목 목록 불러오기
            loadCatalog()

            // 기존 루틴 수정 여부 확인
            routineId = intent.getLongExtra("routineId", -1L)

            if (routineId >= 0) {

                val routine = helper.listRoutines()
                    .firstOrNull { it.id == routineId }

                if (routine == null) {
                    toast("수정할 루틴을 찾을 수 없습니다.")
                    finish()
                    return
                }

                nameInput.setText(routine.name)

                selected.addAll(
                    routine.exercises.map { name ->
                        catalog.firstOrNull {
                            it.name == name
                        } ?: Exercise(
                            name,
                            routine.bodyPart
                        )
                    }
                )

                findViewById<TextView>(
                    R.id.tvScreenTitle
                ).text = "운동 루틴 수정"

            } else {

                // 새로운 루틴 생성
                nameInput.setText("")
            }

            // 화면 회전 또는 재생성 시 데이터 복원
            if (savedInstanceState != null) {

                nameInput.setText(
                    savedInstanceState.getString(
                        "routine_name",
                        ""
                    )
                )

                selected.clear()

                savedInstanceState
                    .getStringArrayList("exercises")
                    ?.forEach { name ->

                        selected.add(
                            catalog.firstOrNull {
                                it.name == name
                            } ?: Exercise(name, "기타")
                        )
                    }
            }

            renderRows()

        } catch (e: Exception) {

            toast(
                "루틴 데이터를 불러오지 못했습니다: ${e.message}"
            )

            finish()
            return
        }

        // 운동 선택 버튼
        findViewById<View>(
            R.id.layoutExerciseSelector
        ).setOnClickListener {
            showPicker()
        }

        // 운동 추가 버튼
        findViewById<View>(
            R.id.btnAddExercise
        ).setOnClickListener {
            addExercise()
        }

        // 루틴 저장 버튼
        findViewById<View>(
            R.id.btnSaveRoutine
        ).setOnClickListener {
            saveRoutine()
        }

        // 취소 버튼
        findViewById<View>(
            R.id.btnCancel
        ).setOnClickListener {
            finish()
        }

        // 뒤로가기 버튼
        findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        // 홈 화면 이동
        findViewById<View>(
            R.id.navHome
        ).setOnClickListener {
            goTo(MainActivity::class.java)
        }

        // 운동 루틴 목록 이동
        findViewById<View>(
            R.id.navRoutine
        ).setOnClickListener {
            goTo(S02RoutineListActivity::class.java)
        }

        // 운동 기록 화면 이동
        findViewById<View>(
            R.id.navRecord
        ).setOnClickListener {
            navigateOptional("S06WorkoutHistoryActivity")
        }

        // 운동 일정 화면 이동
        findViewById<View>(
            R.id.navSchedule
        ).setOnClickListener {
            navigateOptional("S08WorkoutScheduleActivity")
        }
    }

    /**
     * 데이터베이스에 저장된 운동 종목 불러오기
     */
    private fun loadCatalog() {

        catalog.clear()

        helper.readableDatabase.rawQuery(
            """
            SELECT exerciseName, bodyPart
            FROM Exercise
            ORDER BY exerciseId
            """.trimIndent(),
            null
        ).use { cursor ->

            while (cursor.moveToNext()) {

                catalog.add(
                    Exercise(
                        cursor.getString(0),
                        cursor.getString(1)
                    )
                )
            }
        }
    }

    /**
     * 운동 종목 선택 다이얼로그
     */
    private fun showPicker() {

        if (catalog.isEmpty()) {
            toast("선택할 수 있는 운동이 없습니다.")
            return
        }

        val items = catalog.map {
            "${it.name} (${it.part})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("운동 종목 선택")
            .setItems(items) { _, index ->

                pending = catalog[index]

                selectedLabel.text =
                    catalog[index].name
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 선택한 운동 추가
     */
    private fun addExercise() {

        val exercise = pending

        if (exercise == null) {
            showPicker()
            return
        }

        // 중복 운동 방지
        if (selected.any {
                it.name == exercise.name
            }) {

            toast("이미 추가된 운동입니다.")
            return
        }

        selected.add(exercise)

        pending = null

        selectedLabel.text =
            "운동 종목을 선택하세요"

        renderRows()
    }

    /**
     * 운동 목록 화면에 표시
     */
    private fun renderRows() {

        // 기존 XML 예시 운동 목록 제거
        listContainer.removeAllViews()

        countLabel.text =
            "선택된 운동 종목 (${selected.size}개)"

        if (selected.isEmpty()) {

            listContainer.addView(
                TextView(this).apply {

                    text = "운동 종목을 추가해 주세요."
                    textSize = 15f

                    setTextColor(Color.DKGRAY)

                    setPadding(
                        dp(12),
                        dp(22),
                        dp(12),
                        dp(22)
                    )
                }
            )

            return
        }

        selected.forEachIndexed { index, exercise ->

            val row = LinearLayout(this).apply {

                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                setPadding(
                    dp(12),
                    dp(10),
                    dp(12),
                    dp(10)
                )

                background = rounded(Color.WHITE)
            }

            // 운동 번호
            val number = TextView(this).apply {

                text = (index + 1).toString()
                textSize = 18f

                setTextColor(
                    Color.rgb(8, 125, 87)
                )

                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

            row.addView(
                number,
                LinearLayout.LayoutParams(
                    dp(38),
                    dp(50)
                )
            )

            // 운동 이름 및 부위 표시
            val labels = LinearLayout(this).apply {

                orientation = LinearLayout.VERTICAL

                setPadding(
                    dp(10),
                    0,
                    0,
                    0
                )
            }

            labels.addView(
                TextView(this).apply {

                    text = exercise.name
                    textSize = 16f

                    setTextColor(
                        Color.rgb(17, 24, 39)
                    )

                    typeface = Typeface.DEFAULT_BOLD
                }
            )

            labels.addView(
                TextView(this).apply {

                    text = exercise.part
                    textSize = 13f

                    setTextColor(Color.GRAY)
                }
            )

            row.addView(
                labels,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            // 운동 순서 변경
            val move = TextView(this).apply {

                text = "↕"

                contentDescription = "운동 순서 변경"

                textSize = 25f
                gravity = Gravity.CENTER

                setTextColor(Color.GRAY)

                setOnClickListener {
                    showReorder(index)
                }
            }

            row.addView(
                move,
                LinearLayout.LayoutParams(
                    dp(42),
                    dp(52)
                )
            )

            // 운동 삭제
            val remove = ImageView(this).apply {

                setImageResource(R.drawable.s03_delete_icon)

                contentDescription = "운동 삭제"

                scaleType = ImageView.ScaleType.FIT_CENTER

                setPadding(dp(8), dp(8), dp(8), dp(8))

                setOnClickListener {

                    selected.removeAt(index)

                    renderRows()
                }
            }

            row.addView(
                remove,
                LinearLayout.LayoutParams(
                    dp(38),
                    dp(52)
                )
            )

            // 길게 눌러 순서 변경
            row.setOnLongClickListener {

                showReorder(index)

                true
            }

            listContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {

                    if (index > 0) {
                        topMargin = dp(10)
                    }
                }
            )
        }
    }

    /**
     * 운동 순서 변경
     */
    private fun showReorder(index: Int) {

        val actions = arrayOf(
            "위로 이동",
            "아래로 이동"
        )

        AlertDialog.Builder(this)
            .setTitle("운동 순서 변경")
            .setItems(actions) { _, which ->

                val target = if (which == 0) {
                    index - 1
                } else {
                    index + 1
                }

                if (target in selected.indices) {

                    val item = selected.removeAt(index)

                    selected.add(target, item)

                    renderRows()

                } else {

                    toast("더 이상 이동할 수 없습니다.")
                }
            }
            .show()
    }

    /**
     * 운동 루틴 데이터베이스 저장
     */
    private fun saveRoutine() {

        val name = nameInput.text
            .toString()
            .trim()

        // 루틴 이름 검사
        if (name.isBlank()) {

            nameInput.error =
                "루틴 이름을 입력해 주세요."

            nameInput.requestFocus()

            return
        }

        // 운동 종목 검사
        if (selected.isEmpty()) {

            toast(
                "운동 종목을 한 개 이상 추가해 주세요."
            )

            return
        }

        // 운동 부위 설정
        val distinctParts = selected
            .map { it.part }
            .distinct()

        val part = if (distinctParts.size == 1) {

            distinctParts.first()

        } else {

            "전신"
        }

        try {

            val id = helper.saveRoutine(
                if (routineId >= 0) {
                    routineId
                } else {
                    null
                },
                name,
                part,
                selected.map { it.name }
            )

            toast("루틴이 저장되었습니다.")

            // S02에 저장 완료 결과 전달
            setResult(
                RESULT_OK,
                Intent().putExtra(
                    "routineId",
                    id
                )
            )

            finish()

        } catch (e: Exception) {

            toast("저장 실패: ${e.message}")
        }
    }

    /**
     * 다른 화면으로 이동
     */
    private fun goTo(target: Class<out Activity>) {

        startActivity(
            Intent(this, target).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        )

        finish()
    }

    /**
     * 화면 재생성 시 데이터 유지
     */
    override fun onSaveInstanceState(outState: Bundle) {

        outState.putString(
            "routine_name",
            nameInput.text.toString()
        )

        outState.putStringArrayList(
            "exercises",
            ArrayList(
                selected.map { it.name }
            )
        )

        super.onSaveInstanceState(outState)
    }

    /**
     * 데이터베이스 연결 종료
     */
    override fun onDestroy() {

        if (::helper.isInitialized) {
            helper.close()
        }

        super.onDestroy()
    }

    /**
     * 운동 목록 배경 생성
     */
    private fun rounded(color: Int): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius = dp(12).toFloat()
        }
    }

    /**
     * dp 단위 변환
     */
    private fun dp(value: Int): Int {

        return (
                value * resources.displayMetrics.density
                ).toInt()
    }

    /**
     * 메시지 표시
     */
    private fun toast(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun navigateOptional(simpleName: String) {
        try {
            val target = Class.forName("com.syw.figlogsample.$simpleName")
                .asSubclass(Activity::class.java)
            startActivity(Intent(this, target))
        } catch (e: Exception) {
            Toast.makeText(this, "해당 화면은 다음 개발 단계에서 연결합니다.", Toast.LENGTH_SHORT).show()
        }
    }
}