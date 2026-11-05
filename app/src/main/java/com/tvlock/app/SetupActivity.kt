package com.tvlock.app

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.app.Activity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tvlock.app.net.MdnsResponder
import com.tvlock.app.net.Net
import com.tvlock.app.service.LockCoreService

/**
 * 电视端状态页（桌面入口）：
 *  - 大字显示手机管理地址 tvlock.local:8080 与本机 IP（免记 IP 的兜底）
 *  - 二维码：手机扫码直达控制面板
 *  - 状态卡片 + 测试锁机 / 解除锁定
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

    private val stateListener: (com.tvlock.app.data.TvState) -> Unit = { st ->
        ui.post { renderState(st) }
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

        findViewById<Button>(R.id.btnTestLock).setOnClickListener {
            AppCore.forceLockNow()
        }
        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            AppCore.unlock(true)
        }

        renderStatic()
        renderState(AppCore.state)
        AppCore.addListener(stateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppCore.removeListener(stateListener)
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
        val cats = AppCore.bank.perCat().joinToString("  ·  ") { "${it.first} ${it.second} 题" }
        tvBank.text = "题库：共 ${AppCore.bank.total()} 题${if (cats.isNotEmpty()) "（$cats）" else ""}"
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
