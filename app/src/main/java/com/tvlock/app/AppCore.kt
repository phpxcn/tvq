package com.tvlock.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tvlock.app.core.Scheduler
import com.tvlock.app.data.BankManager
import com.tvlock.app.data.Question
import com.tvlock.app.data.StateManager
import com.tvlock.app.data.TvState
import com.tvlock.app.net.MdnsResponder
import com.tvlock.app.net.Net
import com.tvlock.app.service.WebPanelServer
import java.io.File
import java.util.Random

/**
 * 应用核心：所有状态变更的唯一入口。
 *
 * 由 LockCoreService（前台常驻服务）拉起并持有，Activity / 手机面板都只通过它操作。
 * 职责：
 *  - 500ms 心跳：到点锁机（墙钟，断电时间照算）、时钟回拨检测、看门狗拉回锁屏
 *  - 锁机 / 解锁 / 武装 / 间隔设置 / 答题判定（每一步立即持久化到 state.json）
 *  - 持有题库管理、mDNS 应答器、手机面板 HTTP 服务器
 */
@SuppressLint("StaticFieldLeak")
object AppCore {

    const val PORT = 8080
    const val TAG = "TVLock"

    lateinit var appContext: Context
        private set

    val random = Random()

    private val coreLock = Any()

    private lateinit var states: StateManager
    private lateinit var bankMgr: BankManager
    private var mdns: MdnsResponder? = null
    private var server: WebPanelServer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    @Volatile
    var serverRunning: Boolean = false
        private set

    @Volatile
    var mdnsActive: Boolean = false
        private set

    val state: TvState get() = states.state
    val stateManager: StateManager get() = states
    val bank: BankManager get() = bankMgr

    private val listeners = ArrayList<(TvState) -> Unit>()

    fun addListener(l: (TvState) -> Unit) {
        synchronized(listeners) { listeners.add(l) }
        l(state)
    }

    fun removeListener(l: (TvState) -> Unit) {
        synchronized(listeners) { listeners.remove(l) }
    }

    private fun notifyState() {
        val st = state
        synchronized(listeners) { listeners.toList() }.forEach { l ->
            try {
                l(st)
            } catch (t: Throwable) {
                Log.w(TAG, "listener error", t)
            }
        }
    }

    fun log(msg: String) {
        Log.d(TAG, msg)
    }

    /** 初始化（幂等，Service / Activity 都会调用，保证任何入口进来核心都在运行） */
    fun ensureStarted(context: Context) {
        synchronized(coreLock) {
            if (started) return
            started = true
            appContext = context.applicationContext
            states = StateManager(File(appContext.filesDir, "state"))
            bankMgr = BankManager(appContext)
            try {
                bankMgr.bootstrapAndLoad()
            } catch (t: Throwable) {
                log("题库引导失败：$t")
            }
            try {
                mdns = MdnsResponder(appContext).also { it.start() }
                mdnsActive = mdns?.responderActive ?: false
            } catch (t: Throwable) {
                log("mDNS 启动失败：$t")
            }
            try {
                server = WebPanelServer(this).also { it.start() }
                serverRunning = server?.isAlive ?: false
            } catch (t: Throwable) {
                log("面板服务器启动失败：$t")
            }
            log("AppCore 启动完成，状态=${state}")
            handler.postDelayed(this::tick, 500)
            // 开机/服务冷启动立即判定一次：断电期间到点/锁定中的场景立刻恢复
            tick()
        }
    }

    // ---------- 500ms 心跳 ----------

    private var lastRelaunchAt = 0L

    private fun tick() {
        val st = state
        val now = System.currentTimeMillis()
        val decision = Scheduler.evaluate(st, now)

        if (decision.action == Scheduler.Action.LOCK_NOW) {
            log("锁机触发：${decision.reason}")
            lockNowInternal()
        } else {
            // 心跳落盘（时钟回拨检测依据），20 秒一次足够
            if (now - st.lastSeenAt >= 20_000L) {
                states.mutate { it.copy(lastSeenAt = now) }
            }
        }

        // 看门狗：锁定中但锁屏不在前台 -> 500ms 内拉回（HOME 键逃逸兜底）
        val st2 = state
        if (st2.locked && !LockActivity.isFront) {
            val now2 = System.currentTimeMillis()
            if (now2 - lastRelaunchAt > 600) {
                lastRelaunchAt = now2
                relaunchLockActivity()
            }
        }

        if (started) handler.postDelayed(this::tick, 500)
    }

