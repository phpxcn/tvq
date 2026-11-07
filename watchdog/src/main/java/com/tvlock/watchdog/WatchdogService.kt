package com.tvlock.watchdog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * 独立看门狗 App 的守卫服务。
 *
 * 每 3 秒探活主 App（com.android.tvlock）的常驻服务：
 * force-stop 后的应用处于 stopped state（闹钟被清、广播被屏蔽），
 * 但显式 startService 不受 stopped state 限制——这是对抗 ROM
 * 后台清理守护（周期性强杀+清闹钟）的有效通道：两个 App 分属
 * 两个独立 uid/进程，被同一次清理连杀的概率远低于单 App。
 *
 * 主 App 也会反向拉起本服务（ensureStarted + 开机广播），双向互保。
 */
class WatchdogService : Service() {

    companion object {
        // 1 秒探活（getRunningAppProcesses 普通查询不触发反滥用；死才拉是事件驱动）
        private const val INTERVAL = 1000L
        // 主 App 安装标识（伪装 com.android.* 前缀避开 hippo 强杀；类全名仍是 namespace 前缀）
        private const val MAIN_PKG = "com.android.tvlock"
        private const val MAIN_SVC = "com.tvlock.app.service.LockCoreService"
        private const val CHANNEL_ID = "tvlock_watchdog"
        private const val NOTIFY_ID = 2
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFY_ID, buildNotification())
        handler.post(watch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // 被杀时尽力自拉（主 App 也会拉本服务）
        try {
            val i = Intent(this, WatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (_: Throwable) {
        }
    }

    private val watch: Runnable = object : Runnable {
        override fun run() {
            try {
                // 先探活再拉：getRunningAppProcesses 是普通查询（无 startService），
                // 主进程活着就跳过——高频互拉 startService 会触发 ROM 反滥用强杀，
                // 只有真死时才发起拉起，频率被压到「事件驱动」
                if (isPackageInstalled(MAIN_PKG) && !isProcessRunning(MAIN_PKG)) {
                    // force-stop 后 stopped state 只屏蔽广播，显式 startService 能唤醒
                    val i = Intent().apply { component = ComponentName(MAIN_PKG, MAIN_SVC) }
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                }
                // 主 App 被卸载时静默：用户卸载主 App 不应被反复打扰
            } catch (_: Throwable) {
                // 主 App 未安装/异常：下一轮再试
            }
            handler.postDelayed(this, INTERVAL)
        }
    }

    /** 主 App 进程是否存活（跨 App 查询；查不到时返回 false 由拉起兜底） */
    private fun isProcessRunning(pkg: String): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.any { it.processName == pkg } ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0) != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "守卫运行", NotificationManager.IMPORTANCE_MIN
                ).apply { setShowBadge(false) }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("电视答题锁·守卫")
            .setContentText("守护主程序后台运行")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }
}
