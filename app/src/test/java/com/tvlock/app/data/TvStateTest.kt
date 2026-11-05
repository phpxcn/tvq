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
            adminPin = "8888",
            panelPin = "2468"
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
}
