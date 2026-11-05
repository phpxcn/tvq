package com.tvlock.app.data

import com.tvlock.app.MiniJson

/**
 * 题库校验器（纯函数，可在 JVM 单元测试直接验证）。
 * 坏题库在这里被拦截，绝不进入主库。
 */
object BankValidator {

    const val MAX_QUESTIONS = 5000

    class Bank(val cats: LinkedHashMap<String, List<Question>>) {
        fun total(): Int = cats.values.sumOf { it.size }
        fun all(): List<Question> = cats.values.flatten()
        fun perCat(): List<Pair<String, Int>> = cats.map { it.key to it.value.size }
        fun toBankJson(): String {
            val m = LinkedHashMap<String, Any?>()
            for ((k, v) in cats) m[k] = v.map { it.toMap() }
            return MiniJson.write(m)
        }
    }

    data class Result(
        val ok: Boolean,
        val bank: Bank?,
        val error: String?
    )

    /**
     * 校验一段题库 JSON 文本。
     * 允许个别坏题被跳过，但必须至少有 1 道有效题、总数不超过上限。
     */
    fun validate(text: String): Result {
        if (text.isBlank()) return fail("文件内容为空")
        val root = try {
            MiniJson.parse(text)
        } catch (e: Exception) {
            return fail("不是合法的 JSON 文件：${e.message}")
        }
        if (root !is Map<*, *>) return fail("JSON 顶层必须是对象（分类 -> 题目数组）")
        if (root.isEmpty()) return fail("题库是空的，没有任何分类")

        val cats = LinkedHashMap<String, List<Question>>()
        var skipped = 0
        for ((k, v) in root) {
            val cat = k.toString().trim()
            if (cat.isEmpty()) { skipped++; continue }
            if (v !is List<*>) return fail("分类「$cat」必须是题目数组")
            val list = ArrayList<Question>()
            for (item in v) {
                if (item !is Map<*, *>) { skipped++; continue }
                val m = LinkedHashMap<String, Any?>()
                for ((qk, qv) in item) m[qk.toString()] = qv
                val question = Question.fromMap(m, cat)
                if (question == null) { skipped++; continue }
                list.add(question)
            }
            if (list.isNotEmpty()) cats[cat] = list
        }

        val total = cats.values.sumOf { it.size }
        if (total == 0) return fail("没有一道有效题目（跳过了 $skipped 道坏题）")
        if (total > MAX_QUESTIONS) return fail("题目总数 $total 超过上限 $MAX_QUESTIONS")

        return Result(true, Bank(cats), if (skipped > 0) "跳过了 $skipped 道格式不对的题" else null)
    }

    private fun fail(msg: String) = Result(false, null, msg)
}
