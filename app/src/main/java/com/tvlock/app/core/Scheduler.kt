package com.tvlock.app.core

import com.tvlock.app.data.TvState

/**
 * 锁机调度判定（纯函数，JVM 单元测试直接覆盖）。
 */
object Scheduler {

    /** 时钟回拨容忍度：超过 60 秒视为时钟异常（电视无 RTC 电池 / 人为改时间） */
    const val CLOCK_JUMP_TOLERANCE_MS = 60_000L

    enum class Action { WAIT, LOCK_NOW }

    data class Decision(val action: Action, val reason: String)

    /**
     * 核心防逃判定：
     *  1. 已锁定 -> 维持
     *  2. 时钟回拨超容忍度 -> 立即锁机（改时间作弊 / 时钟重置都逃不掉）
     *  3. armed 且到点 -> 立即锁机（断电期间时间照算）
     */
    fun evaluate(st: TvState, now: Long): Decision {
        if (st.locked) return Decision(Action.WAIT, "already-locked")
        if (st.lastSeenAt > 0 && now + CLOCK_JUMP_TOLERANCE_MS < st.lastSeenAt) {
            return Decision(Action.LOCK_NOW, "clock-rollback")
        }
        if (st.armed && st.nextLockAt in 1..now) {
            return Decision(Action.LOCK_NOW, "due")
        }
        return Decision(Action.WAIT, "wait")
    }

    /** 距下次锁机还有多久（未武装或已锁定返回 null） */
    fun remainingMs(st: TvState, now: Long): Long? {
        if (!st.armed || st.locked) return null
        if (st.nextLockAt <= 0) return null
        val r = st.nextLockAt - now
        return if (r < 0) 0L else r
    }
}
