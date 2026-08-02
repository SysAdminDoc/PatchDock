#!/usr/bin/env python3
"""Build PatchDock's real Android system-surface verification sheet."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "assets" / "brand"
VERIFY = BRAND / "verification"
OUTPUT = BRAND / "patchdock-system-surfaces.png"

INK = "#102A43"
MUTED = "#52687A"
MIST = "#F4F7FB"
BLUE = "#005FAC"
ORANGE = "#FF6B35"


def font(size: int, semibold: bool = False) -> ImageFont.FreeTypeFont:
    windows_font = Path("C:/Windows/Fonts/seguisb.ttf" if semibold else "C:/Windows/Fonts/segoeui.ttf")
    fallback = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if semibold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    return ImageFont.truetype(str(windows_font if windows_font.exists() else fallback), size=size)


def contain(image: Image.Image, width: int, height: int) -> Image.Image:
    copy = image.convert("RGB")
    copy.thumbnail((width, height), Image.Resampling.LANCZOS)
    return copy


def main() -> None:
    sources = [
        ("Launcher", "Adaptive icon beside installed apps", VERIFY / "launcher-app-drawer.png"),
        ("Recents", "System task-card identity", VERIFY / "recents.png"),
        ("App Info", "Settings package identity", VERIFY / "app-info.png"),
    ]
    missing = [str(path) for _, _, path in sources if not path.exists()]
    if missing:
        raise FileNotFoundError("Missing verification capture(s): " + ", ".join(missing))

    canvas = Image.new("RGB", (1600, 1120), MIST)
    draw = ImageDraw.Draw(canvas)

    mark = Image.open(BRAND / "patchdock-mark-512.png").convert("RGBA")
    mark.thumbnail((104, 104), Image.Resampling.LANCZOS)
    canvas.paste(mark, (58, 53), mark)

    draw.text((184, 55), "PatchDock", fill=INK, font=font(52, semibold=True))
    draw.text((184, 122), "Android system-surface verification", fill=BLUE, font=font(28, semibold=True))
    draw.text(
        (1536, 70),
        "API 36 · light mode",
        fill=MUTED,
        font=font(22),
        anchor="ra",
    )
    draw.text(
        (1536, 106),
        "Pixel launcher · isolated emulator",
        fill=MUTED,
        font=font(22),
        anchor="ra",
    )

    card_top = 230
    card_width = 450
    card_height = 790
    gap = 58
    start_x = 67
    screen_width = 330
    screen_height = 734

    for index, (title, subtitle, path) in enumerate(sources):
        x = start_x + index * (card_width + gap)
        draw.rounded_rectangle(
            (x + 8, card_top + 10, x + card_width + 8, card_top + card_height + 10),
            radius=36,
            fill="#DCE4EC",
        )
        draw.rounded_rectangle(
            (x, card_top, x + card_width, card_top + card_height),
            radius=36,
            fill="#FFFFFF",
        )
        draw.text((x + 32, card_top + 28), title, fill=INK, font=font(30, semibold=True))
        draw.text((x + 32, card_top + 72), subtitle, fill=MUTED, font=font(19))

        screenshot = contain(Image.open(path), screen_width, screen_height - 124)
        screen_x = x + (card_width - screenshot.width) // 2
        screen_y = card_top + 130
        draw.rounded_rectangle(
            (screen_x - 5, screen_y - 5, screen_x + screenshot.width + 5, screen_y + screenshot.height + 5),
            radius=22,
            fill="#CFD9E3",
        )
        canvas.paste(screenshot, (screen_x, screen_y))

    draw.text(
        (800, 1072),
        "Real launcher, task switcher, and Settings rendering · no mockups",
        fill=MUTED,
        font=font(22),
        anchor="mm",
    )
    canvas.save(OUTPUT, format="PNG", optimize=True)
    print(OUTPUT)


if __name__ == "__main__":
    main()
