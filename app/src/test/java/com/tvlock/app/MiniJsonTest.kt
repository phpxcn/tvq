package com.tvlock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun parsePrimitives() {
        assertNull(MiniJson.parse("null"))
        assertEquals(true, MiniJson.parse("true"))
        assertEquals(false, MiniJson.parse("false"))
        assertEquals(12L, MiniJson.parse("12"))
        assertEquals(-5L, MiniJson.parse("-5"))
        assertEquals(1.5, MiniJson.parse("1.5"))
        assertEquals("你好", MiniJson.parse("\"你好\""))
        assertEquals("a\"b\\c\nd", MiniJson.parse("\"a\\\"b\\\\c\\nd\""))
    }

    @Test
    fun parseNested() {
        val v = MiniJson.parse("""{"math":[{"q":"1+1等于几？","ans":"2"}],"n":3,"flag":true,"z":null}""")
        assertTrue(v is Map<*, *>)
        val m = v as Map<*, *>
        assertEquals(4, m.size)
        assertEquals(3L, m["n"])
        assertEquals(true, m["flag"])
        assertNull(m["z"])
        val list = m["math"] as List<*>
        assertEquals(1, list.size)
        val q = list[0] as Map<*, *>
        assertEquals("1+1等于几？", q["q"])
        assertEquals("2", q["ans"])
    }

    @Test
    fun parseWhitespaceAndTrailing() {
        assertEquals(1L, MiniJson.parse("  1  "))
        assertEquals(1L, MiniJson.parse("\n\t1\r\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectGarbage() {
        MiniJson.parse("{bad")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectTrailingContent() {
        MiniJson.parse("1 2")
    }

    @Test
    fun writeRoundTrip() {
        val src = """{"a":[1,2.5,"x",true,null],"b":{"c":"d"}}"""
        val v = MiniJson.parse(src)
        val out = MiniJson.write(v)
        assertEquals(v, MiniJson.parse(out))
    }

    @Test
    fun writeEscapes() {
        val m = linkedMapOf("k" to "quote\"back\\slash\n")
        val s = MiniJson.write(m)
        assertEquals(m, MiniJson.parse(s))
    }

    @Test
    fun unicodeEscape() {
        assertEquals("中", MiniJson.parse("\"\\u4e2d\""))
        val out = MiniJson.write(linkedMapOf("a" to "中"))
        assertTrue(out.contains("中"))
    }

    @Test
    fun writeNumbers() {
        assertEquals("3", MiniJson.write(3L))
        assertEquals("3", MiniJson.write(3))
        assertEquals("1.5", MiniJson.write(1.5))
        assertEquals("2", MiniJson.write(2.0))
    }
}
