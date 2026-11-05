package com.tvlock.app

import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.app.Activity
import com.tvlock.app.data.TvState

/**
 * 全屏答题锁屏。
 *
 * 遥控器操作：
 *  - 选择题：方向键移动焦点，OK 确认；数字键 1~9 直接选第 N 个选项
 *  - 填空题：数字键直接输入，屏幕键盘支持（退格/确定）
 *  - 返回键/菜单键一律吞掉
 * 防逃：
 *  - 沉浸全屏 + 屏幕固定 startLockTask（HOME 键失效）
 *  - 即使固定被取消，AppCore 看门狗 500ms 内拉回本页
 * 家长兜底：上上下下左右左右 -> 输入 4 位管理码 -> 解锁
 */
class LockActivity : Activity() {

    companion object {
        @Volatile
        var isFront: Boolean = false
            private set

        private val KONAMI = intArrayOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
        )
    }

    private val ui = Handler(Looper.getMainLooper())

    private lateinit var tvQuestion: TextView
    private lateinit var tvAttempts: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var containerOptions: GridLayout
    private lateinit var containerFill: android.widget.LinearLayout
    private lateinit var tvTyped: TextView
    private lateinit var gridNumpad: GridLayout
    private lateinit var overlayCorrect: View
    private lateinit var overlayPin: View
    private lateinit var tvPinDots: TextView

    private var optionButtons: List<Button> = emptyList()

    private val stateListener: (TvState) -> Unit = { st ->
        ui.post { onStateChanged(st) }
    }

    // 管理码输入缓冲
    private var pinBuf = ""
    private var pinMode = false
    private var konamiIdx = 0
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppCore.ensureStarted(applicationContext)
        bindViews()
        AppCore.addListener(stateListener)
        render(AppCore.state)
    }

    private fun bindViews() {
        tvQuestion = findViewById(R.id.tvQuestion)
        tvAttempts = findViewById(R.id.tvAttempts)
        tvFeedback = findViewById(R.id.tvFeedback)
        containerOptions = findViewById(R.id.containerOptions)
        containerFill = findViewById(R.id.containerFill)
        tvTyped = findViewById(R.id.tvTyped)
        gridNumpad = findViewById(R.id.gridNumpad)
        overlayCorrect = findViewById(R.id.overlayCorrect)
        overlayPin = findViewById(R.id.overlayPin)
        tvPinDots = findViewById(R.id.tvPinDots)
    }

    override fun onResume() {
        super.onResume()
        isFront = true
        hideSystemUi()
        // 屏幕固定：非设备所有者会弹一次系统确认框（家长按一次 OK 即固定）
        if (AppCore.state.locked) {
            try {
                startLockTask()
            } catch (_: SecurityException) {
            } catch (_: Throwable) {
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isFront = false
    }

    override fun onDestroy() {
        super.onDestroy()
        AppCore.removeListener(stateListener)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        val decor = window.decorView
        decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    // ---------- 按键 ----------

    override fun onBackPressed() {
        // 锁定期间返回键无效
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 管理码输入模式
        if (pinMode) return handlePinKey(keyCode)

        when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH -> return true

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                trackKonami(keyCode)
                return super.onKeyDown(keyCode, event)
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                trackKonamiReset()
                return super.onKeyDown(keyCode, event)
            }
        }

        // 数字键：选择题直接选；填空题输入
        val digit = digitOf(keyCode)
        if (digit >= 0) {
            val st = AppCore.state
            val q = st.current
            if (q != null && q.isChoice) {
                if (digit in 1..(q.opts?.size ?: 0)) {
                    submit(digit.toString())
                }
            } else if (q != null) {
                val typed = st.typed + digit.toString()
                if (typed.length <= 8) AppCore.setTyped(typed)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun digitOf(keyCode: Int): Int {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) return keyCode - KeyEvent.KEYCODE_0
        return -1
    }

    private fun trackKonami(keyCode: Int) {
        if (konamiIdx < KONAMI.size && KONAMI[konamiIdx] == keyCode) {
            konamiIdx++
            if (konamiIdx == KONAMI.size) {
                konamiIdx = 0
                enterPinMode()
            }
        } else {
            konamiIdx = if (KONAMI[0] == keyCode) 1 else 0
        }
    }

    private fun trackKonamiReset() {
        konamiIdx = 0
    }

    // ---------- 管理码（家长兜底） ----------

    private fun enterPinMode() {
        pinMode = true
        pinBuf = ""
        tvPinDots.text = "____"
        overlayPin.visibility = View.VISIBLE
    }

    private fun exitPinMode() {
        pinMode = false
        pinBuf = ""
        overlayPin.visibility = View.GONE
    }

    private fun handlePinKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (pinBuf.isEmpty()) exitPinMode()
                pinBuf = ""
                tvPinDots.text = "____"
                return true
            }
        }
        val digit = digitOf(keyCode)
        if (digit >= 0) {
            pinBuf += digit.toString()
            tvPinDots.text = pinBuf.padEnd(4, '_')
            if (pinBuf.length == 4) {
                ui.postDelayed({
                    if (pinBuf.length == 4) {
                        if (AppCore.checkAdminPin(pinBuf)) {
                            exitPinMode()
                            AppCore.unlock(true)
                        } else {
                            pinBuf = ""
                            tvPinDots.text = "____"
                        }
                    }
                }, 150)
            }
            return true
        }
        return true
    }

    // ---------- 状态渲染 ----------

    private fun onStateChanged(st: TvState) {
        if (finished) return
        if (!st.locked) {
            finished = true
            try {
                stopLockTask()
            } catch (_: Throwable) {
            }
            finish()
            return
        }
        render(st)
    }

    private var lastQuestionId: String? = null

    private fun render(st: TvState) {
        if (!st.locked) return
        val q = st.current ?: return
        tvQuestion.text = q.q
        tvAttempts.text = if (st.wrongAttempts > 0) "已答错 ${st.wrongAttempts} 次，再想一想" else "答对就能继续看电视"
        tvFeedback.visibility = View.GONE

        val qid = q.id
        if (qid != lastQuestionId) {
            lastQuestionId = qid
            buildForQuestion(q)
        }
        if (!q.isChoice) {
            tvTyped.text = if (st.typed.isEmpty()) " " else st.typed
        }
    }

    private fun buildForQuestion(q: com.tvlock.app.data.Question) {
        containerOptions.removeAllViews()
        gridNumpad.removeAllViews()
        optionButtons = emptyList()
        if (q.isChoice) {
            containerOptions.visibility = View.VISIBLE
            containerFill.visibility = View.GONE
            val opts = q.opts!!
            containerOptions.columnCount = if (opts.size <= 4) 2 else 1
            val buttons = ArrayList<Button>()
            opts.forEachIndexed { idx, text ->
                val btn = makeOptionButton(idx + 1, text)
                containerOptions.addView(
                    btn,
                    GridLayout.LayoutParams().apply {
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    }
                )
                buttons.add(btn)
            }
            optionButtons = buttons
            buttons.firstOrNull()?.requestFocus()
        } else {
            containerOptions.visibility = View.GONE
            containerFill.visibility = View.VISIBLE
            buildNumpad()
        }
    }

    private fun makeOptionButton(index: Int, text: String): Button {
        val btn = Button(this)
        btn.text = "$index．$text"
        btn.textSize = 30f
        btn.setTextColor(resources.getColorStateList(R.color.option_text))
        btn.background = resolveDrawable(R.drawable.bg_option)
        btn.setPadding(32, 32, 32, 32)
        btn.isAllCaps = false
        btn.setOnClickListener { submit(index.toString()) }
        return btn
    }

    private fun buildNumpad() {
        val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "删除", "0", "确定")
        gridNumpad.columnCount = 3
        labels.forEach { label ->
            val btn = Button(this)
            btn.text = label
            btn.textSize = 28f
            btn.setTextColor(resources.getColorStateList(R.color.option_text))
            btn.background = resolveDrawable(R.drawable.bg_option)
            btn.setPadding(24, 24, 24, 24)
            btn.isAllCaps = false
            btn.minWidth = 200
            btn.setOnClickListener {
                when (label) {
                    "删除" -> AppCore.setTyped(AppCore.state.typed.dropLast(1))
                    "确定" -> submit(AppCore.state.typed)
                    else -> {
                        val typed = AppCore.state.typed + label
                        if (typed.length <= 8) AppCore.setTyped(typed)
                    }
                }
            }
            gridNumpad.addView(
                btn,
                GridLayout.LayoutParams().apply {
                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            )
        }
    }

    // ---------- 答题 ----------

    private fun submit(input: String) {
        val correct = AppCore.submitAnswer(input)
        if (correct) {
            showCorrect()
        } else {
            showWrong()
        }
    }

    private fun showCorrect() {
        overlayCorrect.visibility = View.VISIBLE
        try {
            stopLockTask()
        } catch (_: Throwable) {
        }
        finished = true
        ui.postDelayed({ finish() }, 1600)
    }

    private fun showWrong() {
        tvFeedback.text = "再想一想，你可以的！"
        tvFeedback.setTextColor(resolveColor(R.color.err))
        tvFeedback.visibility = View.VISIBLE
        tvAttempts.text = "已答错 ${AppCore.state.wrongAttempts} 次，继续加油"
        // 题目抖动提示
        val shake = ObjectAnimator.ofFloat(tvQuestion, "translationX", 0f, -24f, 24f, -16f, 16f, 0f)
        shake.duration = 400
        shake.start()
    }

    private fun resolveColor(id: Int): Int {
        @Suppress("DEPRECATION")
        return resources.getColor(id)
    }

    private fun resolveDrawable(id: Int): android.graphics.drawable.Drawable? {
        @Suppress("DEPRECATION")
        return resources.getDrawable(id)
    }
}
