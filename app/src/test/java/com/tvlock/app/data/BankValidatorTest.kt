package com.tvlock.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankValidatorTest {

    @Test
    fun validFlatArrayBank() {
        val text = """
            [
              {"q":"5+7等于几？","ans":"12"},
              {"q":"9+6等于几？","opts":["12","15","18"],"ans":"2"},
              {"q":"cat 的中文意思是？","opts":["狗","猫"],"ans":"猫"}
            ]
        """.trimIndent()
        val r = BankValidator.validate(text)
        assertTrue(r.ok)
        val bank = r.bank!!
        assertEquals(3, bank.total())
        assertTrue(bank.all().all { it.cat.isEmpty() })  // 纯数组格式不带分类
        assertNull(r.error)
    }

    @Test
    fun legacyCategoryBankStillValid() {
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
        assertTrue(bank.all().any { it.cat == "math" })   // 旧格式保留分类名（id 稳定，已答对记录不丢）
        assertTrue(bank.all().any { it.cat == "pinyin" })
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
    fun topLevelNotArrayOrObject() {
        val r = BankValidator.validate("42")
        assertNull(r.bank)
    }

    @Test
    fun arrayWithoutValidQuestionsFails() {
        val r = BankValidator.validate("[1,2,3]")  // 合法数组但全是坏题
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
        val sb = StringBuilder("[")
        for (i in 1..BankValidator.MAX_QUESTIONS + 1) {
            if (i > 1) sb.append(',')
            sb.append("{\"q\":\"题$i\",\"ans\":\"1\"}")
        }
        sb.append("]")
        val r = BankValidator.validate(sb.toString())
        assertNull(r.bank)
    }

    @Test
    fun ansAsTextConvertedToIndex() {
        val r = BankValidator.validate(
            """[{"q":"声调","opts":["一声","二声"],"ans":"二声"}]"""
        )
        assertTrue(r.ok)
        assertEquals("2", r.bank!!.all()[0].ans)
    }

    @Test
    fun bankJsonRoundTrip() {
        val text = """
            [{"q":"2+3等于几？","ans":"5"},{"q":"4+4等于几？","opts":["6","7","8","9"],"ans":"3"}]
        """.trimIndent()
        val b = BankValidator.validate(text).bank!!
        val json = b.toBankJson()
        assertTrue(json.trim().startsWith("["))  // 导出为纯数组格式
        val b2 = BankValidator.validate(json).bank!!
        assertEquals(b.all(), b2.all())
    }

    @Test
    fun legacyBankExportedAsFlatArray() {
        // 旧分类格式导出后统一为纯数组（分类名不导出）
        val b = BankValidator.validate("""{"math":[{"q":"1+1等于几？","ans":"2"}]}""").bank!!
        val json = b.toBankJson()
        val b2 = BankValidator.validate(json).bank!!
        assertEquals(1, b2.total())
        assertTrue(b2.all()[0].cat.isEmpty())
    }
}
