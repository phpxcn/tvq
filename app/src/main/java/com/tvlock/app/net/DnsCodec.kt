package com.tvlock.app.net

import java.io.ByteArrayOutputStream

/**
 * mDNS（组播 DNS）报文编解码（纯函数，JVM 可测）。
 * 只实现 A 记录查询的解析与应答，足够支撑 tvlock.local 域名解析。
 */
object DnsCodec {

    const val MDNS_PORT = 5353
    const val TYPE_A = 1
    const val TYPE_ANY = 255
    const val CLASS_IN = 1

    data class DnsQuestion(val name: String, val type: Int, val cls: Int)

    data class DnsMessage(val id: Int, val isResponse: Boolean, val questions: List<DnsQuestion>)

    /** 解析 mDNS 报文；非查询或无法解析返回 null */
    fun parseQuery(pkt: ByteArray, length: Int): DnsMessage? {
        if (length < 12) return null
        val id = u16(pkt, 0)
        val flags = u16(pkt, 2)
        if (flags and 0x8000 != 0) return null // 响应报文，忽略
        val qdCount = u16(pkt, 4)
        if (qdCount == 0 || qdCount > 16) return null
        var pos = 12
        val questions = ArrayList<DnsQuestion>()
        repeat(qdCount) {
            val name = readName(pkt, pos, length) ?: return null
            pos = name.second
            if (pos + 4 > length) return null
            val type = u16(pkt, pos)
            val cls = u16(pkt, pos + 2)
            pos += 4
            questions.add(DnsQuestion(name.first, type, cls))
        }
        return DnsMessage(id, false, questions)
    }

    /**
     * 构造 A 记录应答。
     * @param question 被应答的查询（null 表示主动广播，无问题段）
     * @param ips 本机 IPv4 文本列表
     */
    fun buildAResponse(id: Int, question: DnsQuestion?, ips: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        // header
        out.write(id ushr 8 and 0xFF)
        out.write(id and 0xFF)
        val flags = 0x8400 // QR=1 应答, AA=1 权威
        out.write(flags ushr 8 and 0xFF)
        out.write(flags and 0xFF)
        val qd = if (question != null) 1 else 0
        out.write(qd ushr 8 and 0xFF)
        out.write(qd and 0xFF)
        val an = ips.size
        out.write(an ushr 8 and 0xFF)
        out.write(an and 0xFF)
        out.write(0); out.write(0) // nscount
        out.write(0); out.write(0) // arcount
        // question 段（回显）
        if (question != null) {
            writeName(out, question.name)
            out.write(question.type ushr 8 and 0xFF)
            out.write(question.type and 0xFF)
            val cls = question.cls or 0x8000 // mDNS 顶位
            out.write(cls ushr 8 and 0xFF)
            out.write(cls and 0xFF)
        }
        // answer 段：名字用指针压缩指向偏移 12（0xC00C）
        for (ip in ips) {
            out.write(0xC0); out.write(0x0C)
            out.write(0); out.write(TYPE_A)
            out.write(0); out.write(CLASS_IN)
            writeTtl(out, 120)
            out.write(0); out.write(4)
            for (b in parseIp(ip)) out.write(b.toInt() and 0xFF)
        }
        return out.toByteArray()
    }

    fun matchesName(q: DnsQuestion, host: String): Boolean {
        if (q.name.equals(host, ignoreCase = true) && (q.type == TYPE_A || q.type == TYPE_ANY)) return true
        return false
    }

    // ---------- 内部 ----------

    private fun u16(b: ByteArray, off: Int): Int = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    /** 读域名，支持压缩指针；返回 (名字, 消费后的新位置) */
    private fun readName(pkt: ByteArray, start: Int, limit: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var pos = start
        var jumped = false
        var jumpCount = 0
        var afterJumpPos = start
        while (true) {
            if (pos >= limit) return null
            val len = pkt[pos].toInt() and 0xFF
            when {
                len == 0 -> {
                    if (!jumped) afterJumpPos = pos + 1
                    break
                }
                len and 0xC0 == 0xC0 -> {
                    if (pos + 1 >= limit) return null
                    val ptr = ((len and 0x3F) shl 8) or (pkt[pos + 1].toInt() and 0xFF)
                    if (!jumped) afterJumpPos = pos + 2
                    jumped = true
                    jumpCount++
                    if (jumpCount > 4) return null
                    pos = ptr
                    if (pos >= limit) return null
                }
                len and 0xC0 != 0 -> return null // 非法标签
                else -> {
                    if (pos + 1 + len > limit) return null
                    if (sb.isNotEmpty()) sb.append('.')
                    for (i in 1..len) sb.append(((pkt[pos + i].toInt() and 0xFF)).toChar())
                    pos += 1 + len
                }
            }
        }
        if (sb.isEmpty()) return null
        return sb.toString() to afterJumpPos
    }

    private fun writeName(out: ByteArrayOutputStream, name: String) {
        for (label in name.split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
    }

    private fun writeTtl(out: ByteArrayOutputStream, ttl: Int) {
        out.write(ttl ushr 24 and 0xFF)
        out.write(ttl ushr 16 and 0xFF)
        out.write(ttl ushr 8 and 0xFF)
        out.write(ttl and 0xFF)
    }

    private fun parseIp(ip: String): ByteArray {
        val parts = ip.split('.')
        val b = ByteArray(4)
        for (i in 0 until 4) {
            b[i] = (parts.getOrElse(i) { "0" }.toIntOrNull() ?: 0).toByte()
        }
        return b
    }
}
