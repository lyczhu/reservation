"""生成商家端底部 tabBar 图标。

微信原生 tabBar 仅支持本地图片，故用几何图形绘制 81×81 透明 PNG，
常态为描边、选中为填充，颜色与 pages.json 中 tabBar 的 color / selectedColor 一致。
换品牌色或尺寸时修改下方常量后重新执行：

    python frontend/scripts/gen-tabbar-icons.py
"""

from pathlib import Path

from PIL import Image, ImageColor, ImageDraw

SIZE = 81  # 微信推荐的 tabBar 图标尺寸
SUPERSAMPLE = 4  # 超采样倍数，缩小后用于抗锯齿
NORMAL_COLOR = "#8C827C"  # 对应 tabBar.color
ACTIVE_COLOR = "#C9707C"  # 对应 tabBar.selectedColor
OUT_DIR = Path(__file__).resolve().parents[1] / "apps/merchant/src/static/tabbar"

S = SUPERSAMPLE
CANVAS = SIZE * S
STROKE = 3  # 常态描边线宽（逻辑像素）


def px(value: float) -> float:
    return value * S


def stroke(draw: ImageDraw.ImageDraw, points, width: float, color) -> None:
    """带圆头圆角的折线。"""
    pts = [(px(x), px(y)) for x, y in points]
    draw.line(pts, fill=color, width=max(1, round(px(width))), joint="curve")
    r = px(width) / 2
    for x, y in pts:
        draw.ellipse((x - r, y - r, x + r, y + r), fill=color)


def box(draw: ImageDraw.ImageDraw, bounds, radius: float, color, filled: bool, width: float = STROKE) -> None:
    area = tuple(px(v) for v in bounds)
    if filled:
        draw.rounded_rectangle(area, radius=px(radius), fill=color)
    else:
        draw.rounded_rectangle(area, radius=px(radius), outline=color, width=max(1, round(px(width))))


def draw_dashboard(draw, color, filled: bool) -> None:
    span, cell, radius = 39.0, 17.0, 3.5
    start = (SIZE - span) / 2
    gap = span - cell * 2
    for i in (0, 1):
        for j in (0, 1):
            x = start + i * (cell + gap)
            y = start + j * (cell + gap)
            box(draw, (x, y, x + cell, y + cell), radius, color, filled)


def draw_reservation(draw, color, filled: bool) -> None:
    body = (22.5, 25, 58.5, 62)
    box(draw, body, 5.5, color, filled)
    for ring in ((31, 19.5, 35, 28.5), (46, 19.5, 50, 28.5)):
        box(draw, ring, 2, color, True)
    # 常态分隔线两端收进描边内，避免圆头线帽外凸；选中态整幅挖空，保留填充剪影
    if filled:
        stroke(draw, [(22.5, 36), (58.5, 36)], STROKE, (0, 0, 0, 0))
    else:
        stroke(draw, [(24.5, 36), (56.5, 36)], STROKE, color)


def draw_verify(draw, color, filled: bool) -> None:
    left, top, right, bottom = 23, 23, 58, 58
    width = 4.4 if filled else STROKE
    arm = 11.5 if filled else 10.5
    stroke(draw, [(left, top + arm), (left, top), (left + arm, top)], width, color)
    stroke(draw, [(right - arm, top), (right, top), (right, top + arm)], width, color)
    stroke(draw, [(left, bottom - arm), (left, bottom), (left + arm, bottom)], width, color)
    stroke(draw, [(right - arm, bottom), (right, bottom), (right, bottom - arm)], width, color)
    if filled:
        box(draw, (28.5, 37.6, 52.5, 43.4), 3, color, True)
    else:
        stroke(draw, [(29, 40.5), (52, 40.5)], STROKE, color)


def draw_profile(draw, color, filled: bool) -> None:
    head = tuple(px(v) for v in (32, 24.5, 49, 41.5))
    shoulder = tuple(px(v) for v in (23.5, 44, 57.5, 78))
    if filled:
        draw.ellipse(head, fill=color)
        draw.pieslice(shoulder, 180, 360, fill=color)
    else:
        draw.ellipse(head, outline=color, width=round(px(STROKE)))
        draw.arc(shoulder, 180, 360, fill=color, width=round(px(STROKE)))


ICONS = {
    "dashboard": draw_dashboard,
    "reservation": draw_reservation,
    "verify": draw_verify,
    "profile": draw_profile,
}


def render(painter, color: str, filled: bool) -> Image.Image:
    image = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    painter(ImageDraw.Draw(image), ImageColor.getrgb(color) + (255,), filled)
    return image.resize((SIZE, SIZE), Image.LANCZOS)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, painter in ICONS.items():
        render(painter, NORMAL_COLOR, False).save(OUT_DIR / f"{name}.png")
        render(painter, ACTIVE_COLOR, True).save(OUT_DIR / f"{name}-active.png")
    print(f"已生成 {len(ICONS) * 2} 张图标 -> {OUT_DIR}")


if __name__ == "__main__":
    main()
