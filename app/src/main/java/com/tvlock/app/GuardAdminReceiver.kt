package com.tvlock.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 设备管理器：激活后系统「强行停止」对本应用失效（设置里的按钮变灰），
 * 是对抗电视 ROM 后台清理守护（一键加速/自动清理反复 stop 应用）的核心防线。
 * 电视答题锁作为家长管控类应用，使用设备管理器是平台标准做法。
 */
class GuardAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun component(context: Context) = ComponentName(context, GuardAdminReceiver::class.java)

        /** 是否已激活防强杀保护 */
        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(component(context))
        }

        /** 拉起系统激活确认页（用户按 OK 即激活） */
        fun requestActivate(context: Context) {
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "激活后系统无法「强行停止」电视答题锁，防止被一键加速 / 后台清理误杀。\n卸载本应用前请先取消激活。"
                    )
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Throwable) {
                // 极少数 ROM 裁剪了设备管理页面
            }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        AppCore.log("设备管理器已激活（防强杀保护开启）")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        AppCore.log("设备管理器已取消激活（防强杀保护关闭）")
    }

    /** 在系统设置里尝试取消激活时的警告文案（防止孩子误关） */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "取消保护后应用会被一键清理杀掉，锁机功能将失效。确定要取消吗？"
    }
}
