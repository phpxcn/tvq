#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成应用图标 PNG（纯标准库：zlib + struct 手写 PNG）。
- mipmap-*/ic_launcher.png（API 26 以下的老电视兜底，26+ 用自适应矢量图标）
- mipmap-xhdpi/banner.png（电视桌面横幅 320x180）
设计：深蓝底 + 白色挂锁
"""
import zlib
import struct
import math
import os

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                    "..", "app", "src", "main", "res")

BG = (30, 90, 168, 255)     # #1E5AA8
BG2 = (18, 60, 116, 255)
WHITE = (255, 255, 255, 255)


def rounded(px, py, w, h, r):
    """点 (px,py) 是否在圆角矩形内（坐标 0..1 归一化）"""
    x, y = px / w, py / h
    if x < r / w or x > 1 - r / w:
        if y < r / h or y > 1 - r / h:
            cx = min(max(x, r / w), 1 - r / w)
            cy = min(max(y, r / h), 1 - r / h)
            dx = (x - cx) * w
            dy = (y - cy) * h
            return dx * dx + dy * dy <= r * r
    return True


def draw_lock(size):
    """画一个居中的挂锁，返回 RGBA 像素行列表"""
    rows = []
    # 锁几何（比例按 108 参照系）
    s = size / 108.0
    body_l, body_r = 30 * s, 78 * s
    body_t, body_b = 50 * s, 86 * s
    shackle_r = 13 * s          # 锁梁半径
    shackle_cx = 54 * s
    shackle_cy = 42 * s
    stroke = 3.5 * s            # 半描边宽
    key_cx, key_cy, key_r = 54 * s, 66 * s, 5 * s
    key_hole_w, key_hole_h = 3 * s, 9 * s

    for py in range(size):
        row = bytearray()
        for px in range(size):
            # 背景：圆角矩形（图标本体）
            if not rounded(px + 0.5, py + 0.5, size, size, size * 0.18):
                row += bytes((0, 0, 0, 0))
                continue
            # 背景微渐变
            t = py / size
            bg = tuple(int(BG[i] + (BG2[i] - BG[i]) * t) for i in range(3)) + (255,)
            color = bg
            # 锁体
            if body_l <= px <= body_r and body_t <= py <= body_b:
                color = WHITE
            # 锁梁：圆环上弧
            dx = px - shackle_cx
            dy = py - shackle_cy
            dist = math.sqrt(dx * dx + dy * dy)
            if py <= shackle_cy and shackle_r - stroke <= dist <= shackle_r + stroke:
                color = WHITE
            # 锁孔
            if (px - key_cx) ** 2 + (py - key_cy) ** 2 <= key_r * key_r:
                color = bg
            if key_cx - key_hole_w <= px <= key_cx + key_hole_w and key_cy <= py <= key_cy + key_hole_h:
                color = bg
            row += bytes(color)
        rows.append(bytes(row))
    return rows


def draw_banner(w, h):
    """电视横幅 320x180：深蓝底 + 左侧锁 + 文案区"""
    rows = []
    size = int(h * 0.86)
    lock = draw_lock(size)
    ox, oy = int(h * 0.07), int((h - size) / 2)
    for py in range(h):
        row = bytearray()
        for px in range(w):
            # 背景
            bg = (14, 22, 33, 255) if px < 26 else (18, 60, 116, 255)
            color = bg
            # 叠加锁图标像素
            lx, ly = px - ox, py - oy
            if 0 <= lx < size and 0 <= ly < size:
                p = lock[ly]
                a = p[lx * 4 + 3]
                if a > 0:
                    color = (p[lx * 4], p[lx * 4 + 1], p[lx * 4 + 2], 255)
            # 文字区域用浅色小方块拼出「TV」字样
            tx = int(w * 0.62)
            ty = int(h * 0.30)
            for i, (cx, cy) in enumerate([
                (0, 0), (1, 0), (2, 0), (3, 0), (4, 0), (5, 0), (6, 0), (7, 0),
                (1, 1), (6, 1), (2, 2), (5, 2), (3, 3), (4, 3),
                (8, 0), (8, 1), (8, 2), (8, 3), (9, 0), (9, 1), (9, 2), (9, 3),
                (10, 0), (11, 0), (10, 1), (11, 1), (10, 2), (11, 2), (10, 3), (11, 3),
            ]):
                if tx + cx * 14 <= px < tx + cx * 14 + 10 and ty + cy * 14 <= py < ty + cy * 14 + 10:
                    color = WHITE
            row += bytes(color)
        rows.append(bytes(row))
    return rows


def write_png(path, rows, w, h):
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + r for r in rows)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("  写入", os.path.relpath(path, BASE))


if __name__ == "__main__":
    print("生成应用图标：")
    for dpi, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                      ("xxhdpi", 144), ("xxxhdpi", 192)]:
        rows = draw_lock(size)
        write_png(os.path.join(BASE, f"mipmap-{dpi}", "ic_launcher.png"), rows, size, size)
    rows = draw_banner(320, 180)
    write_png(os.path.join(BASE, "mipmap-xhdpi", "banner.png"), rows, 320, 180)
    print("完成")
