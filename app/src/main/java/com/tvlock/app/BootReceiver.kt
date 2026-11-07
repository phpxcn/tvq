package com.tvlock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tvlock.app.service.LockCoreService

/**
 * 开机自启：断电/重启后恢复锁机状态。
 * - 锁定中 -> AppCore 心跳会在 500ms 内把锁屏拉回（同一道题、同样进度）
 * - 断电期间到点 -> 立即锁机（墙钟时间照算）
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val ok = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON"
        if (!ok) return
        val svc = Intent(context, LockCoreService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (t: Throwable) {
            AppCore.log("开机自启失败：$t")
        }
        // 同步拉起独立看门狗 App（双通道开机自启，看门狗自身也有 BOOT 广播）
        try {
            val wd = Intent().apply {
                setComponent(
                    android.content.ComponentName(
                        "com.android.tvlockwd",
                        "com.tvlock.watchdog.WatchdogService"
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(wd)
            } else {
                context.startService(wd)
            }
        } catch (_: Throwable) {
            // 看门狗未安装
        }
    }
}
