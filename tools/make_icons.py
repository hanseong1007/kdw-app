#!/usr/bin/env python3
"""경덕농수산 앱 아이콘 생성기.

  python3 tools/make_icons.py

- src/main/res    : 통합 포털 아이콘 (초록 상자 + 잎)
- src/worklog/res : 업무일지 아이콘 (초록 클립보드)
안드로이드 8+ 적응형 아이콘용 foreground PNG 와 8.0 이전용 레거시 PNG 를 함께 만듭니다.
"""

import os

from PIL import Image, ImageDraw

BRAND_TOP = (46, 125, 50, 255)     # #2E7D32
BRAND_BOTTOM = (27, 94, 32, 255)   # #1B5E20
BRAND = (27, 94, 32, 255)
WHITE = (255, 255, 255, 255)

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS_DIR)  # 저장소 루트 (app/ 의 부모)

LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FOREGROUND_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SS = 4  # 슈퍼샘플링 배율


def rounded_gradient(size, radius_ratio, circle=False):
    """초록 그라데이션 + 둥근 모서리(또는 원) 배경."""
    grad = Image.new("RGBA", (1, size))
    for y in range(size):
        t = y / max(1, size - 1)
        grad.putpixel((0, y), tuple(
            int(BRAND_TOP[i] + (BRAND_BOTTOM[i] - BRAND_TOP[i]) * t) for i in range(4)))
    grad = grad.resize((size, size))

    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    if circle:
        draw.ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out


def draw_portal_mark(layer, box, scale=1.0):
    """흰색 상자 + 잎 (경덕농수산)."""
    x0, y0, x1, y1 = box
    s = min(x1 - x0, y1 - y0) * scale
    cx = (x0 + x1) / 2
    cy = (y0 + y1) / 2

    def px(u):
        return cx + (u - 0.5) * s

    def py(v):
        return cy + (v - 0.5) * s

    draw = ImageDraw.Draw(layer)

    # 뚜껑
    draw.rounded_rectangle([px(0.08), py(0.30), px(0.92), py(0.45)],
                           radius=s * 0.045, fill=WHITE)
    # 몸통
    draw.rounded_rectangle([px(0.14), py(0.45), px(0.86), py(0.90)],
                           radius=s * 0.05, fill=WHITE)
    # 세로 칸막이 두 줄
    for u in (0.38, 0.62):
        draw.rounded_rectangle([px(u - 0.018), py(0.47), px(u + 0.018), py(0.875)],
                               radius=s * 0.018, fill=BRAND)

    # 줄기
    draw.line([(px(0.45), py(0.32)), (px(0.45), py(0.16))],
              fill=WHITE, width=max(2, int(s * 0.032)))
    # 잎 (줄기 끝에서 오른쪽 위로 뻗은 타원)
    leaf_w, leaf_h = s * 0.42, s * 0.23
    leaf = Image.new("RGBA", (int(leaf_w), int(leaf_h)), (0, 0, 0, 0))
    ImageDraw.Draw(leaf).ellipse([0, 0, leaf.size[0] - 1, leaf.size[1] - 1], fill=WHITE)
    leaf = leaf.rotate(30, resample=Image.BICUBIC, expand=False)
    layer.alpha_composite(leaf, (int(px(0.41)), int(py(0.035))))


def draw_worklog_mark(layer, box, scale=1.0):
    """흰색 클립보드 + 초록 줄 (업무일지)."""
    x0, y0, x1, y1 = box
    s = min(x1 - x0, y1 - y0) * scale
    cx = (x0 + x1) / 2
    cy = (y0 + y1) / 2

    def px(u):
        return cx + (u - 0.5) * s

    def py(v):
        return cy + (v - 0.5) * s

    draw = ImageDraw.Draw(layer)

    # 종이
    draw.rounded_rectangle([px(0.22), py(0.18), px(0.78), py(0.90)],
                           radius=s * 0.07, fill=WHITE)
    # 상단 집게
    draw.rounded_rectangle([px(0.36), py(0.10), px(0.64), py(0.25)],
                           radius=s * 0.05, fill=BRAND)
    # 글줄
    for i, (a, b) in enumerate([(0.32, 0.68), (0.32, 0.68), (0.32, 0.54)]):
        v = 0.40 + i * 0.145
        draw.rounded_rectangle([px(a), py(v), px(b), py(v + 0.045)],
                               radius=s * 0.022, fill=BRAND)
    # 체크 표시
    draw.line([(px(0.40), py(0.79)), (px(0.47), py(0.85)), (px(0.64), py(0.67))],
              fill=BRAND, width=max(2, int(s * 0.045)), joint="curve")


def make_legacy(mark_fn, target, circle=False):
    big = target * SS
    img = rounded_gradient(big, 0.18, circle=circle)
    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    pad = big * 0.17
    mark_fn(layer, (pad, pad, big - pad, big - pad), scale=1.0)
    img.alpha_composite(layer)
    return img.resize((target, target), Image.LANCZOS)


def make_foreground(mark_fn, target):
    """적응형 아이콘 foreground: 투명 배경, 가운데 안전 영역(66/108)에 그림."""
    big = target * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    safe = big * (66.0 / 108.0)
    pad = (big - safe) / 2
    mark_fn(img, (pad, pad, big - pad, big - pad), scale=0.94)
    return img.resize((target, target), Image.LANCZOS)


def write_set(base_res, mark_fn):
    for density, size in LEGACY_SIZES.items():
        folder = f"{base_res}/mipmap-{density}"
        os.makedirs(folder, exist_ok=True)
        make_legacy(mark_fn, size, circle=False).save(f"{folder}/ic_launcher.png")
        make_legacy(mark_fn, size, circle=True).save(f"{folder}/ic_launcher_round.png")
    for density, size in FOREGROUND_SIZES.items():
        folder = f"{base_res}/mipmap-{density}"
        os.makedirs(folder, exist_ok=True)
        make_foreground(mark_fn, size).save(f"{folder}/ic_launcher_foreground.png")


def main():
    write_set(f"{ROOT}/app/src/main/res", draw_portal_mark)
    write_set(f"{ROOT}/app/src/worklog/res", draw_worklog_mark)

    # 확인용 미리보기 (앱 리소스 아님)
    preview = Image.new("RGBA", (760, 260), (241, 244, 242, 255))
    tiles = [
        make_legacy(draw_portal_mark, 320),
        make_legacy(draw_portal_mark, 320, circle=True),
        make_legacy(draw_worklog_mark, 320),
        make_legacy(draw_worklog_mark, 320, circle=True),
    ]
    for i, tile in enumerate(tiles):
        preview.alpha_composite(tile.resize((160, 160), Image.LANCZOS), (40 + i * 180, 50))
    preview.save(f"{ROOT}/icon-preview.png")
    print("done:", ROOT)


if __name__ == "__main__":
    main()
