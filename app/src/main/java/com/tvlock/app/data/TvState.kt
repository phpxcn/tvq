package com.tvlock.app.data

/**
 * 全部持久化状态（state.json，内部存储）。
 *
 * 防逃设计要点：
 *  - current 独立保存完整题目（不依赖题库文件），更新题库绝不影响当前卡住的题
 *  - typed / wrongAttempts 实时持久化，断电重启后连同输入进度原样恢复
 *  - nextLockAt 用墙钟时间戳：拔插头期间时间照算，重插立即判定是否到点
 *  - lastSeenAt 用于检测时钟回拨（电视无 RTC 电池或人为改时间 -> 直接锁机）
 */
data class TvState(
    val version: Int = 1,
    val locked: Boolean = false,          // 锁定中
    val armed: Boolean = true,            // 定时锁机开关（面板「解锁」= 解锁并暂停）
    val intervalMin: Long = 10,           // 答题间隔（分钟）
    val nextLockAt: Long = 0,             // 下次锁机墙钟时间戳（毫秒）
    val lastSeenAt: Long = 0,             // 最近一次心跳墙钟时间戳
    val wrongAttempts: Int = 0,           // 当前题答错次数
    val typed: String = "",               // 填空题已输入内容（断电保留）
    val current: Question? = null,        // 当前题目（完整冗余存储）
    val recentIds: List<String> = emptyList(), // 最近做过的题（去重，最多50）
    val pendingExtra: Int = 0,            // 答错遗留的附加题数（答对当前题后需连答）
    val answeredIds: List<String> = emptyList(), // 已答对过的题（永不再出，答完全部后轮回）
    val adminPin: String = DEFAULT_ADMIN_PIN, // 电视端管理码（家长兜底：锁屏↑↑↓↓←→←→后输入；首次启动强制修改，禁止保留默认值）
    val panelPin: String = "",            // 手机面板管理码（空 = 关闭）
    val firstRunDone: Boolean = false     // 首次启动引导（开机自启指引+管理码设置）是否完成
) {
    fun toMap(): LinkedHashMap<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["version"] = version
        m["locked"] = locked
        m["armed"] = armed
        m["intervalMin"] = intervalMin
        m["nextLockAt"] = nextLockAt
        m["lastSeenAt"] = lastSeenAt
        m["wrongAttempts"] = wrongAttempts
        m["typed"] = typed
        m["current"] = current?.let { q ->
            val cm = LinkedHashMap<String, Any?>()
            cm["cat"] = q.cat
            cm.putAll(q.toMap())
            cm
        }
        m["recentIds"] = recentIds
        m["pendingExtra"] = pendingExtra
        m["answeredIds"] = answeredIds
        m["adminPin"] = adminPin
        m["panelPin"] = panelPin
        m["firstRunDone"] = firstRunDone
        return m
    }

    companion object {
        /** 默认管理码：仅作为初始占位，首次启动引导必须修改，禁止保留 */
        const val DEFAULT_ADMIN_PIN = "1234"

        fun fromMap(m: Map<String, Any?>): TvState {
            val cur = (m["current"] as? Map<*, *>)?.let {
                val cm = linkedMapOf<String, Any?>()
                for ((k, v) in it) cm[k.toString()] = v
                Question.fromMap(cm, (it["cat"] as? String) ?: "")
            }
            return TvState(
                version = (m["version"] as? Long)?.toInt() ?: 1,
                locked = m["locked"] as? Boolean ?: false,
                armed = (m["armed"] as? Boolean) ?: true,
                intervalMin = (m["intervalMin"] as? Long) ?: 10L,
                nextLockAt = (m["nextLockAt"] as? Long) ?: 0L,
                lastSeenAt = (m["lastSeenAt"] as? Long) ?: 0L,
                wrongAttempts = (m["wrongAttempts"] as? Long)?.toInt() ?: 0,
                typed = (m["typed"] as? String) ?: "",
                current = cur,
                recentIds = (m["recentIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                pendingExtra = (m["pendingExtra"] as? Long)?.toInt() ?: 0,
                answeredIds = (m["answeredIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                adminPin = (m["adminPin"] as? String) ?: DEFAULT_ADMIN_PIN,
                panelPin = (m["panelPin"] as? String) ?: "",
                firstRunDone = m["firstRunDone"] as? Boolean ?: false
            )
        }
    }
}
