package com.tvlock.app.data

import com.tvlock.app.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StateManagerTest {

    private fun newManager(): Pair<StateManager, File> {
        val dir = Files.createTempDirectory("tvlock-state").toFile()
        return StateManager(dir) to dir
    }

    @Test
    fun defaultStateWhenNoFile() {
        val (sm, dir) = newManager()
        assertFalse(sm.state.locked)
        assertTrue(sm.state.armed)
        assertFalse(File(dir, "state.json").exists())
    }

    @Test
    fun saveLoadRoundTrip() {
        val dir = Files.createTempDirectory("tvlock-state").toFile()
        val sm = StateManager(dir)
        val q = Question("math", "8+9等于几？", null, "17")
        sm.mutate {
            it.copy(
                locked = true, current = q, wrongAttempts = 2, typed = "1",
                intervalMin = 20, nextLockAt = 1730000000000, lastSeenAt = 1729999990000
            )
        }
        // 模拟断电重启：新实例从同一磁盘目录加载
        val sm2 = StateManager(dir)
        val st = sm2.state
        assertTrue(st.locked)
        assertEquals(2, st.wrongAttempts)
        assertEquals("1", st.typed)
        assertEquals("17", st.current!!.ans)  // 同一道题
        assertEquals(20L, st.intervalMin)
    }

    @Test
    fun atomicWriteLeavesNoTmp() {
        val (sm, dir) = newManager()
        repeat(20) { i ->
            sm.mutate { it.copy(wrongAttempts = i) }
        }
        assertTrue(File(dir, "state.json").exists())
        assertFalse(File(dir, "state.json.tmp").exists())
        // JSON 始终合法
        val root = MiniJson.parse(File(dir, "state.json").readText())
        assertTrue(root is Map<*, *>)
    }

    @Test
    fun corruptFileFallsBackToDefault() {
        val dir = Files.createTempDirectory("tvlock-state").toFile()
        File(dir, "state.json").writeText("{ corrupted !!!")
        val sm = StateManager(dir)
        assertFalse(sm.state.locked) // 默认状态而不是崩溃
    }

    @Test
    fun mutateAppliesTransform() {
        val (sm, _) = newManager()
        sm.mutate { it.copy(intervalMin = 5) }
        assertEquals(5L, sm.state.intervalMin)
        sm.mutate { it.copy(typed = it.typed + "7") }
        assertEquals("7", sm.state.typed)
        assertNotNull(sm.state)
    }
}
