package com.tvlock.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankValidatorTest {

    @Test
    fun validMultiCategoryBank() {
        val text = """
            {
              "math": [
                {"q":"5+7等于几？","ans":"12"},
                {"q":"9+6等于几？","opts":["12","15","18"],"ans":"2"}
              ],
              "pinyin": [
                {"q":"「山」的声调是？","opts":["一声","二声","三声","四声"],"ans":"3"}
              ]
            }
        """.trimIndent()
        val r = BankValidator.validate(text)
        assertTrue(r.ok)
        val bank = r.bank!!
        assertEquals(3, bank.total())
        assertEquals(2, bank.cats["math"]!!.size)
        assertEquals(1, bank.cats["pinyin"]!!.size)
        assertNull(r.error)
    }

    @Test
    fun notJson() {
        val r = BankValidator.validate("这不是JSON {{{")
        assertNull(r.bank)
        assertNotNull(r.error)
    }

    @Test
    fun emptyContent() {
        val r = BankValidator.validate("   ")
        assertNull(r.bank)
    }

    @Test
    fun topLevelNotObject() {
        val r = BankValidator.validate("[1,2,3]")
        assertNull(r.bank)
    }

    @Test
    fun emptyBank() {
        val r = BankValidator.validate("{}")
        assertNull(r.bank)
    }

    @Test
    fun categoryNotList() {
        val r = BankValidator.validate("""{"math": "abc"}""")
        assertNull(r.bank)
    }

    @Test
    fun badItemsSkippedButValidOnesKept() {
        val text = """
            {
              "math": [
                {"q":"1+1等于几？","ans":"2"},
                {"q":""},
                {"ans":"5"},
                {"q":"x","opts":["a"],"ans":"1"},
                {"q":"y","opts":["a","b"],"ans":"9"},
                "garbage-item"
              ]
            }
        """.trimIndent()
        val r = BankValidator.validate(text)
        assertTrue(r.ok)
        assertEquals(1, r.bank!!.total())
        assertNotNull(r.error) // 提示跳过了坏题
    }

    @Test
    fun allItemsBadMeansFail() {
        val r = BankValidator.validate("""{"math": [{"q":""}, {"ans":"1"}]}""")
        assertNull(r.bank)
    }

    @Test
    fun oversizeRejected() {
        val sb = StringBuilder("{\"math\":[")
        for (i in 1..BankValidator.MAX_QUESTIONS + 1) {
            if (i > 1) sb.append(',')
            sb.append("{\"q\":\"题$i\",\"ans\":\"1\"}")
        }
        sb.append("]}")
        val r = BankValidator.validate(sb.toString())
        assertNull(r.bank)
    }

    @Test
    fun ansAsTextConvertedToIndex() {
        val r = BankValidator.validate(
            """{"pinyin":[{"q":"声调","opts":["一声","二声"],"ans":"二声"}]}"""
        )
        assertTrue(r.ok)
        assertEquals("2", r.bank!!.cats["pinyin"]!![0].ans)
    }

    @Test
    fun bankJsonRoundTrip() {
        val text = """
            {"math":[{"q":"2+3等于几？","ans":"5"},{"q":"4+4等于几？","opts":["6","7","8","9"],"ans":"3"}]}
        """.trimIndent()
        val b = BankValidator.validate(text).bank!!
        val json = b.toBankJson()
        val b2 = BankValidator.validate(json).bank!!
        assertEquals(b.all(), b2.all())
    }
}
