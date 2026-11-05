package com.tvlock.app.service

import com.tvlock.app.AppCore
import com.tvlock.app.MiniJson
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream

/**
 * 手机控制面板 HTTP 服务器（NanoHTTPD，监听 0.0.0.0:8080）。
 *
 * GET  /              面板页面（assets/panel.html）
 * GET  /api/status    实时状态（绝不包含答案）
 * GET  /api/bank      导出当前题库（手机改完传回）
 * POST /api/lock      一键锁机（立刻出新题）
 * POST /api/unlock    一键解锁（暂停定时）
 * POST /api/arm       恢复定时锁机
 * POST /api/interval  修改答题间隔 {"minutes": 5}
 * POST /api/upload    无感导入题库（body 为题库 JSON 文本）
 * POST /api/pin       设置/关闭面板管理码 {"pin": "1234"}（空串关闭）
 * POST /api/pin/verify 校验管理码
 *
 * 开启面板管理码后，所有 POST 需带 X-TVLock-Pin 请求头。
 */
class WebPanelServer(private val core: AppCore) : NanoHTTPD(AppCore.PORT) {

    private val panelHtml: String by lazy {
        try {
            core.appContext.assets.open("panel.html").bufferedReader().readText()
        } catch (e: Exception) {
            "<html><body><h3>面板页面加载失败</h3></body></html>"
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        return try {
            when {
                uri == "/" || uri == "/index.html" -> servePanel()
                uri == "/favicon.ico" -> noContent()
                uri == "/api/status" -> handleStatus()
                uri == "/api/bank" -> handleBank()
                uri == "/api/lock" -> handleSimplePost(session) { core.forceLockNow(); ok() }
                uri == "/api/unlock" -> handleSimplePost(session) { core.unlock(true); ok() }
                uri == "/api/arm" -> handleSimplePost(session) { core.arm(); ok() }
                uri == "/api/interval" -> handleInterval(session)
                uri == "/api/upload" -> handleUpload(session)
                uri == "/api/pin" -> handleSetPin(session)
                uri == "/api/pin/verify" -> handleVerifyPin(session)
                else -> json(Response.Status.NOT_FOUND, mapOf("ok" to false, "error" to "接口不存在"))
            }
        } catch (t: Throwable) {
            AppCore.log("HTTP 处理异常 $uri: $t")
            json(Response.Status.INTERNAL_ERROR, mapOf("ok" to false, "error" to "服务器内部错误：${t.message}"))
        }
    }

    // ---------- 各接口 ----------

    private fun servePanel(): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", panelHtml)

    private fun noContent(): Response =
        newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")

    private fun handleStatus(): Response {
        val st = core.state
        val now = System.currentTimeMillis()
        val remaining = com.tvlock.app.core.Scheduler.remainingMs(st, now)
        val m = LinkedHashMap<String, Any?>()
        m["ok"] = true
        m["locked"] = st.locked
        m["armed"] = st.armed
        m["intervalMin"] = st.intervalMin
        m["remainingMs"] = remaining
        m["wrongAttempts"] = st.wrongAttempts
        m["typed"] = st.typed
        m["question"] = st.current?.let { q ->
            LinkedHashMap<String, Any?>().apply {
                put("cat", q.cat)
                put("q", q.q)
                if (q.opts != null) put("opts", q.opts)
                put("choice", q.isChoice)
            }
        }
        val bank = LinkedHashMap<String, Any?>()
        bank["total"] = core.bank.total()
        bank["categories"] = core.bank.perCat().map { LinkedHashMap<String, Any?>().apply { put("name", it.first); put("count", it.second) } }
        bank["dir"] = core.bank.bankDir.absolutePath
        m["bank"] = bank
        m["ip"] = core.statusIp()
        m["host"] = MdnsResponder_Host
        m["port"] = AppCore.PORT
        m["serverRunning"] = true
        m["mdnsActive"] = AppCore.mdnsActive
        m["hasPin"] = st.panelPin.isNotEmpty()
        m["time"] = now
        return json(Response.Status.OK, m)
    }

    private fun handleBank(): Response {
        val text = core.bank.currentBankJson()
        val bytes = text.toByteArray(Charsets.UTF_8)
        val r = newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            ByteArrayInputStream(bytes),
            bytes.size.toLong()
        )
        r.addHeader("Content-Disposition", "attachment; filename=\"questions.json\"")
        return r
    }

