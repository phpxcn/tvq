package com.tvlock.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsCodecTest {

    /** 手工构造一个查询 tvlock.local A 记录的 mDNS 报文 */
    private fun buildQuery(id: Int, name: String, type: Int): ByteArray {
        val out = ArrayList<Byte>()
        fun u16(v: Int) { out.add(((v shr 8) and 0xFF).toByte()); out.add((v and 0xFF).toByte()) }
        u16(id)
        u16(0x0000) // 查询
        u16(1)      // qd
        u16(0); u16(0); u16(0)
        for (label in name.split('.')) {
            out.add(label.length.toByte())
            for (c in label.toByteArray(Charsets.US_ASCII)) out.add(c)
        }
        out.add(0)
        u16(type)
        u16(0x8001)
        return out.toByteArray()
    }

    @Test
    fun parseQueryRoundTrip() {
        val pkt = buildQuery(0xABCD, "tvlock.local", DnsCodec.TYPE_A)
        val msg = DnsCodec.parseQuery(pkt, pkt.size)
        assertNotNull(msg)
        assertEquals(0xABCD, msg!!.id)
        assertEquals(1, msg.questions.size)
        val q = msg.questions[0]
        assertEquals("tvlock.local", q.name)
        assertEquals(DnsCodec.TYPE_A, q.type)
        assertTrue(DnsCodec.matchesName(q, "tvlock.local"))
    }

    @Test
    fun caseInsensitiveMatch() {
        val pkt = buildQuery(1, "TVLOCK.LOCAL", DnsCodec.TYPE_A)
        val msg = DnsCodec.parseQuery(pkt, pkt.size)
        assertTrue(DnsCodec.matchesName(msg!!.questions[0], "tvlock.local"))
    }

    @Test
    fun anyTypeMatches() {
        val q = DnsCodec.DnsQuestion("tvlock.local", DnsCodec.TYPE_ANY, 0x8001)
        assertTrue(DnsCodec.matchesName(q, "tvlock.local"))
    }

    @Test
    fun otherNameDoesNotMatch() {
        val pkt = buildQuery(2, "otherhost.local", DnsCodec.TYPE_A)
        val msg = DnsCodec.parseQuery(pkt, pkt.size)
        assertTrue(!DnsCodec.matchesName(msg!!.questions[0], "tvlock.local"))
    }

    @Test
    fun responsePacketIsNotQuery() {
        val resp = DnsCodec.buildAResponse(5, null, listOf("192.168.1.20"))
        assertNull(DnsCodec.parseQuery(resp, resp.size)) // QR=1 -> 忽略
    }

    @Test
    fun buildResponseStructure() {
        val q = DnsCodec.DnsQuestion("tvlock.local", DnsCodec.TYPE_A, 0x8001)
        val resp = DnsCodec.buildAResponse(0x1234, q, listOf("192.168.1.20", "10.0.0.5"))
        // 头部
        assertEquals(0x1234, u16(resp, 0))
        assertEquals(0x8400, u16(resp, 2))
        assertEquals(1, u16(resp, 4))       // qd
        assertEquals(2, u16(resp, 6))       // an
        // 找到第一个 answer（名字压缩指针）
        var pos = 12
        // 跳过 question 段
        while (resp[pos].toInt() != 0) pos += 1 + (resp[pos].toInt() and 0xFF)
        pos += 5 // 0 终止 + type(2) + class(2)
        assertEquals(0xC0, resp[pos].toInt() and 0xFF)
        assertEquals(0x0C, resp[pos + 1].toInt() and 0xFF)
        assertEquals(DnsCodec.TYPE_A, u16(resp, pos + 2))
        assertEquals(DnsCodec.CLASS_IN, u16(resp, pos + 4))
        assertEquals(120, u16(resp, pos + 8)) // ttl 低 16 位
        assertEquals(4, u16(resp, pos + 10)) // rdlength
        assertEquals(192, resp[pos + 12].toInt() and 0xFF) // rdata: 192.168.1.20
        assertEquals(168, resp[pos + 13].toInt() and 0xFF)
    }

    @Test
    fun truncatedPacketSafe() {
        val pkt = buildQuery(7, "tvlock.local", DnsCodec.TYPE_A)
        assertNull(DnsCodec.parseQuery(pkt, 8))
        assertNull(DnsCodec.parseQuery(pkt, 13))
    }

    @Test
    fun announceHasNoQuestion() {
        val resp = DnsCodec.buildAResponse(0, null, listOf("192.168.1.20"))
        assertEquals(0, u16(resp, 4))
        assertEquals(1, u16(resp, 6))
    }

    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
}
