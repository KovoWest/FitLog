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
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast


/**
 * S02: 루틴 목록/필터/추가/수정/삭제/운동 시작.
 * activity_s02.xml 및 FitLogDbHelper.kt는 변경하지 않습니다.
 */
class S02RoutineListActivity : Activity() {

    private lateinit var db: FitLogDbHelper
    private lateinit var container: LinearLayout
    private lateinit var emptyView: View
    private var selectedPart = "전체"
    private val green = Color.rgb(0, 155, 114)
    private val muted = Color.rgb(102, 112, 133)

    private data class StaticCard(
        val seedId: Long,
        val cardId: Int,
        val startId: Int,
        val editId: Int,
        val deleteId: Int
    )

    private val staticCards = listOf(
        StaticCard(1L, R.id.cardChestRoutine, R.id.btnStartChest, R.id.btnEditChest, R.id.btnDeleteChest),
        StaticCard(2L, R.id.cardLegsRoutine, R.id.btnStartLegs, R.id.btnEditLegs, R.id.btnDeleteLegs),
        StaticCard(3L, R.id.cardBackRoutine, R.id.btnStartBack, R.id.btnEditBack, R.id.btnDeleteBack),
        StaticCard(4L, R.id.cardShoulderRoutine, R.id.btnStartShoulder, R.id.btnEditShoulder, R.id.btnDeleteShoulder)
    )

    private val filters = linkedMapOf(
        "전체" to R.id.filterAll,
        "가슴" to R.id.filterChest,
        "등" to R.id.filterBack,
        "하체" to R.id.filterLegs,
        "어깨" to R.id.filterShoulders,
        "팔" to R.id.filterArms
    )

