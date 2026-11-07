package com.tvlock.app.net

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * tvlock.local 局域网域名应答器。
 *
 * 监听组播 224.0.0.251:5353（mDNS），凡有设备查询 tvlock.local 的 A 记录，
 * 就用本机 IP 应答，手机浏览器输入 http://tvlock.local:8080 直达控制面板。
 * 同时周期性主动广播（部分系统不主动发查询也能学到记录）。
 */
class MdnsResponder(private val context: Context) {

    companion object {
        const val HOST = "tvlock.local"
        const val GROUP = "224.0.0.251"
        const val ANNOUNCE_INTERVAL_MS = 120_000L
    }

    private val running = AtomicBoolean(false)
    private var socket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var thread: Thread? = null
    private var announceThread: Thread? = null

    @Volatile
    var responderActive: Boolean = false
        private set

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acquireMulticastLock()
        val sock = try {
            val s = MulticastSocket(DnsCodec.MDNS_PORT)
            s.reuseAddress = true
            try {
                s.joinGroup(InetSocketAddress(InetAddress.getByName(GROUP), DnsCodec.MDNS_PORT), null)
            } catch (_: Exception) {
                @Suppress("DEPRECATION")
                s.joinGroup(InetAddress.getByName(GROUP))
            }
            s.soTimeout = 1000
            s
        } catch (e: Exception) {
            // 端口被占用（系统自带 mDNS 守护进程）：仅保留主动广播能力
            running.set(false)
            AppCoreLog("mDNS 绑定失败（转纯广播模式）：${e.message}")
            startAnnounceOnly()
            return
        }
        socket = sock
        responderActive = true
        thread = Thread({ listenLoop(sock) }, "mdns-responder").apply {
            isDaemon = true
            start()
        }
        startAnnounceLoop()
    }

    fun stop() {
        running.set(false)
        responderActive = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        multicastLock?.let {
            try {
                if (it.isHeld) it.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun listenLoop(sock: MulticastSocket) {
        val buf = ByteArray(1500)
        val groupAddr = try {
            InetAddress.getByName(GROUP)
        } catch (_: Exception) {
            return
        }
        while (running.get()) {
            val pkt = DatagramPacket(buf, buf.size)
            try {
                sock.receive(pkt)
            } catch (_: java.net.SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                if (running.get()) continue else break
            }
            try {
                val msg = DnsCodec.parseQuery(pkt.data, pkt.length) ?: continue
                val hit = msg.questions.firstOrNull { DnsCodec.matchesName(it, HOST) } ?: continue
                val ips = Net.localIpv4s()
                if (ips.isEmpty()) continue
                val resp = DnsCodec.buildAResponse(msg.id, hit, ips)
                // RFC 6762：源端口非 5353 的 legacy 查询必须单播回源（部分安卓 App/Windows 走这条路）；
                // 标准查询（源端口 5353）组播应答，局域网内所有设备都能收到
                if (pkt.port != DnsCodec.MDNS_PORT) {
                    sock.send(DatagramPacket(resp, resp.size, pkt.address, pkt.port))
                } else {
                    sock.send(DatagramPacket(resp, resp.size, groupAddr, DnsCodec.MDNS_PORT))
                }
            } catch (_: Exception) {
                // 单包错误不影响循环
            }
        }
    }

    private fun startAnnounceLoop() {
        announceThread = Thread({
            while (running.get()) {
                announce()
                try {
                    Thread.sleep(ANNOUNCE_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "mdns-announce").apply {
            isDaemon = true
            start()
        }
    }

    private fun startAnnounceOnly() {
        announceThread = Thread({
            while (true) {
                announce()
                try {
                    Thread.sleep(60_000L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "mdns-announce").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * 主动广播（无需绑定 5353 也能发出）。
     * 优先用已绑定 5353 的 socket 发送：Mac/iOS 的 mDNS 守护进程会丢弃
     * 源端口非 5353 的 unsolicited 响应，合规源端口才能让它们提前缓存记录。
     */
    private fun announce() {
        try {
            val ips = Net.localIpv4s()
            if (ips.isEmpty()) return
            val resp = DnsCodec.buildAResponse(0, null, ips)
            val group = InetAddress.getByName(GROUP)
            val sock5353 = socket
            if (sock5353 != null) {
                sock5353.send(DatagramPacket(resp, resp.size, group, DnsCodec.MDNS_PORT))
                return
            }
            // 端口被占（announce-only 模式）：退化为临时 socket，尽力而为
            MulticastSocket().use { s ->
                s.send(DatagramPacket(resp, resp.size, group, DnsCodec.MDNS_PORT))
            }
        } catch (_: Exception) {
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("tvlock-mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun AppCoreLog(msg: String) {
        android.util.Log.d("TVLock", msg)
    }
}
