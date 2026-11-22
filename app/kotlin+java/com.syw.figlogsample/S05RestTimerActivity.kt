package com.syw.figlogsample

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// S05 휴식 타이머 화면
class S05RestTimerActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPauseLabel: TextView
    private lateinit var btnPause: Button
    private lateinit var circularTimer: CircularTimerView

    private var selectedTime = 60
    private var remainingTime = 60

    private var timer: CountDownTimer? = null
    private var isRunning = false
    private var isFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_s05_rest_timer)

        selectedTime = intent.getIntExtra("restSeconds", 60).coerceIn(1, 3600)
        remainingTime = selectedTime
        initViews()
        setClickListeners()

        // 기본 90초 자동 실행
        startTimer()
    }

    private fun initViews() {

        tvTimer = findViewById(R.id.tvTimer)
        tvStatus = findViewById(R.id.tvStatus)
        tvPauseLabel = findViewById(R.id.tvPauseLabel)

        btnPause = findViewById(R.id.btnPause)

        circularTimer = findViewById(R.id.circularTimer)

        updateTimerDisplay()
    }

    private fun setClickListeners() {

        // 뒤로가기
        findViewById<ImageButton>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        // 휴식시간 설정
        findViewById<Button>(R.id.btn30)
            .setOnClickListener {
                setTime(30)
            }

        findViewById<Button>(R.id.btn60)
            .setOnClickListener {
                setTime(60)
            }

        findViewById<Button>(R.id.btn90)
            .setOnClickListener {
                setTime(90)
            }

        findViewById<Button>(R.id.btn120)
            .setOnClickListener {
                setTime(120)
            }

        // 초기화
        findViewById<Button>(R.id.btnReset)
            .setOnClickListener {
                resetTimer()
            }

        // 일시정지 / 재시작
        btnPause.setOnClickListener {

            if (isRunning) {
                pauseTimer()
            } else {
                resumeTimer()
            }
        }

        // 종료
        findViewById<Button>(R.id.btnStop)
            .setOnClickListener {
                stopTimer()
            }
    }

    // 휴식시간 변경
    private fun setTime(seconds: Int) {

        selectedTime = seconds
        remainingTime = seconds

        timer?.cancel()
        isRunning = false
        isFinished = false

        updateTimerDisplay()

        // 시간 변경 후 자동 시작
        startTimer()
    }

    // 타이머 시작
    private fun startTimer() {

        timer?.cancel()

        if (remainingTime <= 0) return

        isRunning = true
        isFinished = false

        btnPause.text = "Ⅱ"
        tvPauseLabel.text = "일시정지"
        tvStatus.text = "남은 시간"

        timer = object : CountDownTimer(
            remainingTime * 1000L,
            1000L
        ) {

            override fun onTick(millisUntilFinished: Long) {

                remainingTime =
                    ((millisUntilFinished + 999) / 1000).toInt()

                updateTimerDisplay()
            }

            override fun onFinish() {

                remainingTime = 0
                isRunning = false
                isFinished = true

                updateTimerDisplay()

                tvStatus.text = "휴식 종료"
                tvPauseLabel.text = "완료"

                Toast.makeText(
                    this@S05RestTimerActivity,
                    "휴식시간이 종료되었습니다!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    // 일시정지
    private fun pauseTimer() {

        timer?.cancel()

        isRunning = false

        btnPause.text = "▶"
        tvPauseLabel.text = "재시작"

        tvStatus.text = "일시정지"

    }

    // 재시작
    private fun resumeTimer() {

        if (remainingTime <= 0) {
            resetTimer()
            return
        }

        startTimer()
    }

    // 초기화
    private fun resetTimer() {

        timer?.cancel()

        remainingTime = selectedTime
        isRunning = false
        isFinished = false

        updateTimerDisplay()

        // 초기화 후 자동 시작
        startTimer()
    }

    // 타이머 종료
    private fun stopTimer() {

        timer?.cancel()

        isRunning = false
        isFinished = false

        remainingTime = 0

        updateTimerDisplay()

        tvStatus.text = "타이머 종료"
        tvPauseLabel.text = "종료"

        // 이전 화면(S04)으로 이동
        finish()
    }

    // 화면 갱신
    private fun updateTimerDisplay() {

        val minutes = remainingTime / 60
        val seconds = remainingTime % 60

        tvTimer.text = String.format(
            "%02d:%02d",
            minutes,
            seconds
        )

        circularTimer.progress =
            if (selectedTime > 0) {
                remainingTime.toFloat() / selectedTime
            } else {
                0f
            }
    }

    override fun onDestroy() {
        super.onDestroy()

        timer?.cancel()
        timer = null
    }
}
