package com.tvlock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tvlock.app.service.LockCoreService

/**
 * 自愈心跳接收器：AppCore 注册的 AlarmManager 周期闹钟（约 10 分钟）唤醒这里，
 * 若进程此前被系统「一键清理」等场景杀死，此处把常驻前台服务重新拉起。
 *
 * 与已有机制互补：
 *  - START_STICKY：系统杀进程后尽力重启（国产 ROM 常拦截）
 *  - onTaskRemoved：任务卡被划掉时服务自救
 *  - BOOT_COMPLETED：电视重启后恢复
 *  - 本接收器：兜住以上全部失效的情况（每 10 分钟一次）
 */
class GuardianReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PING = "com.tvlock.app.GUARDIAN_PING"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PING) return
        // 进程活着：只重排下一次闹钟，绝不动服务——
        // 部分电视 ROM 会把服务启动视作应用激活而弹出界面，影响观看
        if (AppCore.isRunning()) {
            AppCore.scheduleNextGuardian()
            return
        }
        // 进程已死（被清理）：拉起常驻服务，冷启动不会恢复界面
        AppCore.log("自愈心跳：进程已死，重新拉起服务")
        val svc = Intent(context, LockCoreService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (t: Throwable) {
            // 极端：进程刚被拉起就再次被杀，下一轮心跳会重试
            AppCore.log("自愈拉起服务失败：$t")
        }
    }
}
