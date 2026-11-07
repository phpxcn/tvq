package com.tvlock.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * 锁定遮罩：答题锁屏被切走的瞬间（onPause）立即盖住整个屏幕。
 *
 * HOME 键是系统键，Activity 层无法拦截；屏幕固定（startLockTask）依赖家长
 * 在系统确认框按过「确定」。本遮罩是确定性的视觉防线：孩子按 HOME /
 * 切应用的一瞬间，屏幕被「答题锁定中」黑幕盖住，看不到桌面内容，
 * 看门狗把锁屏拉回后（onResume）自动收起。
 *
 * 遮罩不抢焦点（遥控按键穿透到下层，但看不见就无法有效操作），
 * 视口铺满全屏、拦截触摸。需要 SYSTEM_ALERT_WINDOW 悬浮窗权限。
 */
object GuardOverlay {

    private var view: View? = null

    fun show(context: Context) {
        if (view != null) return
        AppCore.log("锁定遮罩准备显示…")
        try {
            val app = context.applicationContext
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val tv = TextView(app).apply {
                text = "答题锁定中…\n请回答屏幕上的题目"
                textSize = 34f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(0xEE0A0A12.toInt())
            }
            val type = if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(tv, lp)
            view = tv
            AppCore.log("锁定遮罩已显示（type=$type）")
        } catch (t: Throwable) {
            // 无悬浮窗权限时静默降级：仅剩看门狗拉回防线
            AppCore.log("锁定遮罩不可用：$t")
        }
    }

    fun hide() {
        val v = view ?: return
        AppCore.log("锁定遮罩收起")
        try {
            (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Throwable) {
        }
        view = null
    }
}
