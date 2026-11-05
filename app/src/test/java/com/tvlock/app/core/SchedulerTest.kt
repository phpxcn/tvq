package com.tvlock.app.core

import com.tvlock.app.data.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {

    private val now = 1_000_000_000_000L

    @Test
    fun dueLocksImmediately() {
        val st = TvState(armed = true, locked = false, nextLockAt = now - 1, lastSeenAt = now)
        assertEquals(Scheduler.Action.LOCK_NOW, Scheduler.evaluate(st, now).action)
    }

    @Test
    fun notYetDueWaits() {
        val st = TvState(armed = true, locked = false, nextLockAt = now + 60_000, lastSeenAt = now)
        assertEquals(Scheduler.Action.WAIT, Scheduler.evaluate(st, now).action)
    }

    @Test
    fun disarmedWaits() {
        val st = TvState(armed = false, locked = false, nextLockAt = now - 1, lastSeenAt = now)
        assertEquals(Scheduler.Action.WAIT, Scheduler.evaluate(st, now).action)
    }

    @Test
    fun lockedStays() {
        val st = TvState(armed = true, locked = true, nextLockAt = now - 1, lastSeenAt = now)
        val d = Scheduler.evaluate(st, now)
        assertEquals(Scheduler.Action.WAIT, d.action)
        assertEquals("already-locked", d.reason)
    }

    @Test
    fun clockRollbackLocks() {
        // 电视断电后时钟回退（无 RTC 电池），或人为改时间
        val st = TvState(armed = true, locked = false, nextLockAt = now + 600_000, lastSeenAt = now)
        val d = Scheduler.evaluate(st, now - 120_000)
        assertEquals(Scheduler.Action.LOCK_NOW, d.action)
        assertEquals("clock-rollback", d.reason)
    }

    @Test
    fun smallClockJitterTolerated() {
        // 60 秒内的回拨视为正常误差
        val st = TvState(armed = true, locked = false, nextLockAt = now + 600_000, lastSeenAt = now)
        assertEquals(Scheduler.Action.WAIT, Scheduler.evaluate(st, now - 30_000).action)
    }

    @Test
    fun noLastSeenDoesNotTriggerRollback() {
        val st = TvState(armed = false, locked = false, nextLockAt = 0, lastSeenAt = 0)
        assertEquals(Scheduler.Action.WAIT, Scheduler.evaluate(st, 1L).action)
    }

    @Test
    fun remainingMs() {
        val st = TvState(armed = true, locked = false, nextLockAt = now + 90_000, lastSeenAt = now)
        assertEquals(90_000L, Scheduler.remainingMs(st, now))
        assertEquals(0L, Scheduler.remainingMs(st, now + 100_000))
        assertNull(Scheduler.remainingMs(st.copy(armed = false), now))
        assertNull(Scheduler.remainingMs(st.copy(locked = true), now))
        assertNull(Scheduler.remainingMs(st.copy(nextLockAt = 0), now))
        assertTrue(true)
    }
}
