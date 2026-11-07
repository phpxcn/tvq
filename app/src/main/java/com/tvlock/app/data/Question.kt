package com.tvlock.app.data

/**
 * 题目模型，与需求给的题库格式完全兼容：
 *  - 带 opts = 选择题（ans 为 1 起始的选项序号，兼容 ans 直接写选项文本）
 *  - 不带 opts = 填空题（ans 为答案文本，口算全部是数字）
 */
data class Question(
    val cat: String = "",      // 分类（仅旧分类格式题库保留；新纯数组格式为空串）
    val q: String,            // 题干
    val opts: List<String>?,  // null = 填空题
    val ans: String           // 选择题：归一化为 "1".."n"；填空题：答案文本
) {
    val isChoice: Boolean get() = opts != null && opts.isNotEmpty()

    /** 稳定 id（用于「最近做过」去重；填空与选择同题干视为不同题） */
    val id: String get() = "$cat|$q|" + (opts?.joinToString(",") ?: "F")

    fun toMap(): LinkedHashMap<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["q"] = q
        if (opts != null) m["opts"] = opts
        m["ans"] = ans
        return m
    }

    /**
     * 判定答案。选择题 input 为 1 起始选项序号；填空题为用户输入文本。
     * 纯数字答案按数值比较（"012" 等于 "12"），其余按去空格文本比较。
     */
    fun checkAnswer(input: String): Boolean {
        if (isChoice) {
            val n = input.trim().toIntOrNull() ?: return false
            return n.toString() == ans
        }
        val a = ans.trim()
        val b = input.trim()
        if (a.isEmpty() || b.isEmpty()) return false
        val na = a.toLongOrNull()
        val nb = b.toLongOrNull()
        return if (na != null && nb != null) na == nb else a == b
    }

    companion object {
        /**
         * 从题库 JSON 对象解析，非法返回 null。
         * ans 归一化规则（选择题）：
         *   "2"                 -> 选项 2
         *   与某个选项文本一致    -> 该选项序号
         *   其余                 -> 非法
         */
        fun fromMap(m: Map<String, Any?>, cat: String): Question? {
            val q = (m["q"] as? String)?.trim().orEmpty()
            if (q.isEmpty()) return null
            val rawOpts = m["opts"]
            val ansRaw = m["ans"]
            if (ansRaw == null) return null
            if (rawOpts == null) {
                val ans = ansRaw.toString().trim()
                if (ans.isEmpty()) return null
                return Question(cat, q, null, ans)
            }
            val opts = rawOpts as? List<*> ?: return null
            val optTexts = opts.mapNotNull {
                val t = (it as? String)?.trim().orEmpty()
                if (t.isEmpty()) null else t
            }
            if (optTexts.size < 2) return null
            val ansText = ansRaw.toString().trim()
            if (ansText.isEmpty()) return null
            // 先按序号
            ansText.toIntOrNull()?.let { n ->
                if (n in 1..optTexts.size) return Question(cat, q, optTexts, n.toString())
            }
            // 再按选项文本匹配
            val idx = optTexts.indexOfFirst { it == ansText }
            if (idx >= 0) return Question(cat, q, optTexts, (idx + 1).toString())
            return null
        }

        /** 题库为空时的内置兜底题（保证锁机功能永不停摆） */
        val FALLBACK = Question("", "1+1等于几？", null, "2")
    }
}
