package com.tvlock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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

    /** 静音保活：后台清理工具通常豁免「正在播放音频」的应用 */
    private var keepAliveTrack: AudioTrack? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFY_ID, buildNotification())
        AppCore.ensureStarted(this)
        startSilentKeepAlive()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppCore.ensureStarted(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 任务卡被划掉：服务本身还活着（前台服务不随任务卡销毁），无需任何动作。
     * 注意：这里不能重启自己——部分电视 ROM（如海信系）会把服务启动视作应用激活，
     * 把界面重新弹到前台，导致用户「按返回退不出去/看电视被打断」。
     * 真正被杀的场景由自愈闹钟兜底。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        AppCore.log("任务卡被划掉，服务继续后台运行")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            keepAliveTrack?.stop()
            keepAliveTrack?.release()
        } catch (_: Throwable) {
        }
        keepAliveTrack = null
        // 服务销毁时确保核心状态已落盘（state.json 每次变更本来就是立即写，这里只是兜底）
        AppCore.log("LockCoreService onDestroy")
    }
    
    /**
     * 静音循环播放（MODE_STATIC + 硬件回环，音量 0）：
     * 后台清理工具（如部分投影仪/电视 ROM 的加速清理）通常会把「音频播放中」
     * 的应用视为用户正在使用而不予清理，这是前台服务之外的第二道保活防线。
     * 不申请音频焦点、音量为 0，不影响观看/听感。
     */
    private fun startSilentKeepAlive() {
        if (keepAliveTrack != null) return
        try {
            val rate = 8000
            val minBuf = AudioTrack.getMinBufferSize(
                rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC, rate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBuf, AudioTrack.MODE_STATIC
            )
            val silence = ShortArray(rate) // 1 秒全零 PCM
            track.write(silence, 0, silence.size)
            track.setLoopPoints(0, silence.size, -1) // 无限循环
            track.setVolume(0f)
            track.play()
            keepAliveTrack = track
            AppCore.log("静音保活已启动")
        } catch (t: Throwable) {
            AppCore.log("静音保活失败（不影响其他功能）：$t")
        }
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
