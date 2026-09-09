#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
小程序视觉预览脚本
==================

用本地 Python（PIL/Pillow）把 App 里 Compose Canvas 画的图标和解压动画
渲染成 PNG，方便在不开模拟器的情况下预览效果。

图形坐标与以下源码一一对应：
  - 图标：app/src/main/kotlin/com/quiddity/app/ui/miniapps/MiniAppIcons.kt
  - 解压动画：app/src/main/kotlin/com/quiddity/app/ui/miniapps/MiniAppLaunchGate.kt

用法：
  python mini_app_preview.py --app spy      --out spy.png
  python mini_app_preview.py --app defuse   --out defuse.png
  python mini_app_preview.py --app board    --out board.png
  python mini_app_preview.py --app box --progress 0.62 --out box.png

可选参数：
  --size 400        渲染边长（像素），默认 400
  --progress 0~1    解压动画进度（仅 --app box 生效）

依赖：pip install pillow
"""

import argparse
import math
import os

from PIL import Image, ImageDraw


def rounded_bg_gradient(size, x0, y0, x1, y1, r, c1, c2):
    """圆角矩形 + 竖向渐变，返回 RGBA 图层。"""
    s = float(size)
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle(
        [x0 * s, y0 * s, x1 * s, y1 * s], radius=r * s, fill=255
    )
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    od = ImageDraw.Draw(out)
    steps = 80
    top = y0 * s
    height = (y1 - y0) * s
    for i in range(steps):
        t = i / (steps - 1)
        c = tuple(int(c1[j] + (c2[j] - c1[j]) * t) for j in range(3))
        od.rectangle([0, top + height * i / steps, size, top + height * (i + 1) / steps], fill=c)
    out.putalpha(mask)
    return out


def draw_line(d, size, x0, y0, x1, y1, color, width):
    s = float(size)
    d.line(
        [x0 * s, y0 * s, x1 * s, y1 * s],
        fill=color,
        width=max(1, int(width * s)),
        joint="curve",
    )


def draw_circle(d, size, x, y, r, color, width=None):
    s = float(size)
    bb = [(x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s]
    if width is None:
        d.ellipse(bb, fill=color)
    else:
        d.ellipse(bb, outline=color, width=max(1, int(width * s)))


def spy_icon(size):
    """谁是卧底：深蓝紫底 + 放大镜 + 问号 + 三个小人点。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    img.alpha_composite(
        rounded_bg_gradient(size, 0, 0, 1, 1, 0.20, (74, 88, 166), (36, 42, 98))
    )
    d = ImageDraw.Draw(img)
    cream = (244, 235, 221)
    draw_circle(d, size, 0.47, 0.43, 0.24, cream, 0.075)          # 放大镜外圈
    draw_circle(d, size, 0.47, 0.43, 0.19, (24, 28, 64, 200))      # 镜片
    draw_line(d, size, 0.64, 0.60, 0.80, 0.76, cream, 0.09)        # 手柄
    d.arc(                                                          # 问号上半弧
        [(0.40 * size, 0.34 * size), (0.54 * size, 0.48 * size)],
        start=90, end=360, fill=cream, width=max(1, int(0.05 * size)),
    )
    draw_line(d, size, 0.47, 0.48, 0.47, 0.54, cream, 0.05)        # 问号竖尾
    draw_circle(d, size, 0.47, 0.59, 0.026, cream)                 # 问号点
    for x, y in [(0.70, 0.80), (0.78, 0.85), (0.86, 0.80)]:        # 三个小人
        draw_circle(d, size, x, y, 0.030, cream)
    return img


