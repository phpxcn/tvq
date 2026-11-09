package com.tvlock.app

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tvlock.app.net.Net
import com.tvlock.app.service.LockCoreService

/**
 * 电视端只读状态页（桌面入口）：
 *  - 大字显示手机管理地址 tvlock.local:8080 与本机 IP（免记 IP 的兜底）
 *  - 二维码：手机扫码直达控制面板
 *  - 状态卡片（锁机状态 / 答题间隔 / 题库）仅展示，不可操作
 *
 * 所有管理操作（锁机 / 解锁 / 改间隔 / 传题库 / 设置管理码）统一在手机面板完成；
 * 电视端仅保留「防护设置」入口（无障碍/自启只能在本机系统设置里激活）。
 */
class SetupActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())

    private lateinit var tvService: TextView
    private lateinit var tvLockState: TextView
    private lateinit var tvInterval: TextView
    private lateinit var tvBank: TextView
    private lateinit var tvBankDir: TextView
    private lateinit var tvUrlIp: TextView
    private lateinit var ivQr: ImageView

    // 解锁时间戳：答完题后若本页被顺带带回前台，自动退回电视画面
    private var lastLocked = false
    private var unlockAt = 0L

    private val stateListener: (com.tvlock.app.data.TvState) -> Unit = { st ->
        ui.post {
            if (lastLocked && !st.locked) unlockAt = System.currentTimeMillis()
            lastLocked = st.locked
            renderState(st)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        AppCore.ensureStarted(applicationContext)
        // 确保常驻服务在跑（用户从桌面打开本应用时）
        startService()

        tvService = findViewById(R.id.tvService)
        tvLockState = findViewById(R.id.tvLockState)
        tvInterval = findViewById(R.id.tvInterval)
        tvBank = findViewById(R.id.tvBank)
        tvBankDir = findViewById(R.id.tvBankDir)
        tvUrlIp = findViewById(R.id.tvUrlIp)
        ivQr = findViewById(R.id.ivQr)

        // 只读：电视端不再提供锁机 / 解锁操作，统一在手机面板完成
        findViewById<View>(R.id.btnTestLock).visibility = View.GONE
        findViewById<View>(R.id.btnUnlock).visibility = View.GONE
        // 防护设置（无障碍/设备管理员）只能在本机系统设置激活，保留入口
        findViewById<Button>(R.id.btnAdmin).setOnClickListener {
            showGuardGuide()
        }

        renderStatic()
        renderState(AppCore.state)
        AppCore.addListener(stateListener)

        // 首次启动仅引导开启开机自启 / 防强杀；管理码统一在手机面板设置，电视端不再设码
        if (!AppCore.state.firstRunDone) ui.post { showFirstRunGuide() }
    }

    override fun onResume() {
        super.onResume()
        // 激活设备管理器 / 无障碍返回后刷新按钮状态
        renderAdminState()
        // 答题解锁后 8 秒内被带回前台（锁屏 finish 后栈顶回落到本页）：
        // 自动退回后台，把电视画面还给正在播放的内容
        if (unlockAt > 0 && System.currentTimeMillis() - unlockAt < 8_000L) {
            unlockAt = 0
            moveTaskToBack(true)
        }
    }

    private fun renderAdminState() {
        val btn = findViewById<Button>(R.id.btnAdmin) ?: return
        btn.setText(if (GuardAccessibilityService.isActive(this)) R.string.setup_admin_on else R.string.setup_admin_off)
    }

    /** 防逃防杀守卫引导：无障碍开启后按 HOME 直接被拦截 + 不可强杀 */
    private fun showGuardGuide() {
        val a11y = GuardAccessibilityService.isActive(this)
        AlertDialog.Builder(this)
            .setTitle(if (a11y) "防护状态" else "防逃防杀守卫（若设备提供）")
            .setMessage(
                (if (a11y) "守卫已开启，防护最强：\n" else "") +
                    "开启后：\n" +
                    "· 答题锁定中按「主页键/最近任务键」直接被拦截，锁屏纹丝不动\n" +
                    "· 应用获得系统级「不可强杀」地位，后台清理工具杀不死\n\n" +
                    (if (a11y) "" else "在电视设置里找「无障碍」入口，列表中开启\n「电视答题锁·守卫」。部分机型（如实测的小米激光投影仪）\n设置里没有该入口（ROM 已裁剪），无法开启——不影响其他功能，\n按主页键会退到桌面，重新打开本应用即恢复锁屏。\n\n") +
                    "极端情况重新打开本应用或重启设备会自动恢复全部锁定状态。"
            )
            .setPositiveButton(if (a11y) "知道了" else "尝试打开设置") { _, _ ->
                if (!a11y) GuardAccessibilityService.openSettings(this)
            }
            .setNegativeButton("关闭") { _, _ -> }
            .setCancelable(true)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppCore.removeListener(stateListener)
    }

    // ---------- 首次启动引导（仅自启 / 防强杀，不设码） ----------

    private fun showFirstRunGuide() {
        val dlg = AlertDialog.Builder(this)
            .setTitle("首次设置：开机自启动 + 防强杀")
            .setMessage(
                "1. 建议允许本应用开机自启动，电视断电/重启后锁机功能自动恢复。\n\n" +
                    "2. 强烈建议开启「防逃防杀守卫」（本页底部按钮）：\n" +
                    "电视系统的后台清理工具会周期性杀死后台应用，开启后应用获得系统级不可强杀地位，锁机功能才不会中断。\n\n" +
                    "常见路径：\n" +
                    "小米：设置 → 应用 → 应用管理 → 电视答题锁 → 开机自启动\n" +
                    "海信/TCL：设置 → 应用管理 → 电视答题锁 → 自启动\n" +
                    "其他品牌：应用管理里的权限/自启动选项\n\n" +
                    "管理码请在手机面板（扫描下方二维码）的「安全」卡片中设置，\n电视端应急解锁与手机面板共用同一个码。\n\n" +
                    "即使不开启，锁定状态也会完整保留，只是断电后需手动打开一次应用。"
            )
            .setPositiveButton("去系统设置") { _, _ ->
                openAppSettings()
                AppCore.setFirstRunDone()
            }
            .setNegativeButton("跳过") { _, _ -> AppCore.setFirstRunDone() }
            .setCancelable(false)
            .create()
        dlg.show()
    }

    /** 多级兜底跳系统设置：应用详情 → 应用列表 → 设置主页（部分电视 ROM 裁剪了页面） */
    private fun openAppSettings() {
        val tries = listOf(
            { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.android.tvlock")) },
            { Intent(Settings.ACTION_APPLICATION_SETTINGS) },
            { Intent(Settings.ACTION_SETTINGS) }
        )
        for (make in tries) {
            try {
                val i = make()
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            } catch (_: Throwable) {
            }
        }
        Toast.makeText(this, "未能自动打开设置，请手动进入：设置 → 应用管理 → 电视答题锁", Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        // 常驻设计：返回键把应用退到后台（服务继续运行、定时锁机照常），
        // 不销毁界面，用户随时可从最近任务/桌面图标回来
        moveTaskToBack(true)
    }

    private fun startService() {
        try {
            startService(android.content.Intent(this, LockCoreService::class.java))
        } catch (_: Throwable) {
        }
    }

    private fun renderStatic() {
        val ip = Net.primaryIp()
        tvUrlIp.text = if (ip == "0.0.0.0") "（网络未连接）" else "或输入：http://$ip:8080"
        val url = if (ip == "0.0.0.0") "http://tvlock.local:8080" else "http://$ip:8080"
        ivQr.setImageBitmap(makeQr(url))
    }

    private fun renderState(st: com.tvlock.app.data.TvState) {
        tvService.text = if (AppCore.serverRunning) "服务运行中 · 面板已开启" else "服务运行中 · 面板未开启"
        tvLockState.text = when {
            st.locked -> "状态：锁定中（答题解锁）"
            st.armed -> "状态：定时锁机已开启"
            else -> "状态：已解锁（定时暂停）"
        }
        val now = System.currentTimeMillis()
        val remaining = com.tvlock.app.core.Scheduler.remainingMs(st, now)
        tvInterval.text = if (st.locked) "答题间隔：每 ${st.intervalMin} 分钟一题（当前锁定中）"
        else if (remaining != null) "答题间隔：每 ${st.intervalMin} 分钟一题（${remaining / 60000 + 1} 分钟后锁机）"
        else "答题间隔：每 ${st.intervalMin} 分钟一题"
        tvBank.text = "题库：共 ${AppCore.bank.total()} 题"
        tvBankDir.text = "题库目录：${AppCore.bank.bankDir.absolutePath}"
    }

    private fun makeQr(text: String): Bitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 480, 480, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    pixels[y * w + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            AppCore.log("二维码生成失败：$t")
            null
        }
    }
}
