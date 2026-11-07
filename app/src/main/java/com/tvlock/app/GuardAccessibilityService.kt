package com.tvlock.app

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍守卫服务：开启后应用获得「不可 force-stop」地位，
 * 是对抗 ROM 后台清理守护（如 JMGO 的 com.sparkle.hippo 周期性强杀）的最后防线——
 * 启用无障碍服务的应用，系统层的强杀（FORCE_STOP_PACKAGES）对它同样失效，
 * 这也是那些清理守护自身杀不死的原因。同权限等级才能相互制衡。
 *
 * 附带能力：答题锁屏期间 0ms 拦截 HOME / 最近任务键（比看门狗拉回更快、桌面完全不出）。
 * 拦截条件四重收紧（仅 ACTION_DOWN + 仅 HOME/RECENTS + 仅锁屏在前台 + 仅锁定中），
 * 锁屏一旦不在前台立即放行，绝无把电视「锁死」的风险。
 */
class GuardAccessibilityService : AccessibilityService() {

    companion object {
        /** 无障碍守卫是否已开启（检测系统 enabled_accessibility_services） */
        fun isActive(context: android.content.Context): Boolean {
            val expect = android.content.ComponentName(context, GuardAccessibilityService::class.java).flattenToString()
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            return enabled.split(':').any { it.equals(expect, ignoreCase = true) }
        }

        /** 拉起系统无障碍设置页；ROM 裁剪了标准设置页时降级为 Toast 指引遥控器路径 */
        fun openSettings(context: android.content.Context) {
            val tries = listOf(
                android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
                android.provider.Settings.ACTION_SETTINGS
            )
            for (action in tries) {
                try {
                    val i = android.content.Intent(action)
                    i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                    return
                } catch (_: Throwable) {
                }
            }
            android.widget.Toast.makeText(
                context,
                "本机没有无障碍设置入口（ROM 已裁剪）？该机型不支持按键拦截，可忽略；\n或手动在电视设置里找「无障碍」并开启「电视答题锁·守卫」",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppCore.log("无障碍守卫已连接（不可强杀地位生效）")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 本服务只为获得守卫地位与按键拦截通道，不消费窗口内容事件
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        val e = event ?: return false
        if (e.action == KeyEvent.ACTION_DOWN &&
            (e.keyCode == KeyEvent.KEYCODE_HOME || e.keyCode == KeyEvent.KEYCODE_APP_SWITCH) &&
            LockActivity.isFront && AppCore.state.locked
        ) {
            return true // 消费掉：桌面/最近任务不弹出
        }
        return false
    }
}