    private fun handleInterval(session: IHTTPSession): Response {
        checkPin(session)?.let { return it }
        val body = readJsonBody(session) ?: return badRequest("请求体不是 JSON")
        val minutes = (body["minutes"] as? Long) ?: (body["minutes"] as? Int)?.toLong() ?: (body["minutes"] as? Double)?.toLong()
        if (minutes == null) return badRequest("缺少 minutes 字段")
        val ok = core.setIntervalMinutes(minutes)
        return if (ok) ok() else badRequest("minutes 需在 1~240 之间")
    }

    private fun handleUpload(session: IHTTPSession): Response {
        checkPin(session)?.let { return it }
        val raw = readRawBody(session)
        if (raw.isBlank()) return badRequest("上传内容为空")
        val r = core.importBankText(raw)
        return if (r.ok) {
            val m = LinkedHashMap<String, Any?>()
            m["ok"] = true
            m["total"] = r.total
            m["categories"] = r.perCat.map { LinkedHashMap<String, Any?>().apply { put("name", it.first); put("count", it.second) } }
            if (r.note != null) m["note"] = r.note
            json(Response.Status.OK, m)
        } else {
            badRequest(r.error ?: "导入失败")
        }
    }

    private fun handleSetPin(session: IHTTPSession): Response {
        // 已设置管理码时，修改必须携带正确旧码
        checkPin(session)?.let { return it }
        val body = readJsonBody(session) ?: return badRequest("请求体不是 JSON")
        val pin = (body["pin"] as? String).orEmpty()
        if (!core.setPanelPin(pin)) return badRequest("管理码必须是 4 位数字，或空串表示关闭")
        return ok()
    }

    private fun handleVerifyPin(session: IHTTPSession): Response {
        val body = readJsonBody(session) ?: return badRequest("请求体不是 JSON")
        val pin = (body["pin"] as? String).orEmpty()
        return if (core.state.panelPin.isNotEmpty() && core.state.panelPin == pin) ok()
        else json(Response.Status.FORBIDDEN, mapOf("ok" to false, "error" to "管理码不对"))
    }

    // ---------- 工具 ----------

    /** 面板管理码开启后，POST 必须携带正确 X-TVLock-Pin */
    private fun handleSimplePost(
        session: IHTTPSession,
        action: () -> Response
    ): Response {
        val err = checkPin(session)
        if (err != null) return err
        // 消费 body（保持连接语义干净）
        readRawBody(session)
        return action()
    }

    private fun checkPin(session: IHTTPSession): Response? {
        val pin = core.state.panelPin
        if (pin.isEmpty()) return null
        val provided = session.headers["x-tvlock-pin"]
        return if (provided == pin) null
        else json(Response.Status.FORBIDDEN, mapOf("ok" to false, "error" to "管理码不对", "needPin" to true))
    }

    private fun readRawBody(session: IHTTPSession): String {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            files["postData"] ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun readJsonBody(session: IHTTPSession): Map<String, Any?>? {
        val raw = readRawBody(session)
        if (raw.isBlank()) return null
        // 开启管理码时同样校验
        val root = try {
            MiniJson.parse(raw)
        } catch (e: Exception) {
            return null
        }
        if (root !is Map<*, *>) return null
        val m = LinkedHashMap<String, Any?>()
        for ((k, v) in root) m[k.toString()] = v
        return m
    }

    private fun json(status: Response.Status, payload: Any?): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", MiniJson.write(payload))

    private fun ok(): Response = json(Response.Status.OK, mapOf("ok" to true))

    private fun badRequest(msg: String): Response =
        json(Response.Status.BAD_REQUEST, mapOf("ok" to false, "error" to msg))

    companion object {
        private const val MdnsResponder_Host = "tvlock.local"
    }
}