    private val dynamicCards = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_s02)

        db = FitLogDbHelper(this)
        container = findViewById(R.id.routineListContainer)
        emptyView = findViewById(R.id.layoutEmptyRoutine)
        selectedPart = savedInstanceState?.getString("selectedPart") ?: "전체"

        findViewById<View>(R.id.btnAddRoutine).setOnClickListener { editRoutine(null) }
        findViewById<View>(R.id.btnEmptyAddRoutine).setOnClickListener { editRoutine(null) }

        filters.forEach { (part, id) ->
            findViewById<View>(id).setOnClickListener {
                selectedPart = part
                render()
            }
        }

        findViewById<View>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<View>(R.id.navRoutine).setOnClickListener {
            selectedPart = "전체"
            render()
        }
        findViewById<View>(R.id.navRecord).setOnClickListener {
            navigateOptional("S06WorkoutHistoryActivity")
        }
        findViewById<View>(R.id.navSchedule).setOnClickListener {
            navigateOptional("S08WorkoutScheduleActivity")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selectedPart", selectedPart)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }

    private fun render() {
        updateFilters()
        // 기존 XML 카드와 빈 상태 UI를 제거하거나 재생성하지 않습니다.
        dynamicCards.forEach { container.removeView(it) }
        dynamicCards.clear()

        val routines = try {
            db.listRoutines()
        } catch (e: Exception) {
            Toast.makeText(this, "루틴 조회 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            emptyView.visibility = View.VISIBLE
            staticCards.forEach { findViewById<View>(it.cardId).visibility = View.GONE }
            return
        }

        val visible = routines.filter { selectedPart == "전체" || it.bodyPart == selectedPart }
        val byId = routines.associateBy { it.id }

        staticCards.forEach { card ->
            val routine = byId[card.seedId]
            val cardView = findViewById<View>(card.cardId)
            val shown = routine != null && visible.any { it.id == routine.id }
            cardView.visibility = if (shown) View.VISIBLE else View.GONE
            if (routine != null) {
                updateStaticCard(cardView, routine)
                bindActions(card.startId, card.editId, card.deleteId, routine)
            }
        }

        // 초기 네 개 카드 외에 사용자가 S03에서 등록한 루틴을 추가로 표시합니다.
        val staticIds = staticCards.map { it.seedId }.toSet()
        visible.filterNot { it.id in staticIds }.forEach { routine ->
            val card = makeDynamicCard(routine)
            container.addView(card, container.indexOfChild(emptyView))
            dynamicCards.add(card)
        }

        emptyView.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateFilters() {
        filters.forEach { (part, id) ->
            val view = findViewById<TextView>(id)
            val active = selectedPart == part
            view.setTextColor(if (active) Color.WHITE else muted)
            view.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            // 기존 텍스트/위치/크기를 유지하고 선택 상태만 표시합니다.
            view.background = if (active) rounded(green, 10f) else null
        }
    }

    private fun updateStaticCard(cardView: View, routine: FitRoutine) {
        // XML 카드 구조: 이미지 / 운동 정보 LinearLayout / 버튼 LinearLayout
        val row = cardView as? LinearLayout ?: return
        val info = row.getChildAt(1) as? LinearLayout ?: return
        (info.getChildAt(0) as? TextView)?.text = routine.name
        (info.getChildAt(1) as? TextView)?.text = "운동 ${routine.exercises.size}개"
        (info.getChildAt(2) as? TextView)?.text = routine.exercises.mapIndexed { index, name ->
            "${index + 1}. $name"
        }.joinToString("\n")
    }

    private fun bindActions(startId: Int, editId: Int, deleteId: Int, routine: FitRoutine) {
        findViewById<View>(startId).setOnClickListener { startRoutine(routine) }
        findViewById<View>(editId).setOnClickListener { editRoutine(routine.id) }
        findViewById<View>(deleteId).setOnClickListener { confirmDelete(routine) }
    }

    private fun makeDynamicCard(routine: FitRoutine): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.WHITE, 8f)
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val picture = ImageView(this).apply {
            setImageResource(when (routine.bodyPart) {
                "가슴" -> R.drawable.img_chest
                "등" -> R.drawable.img_back
                "하체" -> R.drawable.img_legs
                else -> R.drawable.img_shoulders
            })
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "${routine.bodyPart} 운동 이미지"
        }
        card.addView(picture, LinearLayout.LayoutParams(dp(64), dp(80)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, dp(4), 0)
        }
        info.addView(label(routine.name, 16f, Color.BLACK, true))
        info.addView(label("운동 ${routine.exercises.size}개", 12f, muted, false))
        info.addView(label(routine.exercises.mapIndexed { index, exercise ->
            "${index + 1}. $exercise"
        }.joinToString("\n"), 12f, muted, false))
        card.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(ImageView(this).apply {
            setImageResource(R.drawable.s02_start_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "운동 시작"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener { startRoutine(routine) }
        })
        actions.addView(ImageView(this).apply {
            setImageResource(R.drawable.s02_change_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "루틴 수정"
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(38))
            setOnClickListener { editRoutine(routine.id) }
        })
        actions.addView(ImageView(this).apply {
            setImageResource(R.drawable.s02_delete)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "루틴 삭제"
            layoutParams = LinearLayout.LayoutParams(dp(27), dp(36))
            setOnClickListener { confirmDelete(routine) }
        })
        card.addView(actions)
        return card
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        }

    private fun action(text: String, description: String, color: Int, click: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(color)
            gravity = Gravity.CENTER
            contentDescription = description
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(38))
            setOnClickListener { click() }
        }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun editRoutine(id: Long?) {
        val intent = Intent(this, S03RoutineEditActivity::class.java)
        if (id != null) {
            // S03이 사용하는 키가 다를 수 있으므로 S03 코드에서 확인해야 합니다.
            intent.putExtra("routineId", id)
            intent.putExtra("ROUTINE_ID", id)
        }
        startActivity(intent)
    }

    private fun startRoutine(routine: FitRoutine) {
        if (routine.exercises.isEmpty()) {
            Toast.makeText(this, "이 루틴에는 운동 종목이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        // 현재 프로젝트의 WorkoutRecordActivity를 S04 운동 기록 화면으로 연결.
        val intent = Intent(this, WorkoutRecordActivity::class.java).apply {
            putExtra("routineId", routine.id)
            putExtra("ROUTINE_ID", routine.id)
            putExtra("routineName", routine.name)
            putExtra("exerciseName", routine.exercises.first())
        }
        startActivity(intent)
    }

    private fun confirmDelete(routine: FitRoutine) {
        AlertDialog.Builder(this)
            .setTitle("운동 루틴 삭제")
            .setMessage("'${routine.name}' 루틴을 삭제하시겠습니까?\n기존 운동 기록은 유지됩니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                try {
                    db.deleteRoutine(routine.id)
                    Toast.makeText(this, "루틴을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    render()
                } catch (e: Exception) {
                    Toast.makeText(this, "삭제 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
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
