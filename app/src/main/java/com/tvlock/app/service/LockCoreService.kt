package com.tvlock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.tvlock.app.AppCore
import com.tvlock.app.R

/**
 * 常驻前台服务：拉起并持有 AppCore（倒计时 + 看门狗 + mDNS + 面板服务器）。
 * START_STICKY：被系统杀死后自动重启；开机广播也会启动它。
 */
class LockCoreService : Service() {

    companion object {
        private const val CHANNEL_ID = "tvlock_core"
        private const val NOTIFY_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFY_ID, buildNotification())
        AppCore.ensureStarted(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppCore.ensureStarted(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // 服务销毁时确保核心状态已落盘（state.json 每次变更本来就是立即写，这里只是兜底）
        AppCore.log("LockCoreService onDestroy")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "锁机守护",
                NotificationManager.IMPORTANCE_MIN
            )
            ch.description = "保持电视答题锁后台运行"
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setContentTitle("电视答题锁运行中")
            .setContentText("手机管理：http://tvlock.local:8080")
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= 21) {
            builder.setCategory(Notification.CATEGORY_SERVICE)
        }
        return builder.build()
    }
}
