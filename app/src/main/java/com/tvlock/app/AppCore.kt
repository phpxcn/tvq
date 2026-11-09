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

    /** 核心是否已在本进程初始化（供 GuardianReceiver 判断进程死活，避免无谓拉服务） */
    fun isRunning(): Boolean = started

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
            scheduleNextGuardian()
            startWatchdogApp()
            handler.postDelayed(this::tick, 500)
            // 开机/服务冷启动立即判定一次：断电期间到点/锁定中的场景立刻恢复
            tick()
        }
    }

    /** 看门狗 App 进程是否存活（探活：活着就不拉，避免高频 startService 触发反滥用） */
    private fun isWatchdogAlive(): Boolean {
        return try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.any { it.processName == "com.android.tvlockwd" } ?: false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 反向拉起独立看门狗 App（com.android.tvlockwd）的守卫服务：
     * 看门狗每 3 秒探活本 App（死了才拉，显式 startService 可唤醒 force-stop
     * 后的 stopped 进程，绕过闹钟被清+广播被屏蔽），双向互保。
     * 看门狗未安装时静默（不影响主 App 任何功能）。
     */
    private fun startWatchdogApp(quiet: Boolean = false) {
        try {
            val i = android.content.Intent().apply {
                setComponent(
                    android.content.ComponentName(
                        "com.android.tvlockwd",
                        "com.tvlock.watchdog.WatchdogService"
                    )
                )
            }
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                appContext.startForegroundService(i)
            } else {
                appContext.startService(i)
            }
            if (!quiet) log("看门狗 App 已拉起（双向互保）")
        } catch (_: Throwable) {
            // 看门狗未安装：静默降级
        }
    }

    // ---------- 自愈心跳 ----------

    /**
     * AlarmManager 周期闹钟（约 10 分钟，非精确不耗电）：进程被一键清理等场景杀死后
     * 由 GuardianReceiver 重新拉起常驻服务。一次性闹钟，每次触发后在接收器里重新排队。
     */
    fun scheduleNextGuardian() {
        if (!started) return // 核心未初始化：由服务拉起后的 ensureStarted 注册
        try {
            val am = appContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(appContext, GuardianReceiver::class.java).apply {
                action = GuardianReceiver.ACTION_PING
            }
            val pi = android.app.PendingIntent.getBroadcast(
                appContext, 1001, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val triggerAt = System.currentTimeMillis() + 10 * 60_000L
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (t: Throwable) {
            log("自愈心跳注册失败：$t")
        }
    }

    // ---------- 500ms 心跳 ----------

    private var lastRelaunchAt = 0L

    /** 看门狗拉回间隔（连续拉不回时指数退避，上限 30s） */
    private var relaunchInterval = 600L

    /** 上次反向拉看门狗 App 的时间（探活+死才拉，拉起是事件驱动低频） */
    private var lastWatchdogPullAt = 0L

    private fun tick() {
        val st = state
        val now = System.currentTimeMillis()
        val decision = Scheduler.evaluate(st, now)

        // 周期性探活看门狗 App（30 秒一次）：死了才拉（双向互保的反向链）。
        // 不能高频互拉 startService：会触发 ROM 的反滥用强杀
        if (now - lastWatchdogPullAt >= 30_000L) {
            lastWatchdogPullAt = now
            if (!isWatchdogAlive()) startWatchdogApp(quiet = true)
        }

        if (decision.action == Scheduler.Action.LOCK_NOW) {
            log("锁机触发：${decision.reason}")
            lockNowInternal()
        } else {
            // 心跳落盘（时钟回拨检测依据），20 秒一次足够
            if (now - st.lastSeenAt >= 20_000L) {
                states.mutate { it.copy(lastSeenAt = now) }
            }
        }

        // 倒计时悬浮窗：app 运行期间（非锁定）右上角常驻——进程可见性保活 + 状态指示，
        // 完全穿透不影响任何页面操作；布防显示距下次锁机剩余，暂停定时显示「已暂停」。
        // 锁定中（锁屏全屏答题）隐藏，避免遮挡。
        // 此处重读 state：本 tick 若刚锁机，开头旧引用 locked 仍为 false，
        // 用旧值会闪现一次悬浮窗再等下个 tick 收掉
        val stLive = state
        when {
            stLive.locked -> CountdownOverlay.hide()
            stLive.armed -> CountdownOverlay.update(appContext, Scheduler.remainingMs(stLive, now) ?: 0L)
            else -> CountdownOverlay.showPaused(appContext)
        }
        // 看门狗：锁定中但锁屏不在前台 -> 拉回（HOME 键逃逸兜底）
        // 指数退避：拉不回时间隔翻倍至 30s——频繁 startActivity 会触发系统
        // 反滥用保护（EventSequenceValidator）导致被强杀，恶性循环
        val st2 = state
        if (st2.locked && !LockActivity.isFront) {
            // 确保防逃遮罩在位（onPause 已零空窗弹出，这里兜底防失败）
            GuardOverlay.show(appContext)
            val now2 = System.currentTimeMillis()
            if (now2 - lastRelaunchAt > relaunchInterval) {
                lastRelaunchAt = now2
                relaunchLockActivity()
                relaunchInterval = (relaunchInterval * 2).coerceAtMost(30_000L)
            }
        } else if (st2.locked && LockActivity.isFront) {
            relaunchInterval = 600L // 锁屏已回前台，恢复正常间隔
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

    /**
     * 唯一抽题入口（调用方须持 coreLock）。
     *  - 当前题库全部答对过 → 轮回：清空 answeredIds 从头开始；
     *    同时把 answeredIds 的规模收敛在「当前库题数」以内，避免随使用无限增长。
     *  - 排除已答 + 最近 50 题，避免立刻重复。
     *  recentIds 不清空（本身有 takeLast(50) 上限）：轮回后仍避开刚做过的题。
     */
    private fun pickNextQuestion(extraExclude: Collection<String> = emptyList()): Question {
        val st = state
        if (bank.total() > 0 && bank.remainingCount(st.answeredIds) == 0) {
            states.mutate { it.copy(answeredIds = emptyList()) }
            log("题库已全部答完，轮回清空已答记录")
        }
        val cur = state
        val exclude = cur.answeredIds + cur.recentIds + extraExclude
        return try {
            bankMgr.pickQuestion(exclude)
        } catch (t: Throwable) {
            log("抽题失败：$t")
            Question.FALLBACK
        }
    }

    /** 定时到点触发的锁机 */
    private fun lockNowInternal() {
        val q = pickNextQuestion()
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

    /** 解锁并暂停定时（面板「解锁」/ 电视端管理码）；附加题债一并清零 */
    fun unlock(disarm: Boolean = true) {
        synchronized(coreLock) {
            states.mutate {
                it.copy(
                    locked = false,
                    armed = if (disarm) false else it.armed,
                    pendingExtra = 0,
                    lastSeenAt = System.currentTimeMillis()
                )
            }
        }
        // 解锁后立即收起防逃遮罩（Activity 可能已死，必须全局显式收）
        GuardOverlay.hide()
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

    /** 提交答案的三种结果：答错 / 答对但还有附加题 / 答对并解锁 */
    enum class SubmitResult { WRONG, NEXT, DONE }

    /**
     * 提交答案：
     *  - 答错：不换题，累计答错次数；本题首次答错会追加一道附加题
     *    （答对后需连答，答错的债断电也保留）
     *  - 答对且还有附加题：出下一道新题继续（answeredIds 记入已答）
     *  - 答对且无附加题：解锁并重置倒计时
     */
    fun submitAnswer(input: String): SubmitResult {
        synchronized(coreLock) {
            val st = state
            if (!st.locked) return SubmitResult.DONE
            val q = st.current ?: return SubmitResult.DONE
            val correct = try {
                q.checkAnswer(input)
            } catch (_: Throwable) {
                false
            }
            val now = System.currentTimeMillis()
            if (!correct) {
                states.mutate {
                    val firstMiss = it.wrongAttempts == 0
                    it.copy(
                        wrongAttempts = it.wrongAttempts + 1,
                        pendingExtra = if (firstMiss) it.pendingExtra + 1 else it.pendingExtra,
                        lastSeenAt = now
                    )
                }
                return SubmitResult.WRONG
            }
            // 答对：先把当前题记入已答集（永不再出）并持久化，再判断轮回/连答；
            // 先落盘 answeredIds，pickNextQuestion 的轮回判定才能基于最新已答集准确触发
            states.mutate {
                it.copy(answeredIds = (it.answeredIds + q.id).distinct(), lastSeenAt = now)
            }
            if (st.pendingExtra > 0) {
                val next = pickNextQuestion()
                states.mutate {
                    it.copy(
                        locked = true,
                        current = next,
                        wrongAttempts = 0,
                        typed = "",
                        pendingExtra = it.pendingExtra - 1,
                        recentIds = (it.recentIds + next.id).takeLast(50)
                    )
                }
                notifyState()
                return SubmitResult.NEXT
            }
            states.mutate {
                it.copy(
                    locked = false,
                    wrongAttempts = 0,
                    typed = "",
                    nextLockAt = if (it.armed) now + it.intervalMin * 60_000 else it.nextLockAt
                )
            }
            notifyState()
            return SubmitResult.DONE
        }
    }

    /** 题库热更新（手机上传） */
    fun importBankText(text: String): BankManager.ImportResult {
        val r = bankMgr.importText(text)
        if (r.ok) {
            // 换库后旧题库的 id 已失效，清空已答记录：
            // 避免 answeredIds 随频繁换库累积旧 id 而无限增长，同时新库全部置为未答
            synchronized(coreLock) {
                states.mutate { it.copy(answeredIds = emptyList()) }
            }
            notifyState()
        }
        return r
    }

    /** 已废弃：管理码已统一为 adminPin（电视端 + 手机面板共用），保留仅为兼容旧调用 */
    @Deprecated("统一使用 setAdminPin", ReplaceWith("setAdminPin(pin)"))
    fun setPanelPin(pin: String): Boolean {
        if (pin.isNotEmpty() && (pin.length != 4 || pin.any { it !in '0'..'9' })) return false
        synchronized(coreLock) {
            states.mutate { it.copy(panelPin = pin) }
        }
        notifyState()
        return true
    }

    fun checkAdminPin(pin: String): Boolean = state.adminPin == pin

    /** 题库剩余未答题数（供锁屏/面板展示） */
    fun bankRemaining(): Int = try {
        bankMgr.remainingCount(state.answeredIds)
    } catch (_: Throwable) {
        0
    }

    /** 首次启动引导完成标记 */
    fun setFirstRunDone() {
        synchronized(coreLock) {
            states.mutate { it.copy(firstRunDone = true) }
        }
        notifyState()
    }

    /**
     * 设置统一管理码（电视端锁屏兜底 + 设置页访问门 + 手机面板鉴权共用同一个码）。
     * 约束：4 位数字，且禁止保留默认值 1234（两端规则统一收敛在此）。
     */
    fun setAdminPin(pin: String): Boolean {
        if (pin.length != 4 || pin.any { it !in '0'..'9' }) return false
        if (pin == com.tvlock.app.data.TvState.DEFAULT_ADMIN_PIN) return false
        synchronized(coreLock) {
            states.mutate { it.copy(adminPin = pin) }
        }
        notifyState()
        return true
    }

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
