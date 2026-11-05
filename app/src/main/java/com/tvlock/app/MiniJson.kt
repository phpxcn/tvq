package com.tvlock.app

/**
 * 极简 JSON 解析/序列化（纯 Kotlin，无任何依赖）。
 *
 * 用自带的 MiniJson 而不用 org.json，是为了让单元测试能直接在 JVM 上跑
 * （org.json 在 JVM 单元测试里是空壳实现），同时避免引入额外依赖。
 *
 * 解析结果类型：LinkedHashMap<String, Any?> / ArrayList<Any?> / String / Long / Double / Boolean / null
 */
object MiniJson {

    fun parse(src: String): Any? {
        val p = Parser(src)
        p.skipWs()
        val v = p.parseValue()
        p.skipWs()
        require(p.i >= p.s.length) { "JSON 末尾有多余内容: 位置 ${p.i}" }
        return v
    }

    fun write(v: Any?): String {
        val sb = StringBuilder()
        writeValue(sb, v)
        return sb.toString()
    }

    // ---------- 序列化 ----------
    private fun writeValue(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int -> sb.append(v.toLong().toString())
            is Long -> sb.append(v.toString())
            is Double -> {
                if (v.isFinite()) {
                    if (v == Math.floor(v) && Math.abs(v) < 1e15) sb.append(v.toLong().toString())
                    else sb.append(v.toString())
                } else sb.append("null")
            }
            is Float -> writeValue(sb, v.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    writeValue(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeValue(sb, item)
                }
                sb.append(']')
            }
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // ---------- 解析 ----------
    private class Parser(val s: String) {
        var i = 0

        fun skipWs() {
            while (i < s.length && s[i].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) i++
        }

        fun parseValue(): Any? {
            if (i >= s.length) throw IllegalArgumentException("JSON 意外结束")
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> parseNumber()
            }
        }

        fun expect(word: String) {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("非法 JSON 字面量于位置 $i")
            i += word.length
        }

        fun parseObject(): LinkedHashMap<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return map }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') throw IllegalArgumentException("对象键必须是字符串于位置 $i")
                val k = parseString()
                skipWs()
                if (i >= s.length || s[i] != ':') throw IllegalArgumentException("缺少冒号于位置 $i")
                i++
                skipWs()
                map[k] = parseValue()
                skipWs()
                when {
                    i < s.length && s[i] == ',' -> i++
                    i < s.length && s[i] == '}' -> { i++; return map }
                    else -> throw IllegalArgumentException("对象格式错误于位置 $i")
                }
            }
        }

        fun parseArray(): ArrayList<Any?> {
            val list = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return list }
            while (true) {
                skipWs()
                list.add(parseValue())
                skipWs()
                when {
                    i < s.length && s[i] == ',' -> i++
                    i < s.length && s[i] == ']' -> { i++; return list }
                    else -> throw IllegalArgumentException("数组格式错误于位置 $i")
                }
            }
        }

        fun parseString(): String {
            i++ // "
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '"' -> { i++; return sb.toString() }
                    c == '\\' -> {
                        i++
                        if (i >= s.length) throw IllegalArgumentException("转义意外结束")
                        when (val e = s[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 >= s.length) throw IllegalArgumentException("\\u 转义不完整")
                                val hex = s.substring(i + 1, i + 5)
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("非法转义 \\$e")
                        }
                        i++
                    }
                    else -> { sb.append(c); i++ }
                }
            }
            throw IllegalArgumentException("字符串未闭合")
        }

        fun parseNumber(): Any {
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            var isDouble = false
            while (i < s.length) {
                val c = s[i]
                when {
                    c in '0'..'9' -> i++
                    c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-' -> { isDouble = true; i++ }
                    else -> break
                }
            }
            val text = s.substring(start, i)
            if (text.isEmpty() || text == "-" || text == "+") throw IllegalArgumentException("非法数字于位置 $start")
            return if (isDouble) text.toDouble() else (text.toLongOrNull() ?: text.toDouble())
        }
    }
}
