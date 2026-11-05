package com.tvlock.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionTest {

    private fun m(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        for ((k, v) in pairs) map[k] = v
        return map
    }

    // ---------- 解析与 ans 归一化 ----------

    @Test
    fun fillQuestion() {
        val q = Question.fromMap(m("q" to "5+7等于几？", "ans" to "12"), "math")
        assertEquals("5+7等于几？", q!!.q)
        assertNull(q.opts)
        assertEquals("12", q.ans)
        assertFalse(q.isChoice)
    }

    @Test
    fun choiceQuestionWithIndexAns() {
        // 需求原始示例：opts + ans 为序号
        val q = Question.fromMap(m("q" to "9+6等于几？", "opts" to listOf("12", "15", "18"), "ans" to "2"), "math")
        assertEquals("2", q!!.ans)
        assertTrue(q.isChoice)
        assertEquals(3, q.opts!!.size)
    }

    @Test
    fun choiceQuestionWithTextAns() {
        // 兼容：ans 直接写选项文本
        val q = Question.fromMap(m("q" to "「山」的声调是？", "opts" to listOf("一声", "二声", "三声", "四声"), "ans" to "三声"), "pinyin")
        assertEquals("3", q!!.ans)
    }

    @Test
    fun choiceAnsOutOfBoundsIsInvalid() {
        assertNull(Question.fromMap(m("q" to "x", "opts" to listOf("a", "b"), "ans" to "3"), "math"))
    }

    @Test
    fun choiceAnsNotMatchingAnythingIsInvalid() {
        assertNull(Question.fromMap(m("q" to "x", "opts" to listOf("a", "b"), "ans" to "zz"), "math"))
    }

    @Test
    fun missingQuestionIsInvalid() {
        assertNull(Question.fromMap(m("ans" to "1"), "math"))
        assertNull(Question.fromMap(m("q" to "", "ans" to "1"), "math"))
    }

    @Test
    fun missingAnsIsInvalid() {
        assertNull(Question.fromMap(m("q" to "1+1等于几？"), "math"))
    }

    @Test
    fun singleOptIsInvalid() {
        assertNull(Question.fromMap(m("q" to "x", "opts" to listOf("a"), "ans" to "1"), "math"))
    }

    // ---------- 判定 ----------

    @Test
    fun fillCheckNumericCompare() {
        val q = Question("math", "5+7等于几？", null, "12")
        assertTrue(q.checkAnswer("12"))
        assertTrue(q.checkAnswer(" 12 "))
        assertTrue(q.checkAnswer("012")) // 数值相等
        assertFalse(q.checkAnswer("13"))
        assertFalse(q.checkAnswer(""))
    }

    @Test
    fun fillCheckTextCompare() {
        val q = Question("pinyin", "写拼音", null, "shān")
        assertTrue(q.checkAnswer("shān"))
        assertFalse(q.checkAnswer("shan"))
    }

    @Test
    fun choiceCheckByIndex() {
        val q = Question("pinyin", "声调", listOf("一声", "二声", "三声", "四声"), "3")
        assertTrue(q.checkAnswer("3"))
        assertFalse(q.checkAnswer("2"))
        assertFalse(q.checkAnswer("abc"))
    }

    // ---------- id ----------

    @Test
    fun idDistinguishesFillAndChoice() {
        val fill = Question("math", "5+7等于几？", null, "12")
        val choice = Question("math", "5+7等于几？", listOf("10", "12", "14"), "2")
        assertNotEquals(fill.id, choice.id)
        assertEquals(fill.id, fill.copy(cat = "math").id)
    }

    @Test
    fun roundTripToMap() {
        val q = Question("pinyin", "「山」的韵母是？", listOf("a", "e", "an", "ang"), "3")
        val m = q.toMap()
        assertEquals("「山」的韵母是？", m["q"])
        assertEquals(listOf("a", "e", "an", "ang"), m["opts"])
        assertEquals("3", m["ans"])
        val back = Question.fromMap(m, "pinyin")
        assertEquals(q, back)
    }
}
