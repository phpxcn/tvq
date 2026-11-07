package com.tvlock.app.data

import com.tvlock.app.MiniJson

/**
 * 题库校验器（纯函数，可在 JVM 单元测试直接验证）。
 * 坏题库在这里被拦截，绝不进入主库。
 */
object BankValidator {

    const val MAX_QUESTIONS = 5000

    class Bank(private val questions: List<Question>) {
        fun total(): Int = questions.size
        fun all(): List<Question> = questions

        fun toBankJson(): String = MiniJson.write(questions.map { it.toMap() })
    }

    data class Result(
        val ok: Boolean,
        val bank: Bank?,
        val error: String?
    )

    /**
     * 校验一段题库 JSON 文本。官方格式为纯题目数组（不分学科）；
     * 旧版分类格式（对象：分类名 -> 题目数组）仍然兼容，展开后保留分类名。
     * 允许个别坏题被跳过，但必须至少有 1 道有效题、总数不超过上限。
     */
    fun validate(text: String): Result {
        if (text.isBlank()) return fail("文件内容为空")
        val root = try {
            MiniJson.parse(text)
        } catch (e: Exception) {
            return fail("不是合法的 JSON 文件：${e.message}")
        }

        val questions = ArrayList<Question>()
        var skipped = 0
        when (root) {
            is List<*> -> for (item in root) {
                val question = item.toQuestion("")
                if (question == null) { skipped++; continue }
                questions.add(question)
            }
            is Map<*, *> -> {
                if (root.isEmpty()) return fail("题库是空的，没有任何题目")
                for ((k, v) in root) {
                    val cat = k.toString().trim()
                    if (cat.isEmpty()) { skipped++; continue }
                    if (v !is List<*>) return fail("分类「$cat」必须是题目数组")
                    for (item in v) {
                        val question = item.toQuestion(cat)
                        if (question == null) { skipped++; continue }
                        questions.add(question)
                    }
                }
            }
            else -> return fail("JSON 顶层必须是题目数组")
        }

        if (questions.isEmpty()) return fail("没有一道有效题目（跳过了 $skipped 道坏题）")
        if (questions.size > MAX_QUESTIONS) return fail("题目总数 ${questions.size} 超过上限 $MAX_QUESTIONS")

        return Result(true, Bank(questions), if (skipped > 0) "跳过了 $skipped 道格式不对的题" else null)
    }

    private fun Any?.toQuestion(cat: String): Question? {
        if (this !is Map<*, *>) return null
        val m = LinkedHashMap<String, Any?>()
        for ((qk, qv) in this) m[qk.toString()] = qv
        return Question.fromMap(m, cat)
    }

    private fun fail(msg: String) = Result(false, null, msg)
}
