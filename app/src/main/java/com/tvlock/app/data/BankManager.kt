package com.tvlock.app.data

import android.content.Context
import android.os.FileObserver
import com.tvlock.app.AppCore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 题库管理：加载 / 校验 / inbox 热更新 / 抽题去重。
 *
 * 目录结构（优先 /sdcard/TVLock，不可写时退回应用外部私有目录）：
 *   TVLock/questions.json        主库
 *   TVLock/questions.json.bak    换库前的备份
 *   TVLock/inbox/                新题库投放目录（U 盘 / 文件管理器）
 *   TVLock/inbox/rejected/       校验失败的坏文件
 *   TVLock/inbox/imported/       校验通过的外来文件存档
 *
 * 热更新原则：换库只影响之后抽题，绝不影响当前锁定的题（题目在 state.json 里独立保存）。
 */
class BankManager(private val context: Context) {

    val bankDir: File = resolveDir()
    val bankFile: File get() = File(bankDir, "questions.json")
    private val inboxDir: File get() = File(bankDir, "inbox")
    private val rejectedDir: File get() = File(inboxDir, "rejected")
    private val importedDir: File get() = File(inboxDir, "imported")

    @Volatile
    private var bank: BankValidator.Bank? = null

    private val observerStarted = AtomicBoolean(false)
    private var observer: FileObserver? = null

    fun total(): Int = bank?.total() ?: 0

    fun perCat(): List<Pair<String, Int>> = bank?.perCat() ?: emptyList()

    fun currentBankJson(): String = bank?.toBankJson() ?: "{}"

    /** 首次运行：从 assets 引导初始题库，然后加载主库 */
    fun bootstrapAndLoad() {
        bankDir.mkdirs()
        inboxDir.mkdirs()
        rejectedDir.mkdirs()
        importedDir.mkdirs()
        if (!bankFile.exists()) {
            copyAsset("questions.json", bankFile)
        }
        // 主库加载失败（文件损坏等）时回退到 assets 初始题库
        val loaded = loadFromFile(bankFile)
        if (loaded == null) {
            val assetText = readAsset("questions.json")
            val fromAsset = assetText?.let { BankValidator.validate(it).bank }
            bank = fromAsset
        } else {
            bank = loaded
        }
        startInboxObserver()
    }

    /**
     * 导入一段题库文本（手机面板上传路径）。
     * 校验通过才原子替换主库并热加载；当前锁定中的题目不受影响。
     */
    fun importText(text: String): ImportResult {
        val r = BankValidator.validate(text)
        if (!r.ok) {
            return ImportResult(false, error = r.error ?: "校验失败")
        }
        val newBank = r.bank!!
        // 存档一份到 inbox（作为记录，observer 会忽略 import- 开头的自有文件）
        writeTextSafely(File(inboxDir, "import-${System.currentTimeMillis()}.json"), text)
        // 备份旧库
        if (bankFile.exists()) {
            try {
                bankFile.copyTo(File(bankDir, "questions.json.bak"), overwrite = true)
            } catch (_: IOException) {
            }
        }
        // 原子替换主库
        writeTextSafely(bankFile, text)
        bank = newBank
        return ImportResult(
            ok = true,
            perCat = newBank.perCat(),
            total = newBank.total(),
            note = r.error
        )
    }

    /** 外来文件导入路径（inbox 目录投放，U 盘 / 文件管理器） */
    fun importForeignFile(file: File) {
        val text = try {
            file.readText()
        } catch (e: IOException) {
            return
        }
        val r = BankValidator.validate(text)
        if (r.ok) {
            val res = importText(text)
            // 已由 importText 存档，移走原文件避免重复导入
            try {
                file.copyTo(File(importedDir, "${System.currentTimeMillis()}-${file.name}"), overwrite = true)
                file.delete()
            } catch (_: IOException) {
            }
            AppCore.log("题库热更新：${res.total} 题 (${res.note ?: "全部有效"})")
        } else {
            try {
                rejectedDir.mkdirs()
                file.copyTo(File(rejectedDir, "${System.currentTimeMillis()}-${file.name}"), overwrite = true)
                file.delete()
            } catch (_: IOException) {
            }
            AppCore.log("题库导入被拦截：${r.error}")
        }
    }

    /**
     * 随机抽题，尽量避开最近做过的题。
     */
    fun pickQuestion(recentIds: List<String>): com.tvlock.app.data.Question {
        val b = bank ?: return Question.FALLBACK
        val all = b.all()
        if (all.isEmpty()) return Question.FALLBACK
        val fresh = all.filter { it.id !in recentIds }
        val pool = if (fresh.isNotEmpty()) fresh else all
        return pool[AppCore.random.nextInt(pool.size)]
    }

    // ---------- 内部 ----------

    private fun startInboxObserver() {
        if (observerStarted.getAndSet(true)) return
        if (!inboxDir.isDirectory) return
        try {
            observer = object : FileObserver(inboxDir.path, CLOSE_WRITE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    val name = File(path).name
                    // 只处理外来文件；import- 前缀是我们自己写的存档
                    if (name.startsWith("import-") || name.startsWith(".")) return
                    if (!name.endsWith(".json", ignoreCase = true)) return
                    val f = File(inboxDir, name)
                    if (f.isFile && f.length() in 1..20_000_000L) {
                        importForeignFile(f)
                    }
                }
            }
            observer?.startWatching()
        } catch (t: Throwable) {
            // 某些 ROM 不支持外置存储 inotify：忽略（手机上传路径不依赖 observer）
            AppCore.log("inbox 监听不可用：${t.message}")
        }
    }

    private fun loadFromFile(f: File): BankValidator.Bank? {
        val text = try {
            if (f.exists()) f.readText() else return null
        } catch (e: IOException) {
            return null
        }
        return BankValidator.validate(text).bank
    }

    private fun readAsset(name: String): String? = try {
        context.assets.open(name).bufferedReader().readText()
    } catch (e: IOException) {
        null
    }

    private fun copyAsset(name: String, dest: File) {
        val text = readAsset(name) ?: return
        writeTextSafely(dest, text)
    }

    private fun writeTextSafely(f: File, text: String) {
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            FileOutputStream(tmp).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            if (!tmp.renameTo(f)) {
                f.writeText(text)
                tmp.delete()
            }
        } catch (e: IOException) {
            AppCore.log("写文件失败 ${f.path}: ${e.message}")
        }
    }

    private fun resolveDir(): File {
        // 首选公共目录 /sdcard/TVLock（U 盘投放题库需要）
        val primary = File(
            android.os.Environment.getExternalStorageDirectory(),
            "TVLock"
        )
        try {
            primary.mkdirs()
            if (primary.canWrite()) return primary
        } catch (_: Throwable) {
        }
        // 退化：应用外部私有目录
        val ext = context.getExternalFilesDir(null)
        if (ext != null) return File(ext, "TVLock")
        return File(context.filesDir, "TVLock")
    }

    data class ImportResult(
        val ok: Boolean,
        val perCat: List<Pair<String, Int>> = emptyList(),
        val total: Int = 0,
        val note: String? = null,
        val error: String? = null
    )
}
