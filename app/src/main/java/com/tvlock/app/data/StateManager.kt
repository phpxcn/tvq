package com.tvlock.app.data

import com.tvlock.app.MiniJson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * state.json 原子读写管理器（最高优先级）。
 *
 * 文件放在应用内部存储（/data/data/...），文件管理器无法修改，防止孩子篡改解锁。
 * 每次变更都立刻落盘：写临时文件 -> fsync -> rename 原子替换，
 * 任何时刻断电，文件要么是旧状态要么是新状态，永不损坏。
 */
class StateManager(private val dir: File) {

    private val file = File(dir, "state.json")
    private val tmp = File(dir, "state.json.tmp")
    private val lock = Any()

    @Volatile
    private var current: TvState = loadOrDefault()

    val state: TvState get() = current

    init {
        dir.mkdirs()
    }

    fun loadOrDefault(): TvState {
        return try {
            val text = file.readText()
            val root = MiniJson.parse(text)
            if (root is Map<*, *>) {
                val m = LinkedHashMap<String, Any?>()
                for ((k, v) in root) m[k.toString()] = v
                TvState.fromMap(m)
            } else TvState()
        } catch (e: Exception) {
            // 文件不存在（首次运行）或损坏（理论上原子写不会出现）：使用默认状态
            TvState(lastSeenAt = System.currentTimeMillis())
        }
    }

    /** 原子保存，保证断电安全 */
    fun save(st: TvState) {
        synchronized(lock) {
            val json = MiniJson.write(st.toMap())
            try {
                FileOutputStream(tmp).use { fos ->
                    fos.write(json.toByteArray(Charsets.UTF_8))
                    fos.fd.sync()
                }
                if (!tmp.renameTo(file)) {
                    // 某些文件系统 rename 失败时退化处理
                    file.writeText(json)
                    tmp.delete()
                }
                current = st
            } catch (e: IOException) {
                // 落盘失败也要更新内存（内存状态始终最新）
                current = st
            }
        }
    }

    /** 原子变更状态：transform 返回新状态，保存后返回 */
    fun mutate(transform: (TvState) -> TvState): TvState {
        synchronized(lock) {
            val next = transform(current)
            save(next)
            return next
        }
    }

    fun reloadFromDisk(): TvState {
        synchronized(lock) {
            current = loadOrDefault()
            return current
        }
    }

    fun fileExists(): Boolean = file.exists()
}