def defuse_icon(size):
    """拆弹小队：红橙底 + 剪刀剪断引线 + 炸弹。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    img.alpha_composite(
        rounded_bg_gradient(size, 0, 0, 1, 1, 0.20, (204, 72, 52), (142, 34, 28))
    )
    d = ImageDraw.Draw(img)
    silver = (226, 228, 236)
    wire = (245, 239, 230)
    draw_line(d, size, 0.18, 0.30, 0.36, 0.30, wire, 0.035)        # 左半引线
    draw_line(d, size, 0.64, 0.30, 0.82, 0.30, wire, 0.035)        # 右半引线
    draw_circle(d, size, 0.26, 0.30, 0.030, (86, 142, 214))        # 蓝线头
    draw_circle(d, size, 0.74, 0.30, 0.030, (214, 86, 86))         # 红线头
    draw_line(d, size, 0.50, 0.34, 0.37, 0.17, silver, 0.055)      # 剪刀左刃
    draw_line(d, size, 0.50, 0.34, 0.63, 0.17, silver, 0.055)      # 剪刀右刃
    draw_circle(d, size, 0.50, 0.34, 0.026, (90, 92, 104))         # 剪刀轴
    draw_circle(d, size, 0.42, 0.46, 0.075, silver, 0.045)         # 左把手
    draw_circle(d, size, 0.58, 0.46, 0.075, silver, 0.045)         # 右把手
    for x, y in [(0.46, 0.26), (0.54, 0.26), (0.50, 0.32)]:        # 剪断火花
        draw_circle(d, size, x, y, 0.020, (255, 176, 64))
    draw_circle(d, size, 0.50, 0.68, 0.21, (37, 38, 46))           # 炸弹主体
    draw_circle(d, size, 0.43, 0.61, 0.06, (255, 255, 255, 46))    # 高光
    draw_circle(d, size, 0.50, 0.48, 0.045, (94, 96, 110))         # 引信帽
    return img


def board_icon(size):
    """棋盘：木纹底 + 网格 + 黑白子。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    img.alpha_composite(
        rounded_bg_gradient(size, 0, 0, 1, 1, 0.18, (232, 200, 148), (200, 154, 92))
    )
    d = ImageDraw.Draw(img)
    s = float(size)
    pad = 0.10
    inner = 0.80
    cell = inner / 4
    line_color = (122, 90, 50, 191)
    for i in range(5):
        p = pad + i * cell
        draw_line(d, size, pad, p, pad + inner, p, line_color, 0.025)
        draw_line(d, size, p, pad, p, pad + inner, line_color, 0.025)
    draw_circle(d, size, pad + 2 * cell, pad + 2 * cell, 0.035, (122, 90, 50))  # 星位
    draw_circle(d, size, pad + 1 * cell, pad + 1 * cell, 0.16, (27, 27, 31))    # 黑子
    draw_circle(d, size, pad + 3 * cell, pad + 3 * cell, 0.16, (247, 243, 234), 0.04)  # 白子
    draw_circle(d, size, pad + 3 * cell, pad + 1 * cell, 0.10, (27, 27, 31))    # 黑子
    return img


def compress_box(size, progress):
    """解压动画箱体：暗蓝灰箱 + 拉链 + 纹理，进度由 progress 决定。"""
    progress = max(0.0, min(1.0, progress))
    wobble = math.sin(progress * 18.0) * (1.0 - progress) * 3.5
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = float(size)
    box_h = 0.52 + 0.48 * progress
    top = (1 - box_h) / 2
    left = 0.16
    box_w = 0.68
    corner = 12 / 116  # 与 App 里 12.dp / 116.dp 一致
    draw_rect = lambda x0, y0, x1, y1, radius, fill: d.rounded_rectangle(
        [x0 * s, y0 * s, x1 * s, y1 * s], radius=radius * s, fill=fill
    )
    draw_rect(left + 4 / 116, top + 6 / 116, left + box_w + 4 / 116, top + box_h + 6 / 116,
              corner, (0, 0, 0, 51))  # 阴影
    grad = rounded_bg_gradient(size, left, top, left + box_w, top + box_h,
                               corner, (69, 78, 94), (38, 44, 55))
    img.alpha_composite(grad)
    d = ImageDraw.Draw(img)
    line_color = (154, 163, 176)
    for i in range(1, 4):  # 箱体横纹
        y = top + box_h * i / 4
        draw_line(d, size, left + 6 / 116, y, left + box_w - 6 / 116, y,
                  line_color + (82,), 1.5 / 116)
    draw_line(d, size, 0.50, top + box_h * 0.10, 0.50, top + box_h * 0.90,
              (183, 190, 201, 140), 2 / 116)  # 拉链中线
    zip_y = top + box_h * (0.10 + 0.80 * progress)
    draw_circle(d, size, 0.50, zip_y, 5.5 / 116, (195, 202, 212))  # 拉链头
    if progress < 0.95:  # 压扁/展开时的横拉线
        a = 1.0 - progress
        for i in range(3):
            y = top + box_h * (0.22 + 0.26 * i)
            draw_line(d, size, left - 9 / 116, y, left + box_w + 9 / 116, y,
                      line_color + (int(115 * a),), 1 / 116)
    if abs(wobble) > 0.05:
        img = img.rotate(wobble, resample=Image.BICUBIC, center=(size / 2, size / 2))
    return img


def main():
    parser = argparse.ArgumentParser(description="小程序视觉预览（与 Compose Canvas 坐标一致）")
    parser.add_argument("--app", choices=["spy", "defuse", "board", "box"], default="spy")
    parser.add_argument("--out", default=None, help="输出 PNG 路径")
    parser.add_argument("--size", type=int, default=400, help="渲染边长（像素）")
    parser.add_argument("--progress", type=float, default=0.62, help="解压进度 0~1（仅 box）")
    args = parser.parse_args()

    size = max(64, args.size)
    if args.app == "spy":
        img = spy_icon(size)
    elif args.app == "defuse":
        img = defuse_icon(size)
    elif args.app == "board":
        img = board_icon(size)
    else:
        img = compress_box(size, args.progress)

    out = args.out or os.path.join(os.getcwd(), f"preview_{args.app}.png")
    img.save(out)
    print(f"saved -> {out}")


if __name__ == "__main__":
    main()