    // ---------- 对外操作（面板 / Activity / 服务共用） ----------

    /** 立即锁机出新题（面板「立即锁机」） */
    fun forceLockNow() {
        synchronized(coreLock) {
            if (state.locked) return
            lockNowInternal()
        }
    }

    /** 定时到点触发的锁机 */
    private fun lockNowInternal() {
        val recent = state.recentIds
        val q: Question = try {
            bankMgr.pickQuestion(recent)
        } catch (t: Throwable) {
            log("抽题失败：$t")
            Question.FALLBACK
        }
        val now = System.currentTimeMillis()
        states.mutate {
            it.copy(
                locked = true,
                current = q,
                wrongAttempts = 0,
                typed = "",
                recentIds = (it.recentIds + q.id).takeLast(50),
                lastSeenAt = now
            )
        }
        notifyState()
        relaunchLockActivity()
    }

    /** 解锁并暂停定时（面板「解锁」/ 电视端管理码） */
    fun unlock(disarm: Boolean = true) {
        synchronized(coreLock) {
            states.mutate {
                it.copy(locked = false, armed = if (disarm) false else it.armed, lastSeenAt = System.currentTimeMillis())
            }
        }
        notifyState()
    }

    /** 恢复定时锁机（重新开始倒计时） */
    fun arm() {
        synchronized(coreLock) {
            val now = System.currentTimeMillis()
            states.mutate {
                it.copy(armed = true, nextLockAt = now + it.intervalMin * 60_000, lastSeenAt = now)
            }
        }
        notifyState()
    }

    /** 修改答题间隔（分钟），1..240 */
    fun setIntervalMinutes(min: Long): Boolean {
        if (min < 1 || min > 240) return false
        synchronized(coreLock) {
            val now = System.currentTimeMillis()
            states.mutate {
                val armed = it.armed && !it.locked
                it.copy(
                    intervalMin = min,
                    // 已在解锁状态改间隔：直接重置倒计时
                    nextLockAt = if (armed) now + min * 60_000 else it.nextLockAt,
                    lastSeenAt = now
                )
            }
        }
        notifyState()
        return true
    }

    /** 填空题输入内容变更（每键持久化，断电保留输入进度） */
    fun setTyped(text: String) {
        synchronized(coreLock) {
            states.mutate { it.copy(typed = text.take(8)) }
        }
        notifyState()
    }

    /** 提交答案：答对自动解锁并重置倒计时；答错只累计次数，绝不换题 */
    fun submitAnswer(input: String): Boolean {
        synchronized(coreLock) {
            val st = state
            if (!st.locked) return true
            val q = st.current ?: return true
            val correct = try {
                q.checkAnswer(input)
            } catch (_: Throwable) {
                false
            }
            val now = System.currentTimeMillis()
            if (correct) {
                states.mutate {
                    it.copy(
                        locked = false,
                        wrongAttempts = 0,
                        typed = "",
                        nextLockAt = if (it.armed) now + it.intervalMin * 60_000 else it.nextLockAt,
                        lastSeenAt = now
                    )
                }
            } else {
                states.mutate {
                    it.copy(wrongAttempts = it.wrongAttempts + 1, lastSeenAt = now)
                }
            }
            notifyState()
            return correct
        }
    }

    /** 题库热更新（手机上传） */
    fun importBankText(text: String): BankManager.ImportResult {
        return bankMgr.importText(text)
    }

    fun setPanelPin(pin: String): Boolean {
        if (pin.isNotEmpty() && (pin.length != 4 || pin.any { it !in '0'..'9' })) return false
        synchronized(coreLock) {
            states.mutate { it.copy(panelPin = pin) }
        }
        notifyState()
        return true
    }

    fun checkAdminPin(pin: String): Boolean = state.adminPin == pin

    // ---------- UI 辅助 ----------

    fun relaunchLockActivity() {
        try {
            val i = Intent(appContext, LockActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            appContext.startActivity(i)
        } catch (t: Throwable) {
            // Android 10+ 后台启动限制（appops 未授权的极端情况）：下一轮 tick 会重试
            log("拉起锁屏失败：${t}")
        }
    }

    fun statusIp(): String = Net.primaryIp()

    fun baseUrl(): String {
        val ip = Net.primaryIp()
        return if (ip == "0.0.0.0") "http://tvlock.local:$PORT" else "http://$ip:$PORT"
    }
}
