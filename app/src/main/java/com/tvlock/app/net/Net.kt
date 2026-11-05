package com.tvlock.app.net

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 本机局域网 IPv4 枚举（供 mDNS 应答 / 状态页显示）。
 */
object Net {

    private var cache: Pair<Long, List<String>>? = null

    fun localIpv4s(maxAgeMs: Long = 30_000): List<String> {
        val c = cache
        val now = System.currentTimeMillis()
        if (c != null && now - c.first < maxAgeMs) return c.second
        val ips = ArrayList<String>()
        try {
            val all = NetworkInterface.getNetworkInterfaces() ?: return ips
            val preferred = ArrayList<String>()
            val others = ArrayList<String>()
            for (ni in all) {
                if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
                for (ia in ni.inetAddresses) {
                    if (ia !is Inet4Address) continue
                    if (ia.isLoopbackAddress) continue
                    val host = ia.hostAddress ?: continue
                    if (host.startsWith("169.254.")) continue // 链路本地地址
                    // 优先输出电视常用的有线/无线网卡
                    val name = ni.name ?: ""
                    if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ra")) {
                        preferred.add(host)
                    } else {
                        others.add(host)
                    }
                }
            }
            ips.addAll(preferred.distinct())
            ips.addAll(others.distinct())
        } catch (_: Throwable) {
        }
        cache = now to ips
        return ips
    }

    fun primaryIp(): String = localIpv4s().firstOrNull() ?: "0.0.0.0"
}
