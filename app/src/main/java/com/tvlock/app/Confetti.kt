package com.tvlock.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * 答对礼花特效：42 片彩色纸屑从题目上方迸发，重力下落 900ms 渐隐。
 *
 * 替代旧版全屏「答对了」庆祝页——特效直接叠在答题页上，不打断节奏，
 * 播完即返回播放页。纯 Canvas + ValueAnimator 实现，无任何新依赖。
 */
object Confetti {

    /** 在指定容层（锁屏根布局）顶部全屏叠加一发礼花 */
    fun burst(host: ViewGroup) {
        try {
            val v = ConfettiView(host.context)
            host.addView(
                v,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            v.start()
        } catch (_: Throwable) {
            // 特效纯锦上添花，任何异常静默忽略
        }
    }
}

private class ConfettiView(context: Context) : View(context) {

    private class Piece(
        val x0: Float, val y0: Float,
        val vx: Float, val vy: Float,
        val color: Int, val size: Float,
        val rot0: Float, val vr: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pieces = ArrayList<Piece>()
    private var t = 0f
    private var endAt = 0L

    companion object {
        private const val DURATION = 900f // 总时长 ms（与答对返回延迟一致）
        private val COLORS = intArrayOf(
            0xFFFF5252.toInt(), 0xFFFFD740.toInt(), 0xFF69F0AE.toInt(),
            0xFF40C4FF.toInt(), 0xFFB388FF.toInt(), 0xFFFF80AB.toInt()
        )
    }

    fun start() {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val rnd = Random()
        val ox = w / 2f
        val oy = h * 0.26f // 题目上方迸发
        repeat(42) {
            val ang = rnd.nextFloat() * (Math.PI * 2f).toFloat()
            // 速度域 0.2~1.0 屏宽每秒，向上偏置（迸发感）
            val speed = (0.2f + rnd.nextFloat() * 0.8f) * w
            val vx = cos(ang) * speed
            val vy = sin(ang) * speed - w * 0.55f
            pieces.add(
                Piece(
                    x0 = ox + (rnd.nextFloat() - 0.5f) * 40f,
                    y0 = oy,
                    vx = vx, vy = vy,
                    color = COLORS[rnd.nextInt(COLORS.size)],
                    size = 8f + rnd.nextFloat() * 14f,
                    rot0 = rnd.nextFloat() * 360f,
                    vr = (rnd.nextFloat() - 0.5f) * 720f
                )
            )
        }
        // t 归一化 0..1 驱动重绘，离线积分算位置（不依赖帧率）
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        endAt = System.currentTimeMillis() + DURATION.toLong() + 80
    }

    override fun onDraw(canvas: Canvas) {
        val g = height * 1.6f // 重力（屏高每秒²）
        val ts = t * (DURATION / 1000f)
        for (p in pieces) {
            val x = p.x0 + p.vx * ts
            val y = p.y0 + p.vy * ts + 0.5f * g * ts * ts
            if (y > height + 40) continue
            val alpha = (255 * (1f - t * t)).toInt().coerceIn(0, 255)
            paint.color = p.color
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(p.rot0 + p.vr * t, x, y)
            canvas.drawRect(x - p.size / 2, y - p.size / 4, x + p.size / 2, y + p.size / 4, paint)
            canvas.restore()
        }
        // 自清理：动画结束或宿主要收场时把自己摘掉
        if (System.currentTimeMillis() > endAt) {
            (parent as? ViewGroup)?.removeView(this)
        }
    }
}
