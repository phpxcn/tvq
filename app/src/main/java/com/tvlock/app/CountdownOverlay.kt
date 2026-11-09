package com.tvlock.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * 右上角常驻倒计时悬浮窗。
 *
 * 双重价值：
 * 1. 进程可见性——有窗口常驻在屏 = 进程处于可见状态，ROM 后台清理
 *    （tvBootSourceKey / hippo）按「后台进程」识别时大概率豁免；
 * 2. 心理预期——孩子/家长随时看到「距离下次答题还有多久」。
 *
 * 完全不挡操作：FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCHABLE（触摸/遥控
 * 穿透），窗口不进焦点链，任何页面的正常操作不受影响。
 * app 运行期间（非锁定）常驻：布防显示距下次锁机剩余、暂停定时显示「已暂停」；
 * 锁定中（锁屏全屏答题）隐藏，避免遮挡。进程可见性保活价值贯穿始终。
 * 倒计时结束拉起答题页、答完题重新计时由 Scheduler/tick 既有逻辑负责。
 */
object CountdownOverlay {

    private var view: TextView? = null
    private var lastText: String = ""
    private var lastWarn: Boolean? = null

    /** 更新倒计时文本（布防待机时每 tick 调用，显示距下次锁机的剩余时间 m:ss） */
    fun update(context: Context, remainingMs: Long) {
        val text = format(remainingMs.coerceAtLeast(0L))
        val warn = remainingMs in 1..60_000L
        show(context, text, warn)
    }

    /** 暂停定时：框仍常驻显示「已暂停」，保持进程可见性与状态指示 */
    fun showPaused(context: Context) {
        show(context, "已暂停", warn = false)
    }

    private fun show(context: Context, text: String, warn: Boolean) {
        if (view == null) {
            create(context, text, warn)
            return
        }
        if (text == lastText && warn == lastWarn) return
        lastText = text
        lastWarn = warn
        try {
            view?.let {
                it.text = text
                it.setTextColor(if (warn) 0xFFFF5252.toInt() else Color.WHITE)
            }
        } catch (_: Throwable) {
        }
    }

    private fun create(context: Context, text: String, warn: Boolean): Boolean {
        AppCore.log("倒计时悬浮窗准备显示…")
        return try {
            val app = context.applicationContext
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val tv = TextView(app).apply {
                this.text = text
                textSize = 10f
                setTextColor(if (warn) 0xFFFF5252.toInt() else Color.WHITE)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(14, 6, 14, 6)
                background = GradientDrawable().apply {
                    setColor(0x33000000)
                    cornerRadius = 14f
                }
            }
            val type = if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                // 不抢焦点 + 触摸穿透：任何页面的操作完全不受影响
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 48
                y = 64
            }
            wm.addView(tv, lp)
            view = tv
            lastText = text
            lastWarn = warn
            AppCore.log("倒计时悬浮窗已显示（右上角）")
            true
        } catch (t: Throwable) {
            // 无悬浮窗权限时静默降级：仅剩看门狗防线
            AppCore.log("倒计时悬浮窗不可用：$t")
            false
        }
    }

    fun hide() {
        val v = view ?: return
        try {
            (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Throwable) {
        }
        view = null
        lastText = ""
        lastWarn = null
    }

    /** 毫秒 → m:ss（超过 1 小时也按分钟累计，如 95:00） */
    private fun format(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }
}
