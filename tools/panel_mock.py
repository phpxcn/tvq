#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
手机面板本地预览：用 mock 数据跑 panel.html，无需电视即可调样式。

用法：python3 tools/panel_mock.py [端口=8899]
然后浏览器打开 http://127.0.0.1:8899
"""
import json
import os
import random
import threading
import time
import http.server

PANEL = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     "..", "app", "src", "main", "assets", "panel.html")

# mock 状态
mock = {
    "ok": True, "locked": True, "armed": True, "intervalMin": 10,
    "remainingMs": None, "wrongAttempts": 2, "typed": "1",
    "question": {
        "cat": "pinyin", "q": "「山」的声调是第几声？",
        "opts": ["一声", "二声", "三声", "四声"], "choice": True,
    },
    "bank": {"total": 1766, "categories": [
        {"name": "math", "count": 1211}, {"name": "pinyin", "count": 555}],
        "dir": "/sdcard/TVLock"},
    "ip": "192.168.1.88", "host": "tvlock.local", "port": 8899,
    "serverRunning": True, "mdnsActive": True, "hasPin": False,
    "time": int(time.time() * 1000),
}
# 到计时（剩余 4 分半）
mock["remainingMs"] = 270_000

QUESTIONS = [
    mock["question"],
    {"cat": "math", "q": "5+7等于几？", "choice": False},
    {"cat": "pinyin", "q": "下面哪一个是整体认读音节？",
     "opts": ["zhi", "zha", "chu", "za"], "choice": True},
]


class Handler(http.server.BaseHTTPRequestHandler):

    def log_message(self, *a):
        pass

    def _json(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/api/status"):
            # 每 3 秒换一道题模拟
            q = QUESTIONS[int(time.time() / 3) % len(QUESTIONS)]
            m = dict(mock)
            m["question"] = q
            m["locked"] = True
            self._json(m)
        elif self.path.startswith("/api/bank"):
            bank = {"math": [{"q": "1+1等于几？", "ans": "2"}]}
            body = json.dumps(bank, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Disposition", "attachment; filename=questions.json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path == "/" or self.path.startswith("/index"):
            body = open(PANEL, "rb").read()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(204)
            self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length).decode("utf-8", "replace") if length else ""
        path = self.path.split("?")[0]
        if path == "/api/upload":
            try:
                json.loads(raw)
                self._json({"ok": True, "total": 800, "categories": [
                    {"name": "math", "count": 500}, {"name": "pinyin", "count": 300}]})
            except Exception:
                self._json({"ok": False, "error": "不是合法的 JSON 文件：预览环境的拦截示例"}, 400)
            return
        if path == "/api/interval":
            try:
                minutes = json.loads(raw).get("minutes")
                mock["intervalMin"] = minutes
                mock["remainingMs"] = minutes * 60_000
                self._json({"ok": True})
            except Exception:
                self._json({"ok": False, "error": "缺少 minutes"}, 400)
            return
        # lock / unlock / arm / pin 等全部返回成功
        if path == "/api/lock":
            mock["locked"] = True
        elif path in ("/api/unlock",):
            mock["locked"] = False
        elif path == "/api/arm":
            mock["armed"] = True
            mock["remainingMs"] = mock["intervalMin"] * 60_000
        self._json({"ok": True})


if __name__ == "__main__":
    port = int(__import__("sys").argv[1]) if len(__import__("sys").argv) > 1 else 8899
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"面板预览: http://127.0.0.1:{port}  (Ctrl+C 停止)")
    threading.Thread(target=lambda: srv.serve_forever(), daemon=True).start()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("已停止")
