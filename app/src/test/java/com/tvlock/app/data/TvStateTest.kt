package com.tvlock.app.data

import com.tvlock.app.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvStateTest {

    @Test
    fun defaults() {
        val st = TvState()
        assertEquals(false, st.locked)
        assertEquals(true, st.armed)
        assertEquals(10L, st.intervalMin)
        assertEquals("1234", st.adminPin)
        assertEquals("", st.panelPin)
        assertEquals(false, st.firstRunDone)
        assertEquals(0, st.pendingExtra)
        assertEquals(emptyList<String>(), st.answeredIds)
        assertNull(st.current)
    }

    @Test
    fun roundTripFullState() {
        val st = TvState(
            locked = true,
            armed = false,
            intervalMin = 15,
            nextLockAt = 1730000000000,
            lastSeenAt = 1729999990000,
            wrongAttempts = 3,
            typed = "1",
            current = Question("pinyin", "「山」的声调是？", listOf("一声", "二声", "三声", "四声"), "1"),
            recentIds = listOf("math|1+1等于几？|F", "pinyin|x|a,b"),
            pendingExtra = 2,
            answeredIds = listOf("math|1+1等于几？|F", "math|2+2等于几？|F"),
            adminPin = "8888",
            panelPin = "2468",
            firstRunDone = true
        )
        val json = MiniJson.write(st.toMap())
        val back = TvState.fromMap(MiniJson.parse(json) as Map<String, Any?>)
        assertEquals(st, back)
    }

    @Test
    fun fromMapWithMissingFieldsUsesDefaults() {
        val back = TvState.fromMap(linkedMapOf<String, Any?>())
        assertEquals(TvState(), back)
    }

    @Test
    fun currentQuestionRoundTripKeepsCategory() {
        val st = TvState(locked = true, current = Question("math", "3+4等于几？", null, "7"))
        val back = TvState.fromMap(MiniJson.parse(MiniJson.write(st.toMap())) as Map<String, Any?>)
        assertEquals("math", back.current!!.cat)
        assertEquals("7", back.current!!.ans)
        assertTrue(back.current!!.checkAnswer("7"))
    }

    @Test
    fun firstRunDonePersistsAndAdminPinChangeSurvives() {
        // 首次引导完成后 + 新管理码，断电重启后两者都保留
        val st = TvState(adminPin = "5678", firstRunDone = true)
        val back = TvState.fromMap(MiniJson.parse(MiniJson.write(st.toMap())) as Map<String, Any?>)
        assertEquals(true, back.firstRunDone)
        assertEquals("5678", back.adminPin)
        // 旧版 state.json（无 firstRunDone 字段）升级读取：默认 false，引导可重弹
        val legacy = TvState.fromMap(
            MiniJson.parse("{\"adminPin\":\"8888\"}") as Map<String, Any?>
        )
        assertEquals(false, legacy.firstRunDone)
        assertEquals("8888", legacy.adminPin)
    }

    @Test
    fun legacyStateWithoutNewFieldsUpgradesCleanly() {
        // v1.3.1 之前的 state.json 没有 pendingExtra / answeredIds 字段：
        // 升级读取时默认 0 / 空，不丢任何旧状态
        val legacyJson = "{\"locked\":true,\"armed\":true,\"wrongAttempts\":2,\"recentIds\":[\"math|x|F\"]}"
        val st = TvState.fromMap(MiniJson.parse(legacyJson) as Map<String, Any?>)
        assertEquals(true, st.locked)
        assertEquals(2, st.wrongAttempts)
        assertEquals(listOf("math|x|F"), st.recentIds)
        assertEquals(0, st.pendingExtra)
        assertEquals(emptyList<String>(), st.answeredIds)
    }
}
