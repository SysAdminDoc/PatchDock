#!/usr/bin/env python3
"""Build PatchDock's deterministic vector and raster brand kit.

The production artwork is deliberately generated from simple vector geometry.
Image-generation outputs in assets/brand/concepts are exploration references
only and are never shipped as launcher or store artwork.
"""

from __future__ import annotations

import html
import shutil
import subprocess
from pathlib import Path

import uharfbuzz as hb
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont


ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "assets" / "brand"
FONT_PATH = Path(r"C:\Windows\Fonts\seguisb.ttf")

BLUE = "#005FAC"
ORANGE = "#FF6B35"
INK = "#102A43"
MIST = "#F4F7FB"
NIGHT = "#071827"
NIGHT_BLUE = "#A4C9FF"
NIGHT_ORANGE = "#FF9B73"
NIGHT_INK = "#E6EFF7"

TOP_PATH = "M148 116H364L396 148V244H278L246 212H116V148Z"
BOTTOM_PATH = "M116 268H234L266 300H396V364L364 396H148L116 364Z"


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n", encoding="utf-8", newline="\n")


def mark_group(top: str, bottom: str, transform: str | None = None) -> str:
    transform_attr = f' transform="{transform}"' if transform else ""
    return (
        f'<g{transform_attr}>'
        f'<path fill="{top}" d="{TOP_PATH}"/>'
        f'<path fill="{bottom}" d="{BOTTOM_PATH}"/>'
        "</g>"
    )


def svg_document(body: str, view_box: str, title: str, desc: str, *, width: int | None = None, height: int | None = None) -> str:
    dimensions = ""
    if width is not None and height is not None:
        dimensions = f' width="{width}" height="{height}"'
    body = body.strip()
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view_box}"{dimensions} role="img" aria-labelledby="title desc">
  <title id="title">{html.escape(title)}</title>
  <desc id="desc">{html.escape(desc)}</desc>
  {body}
</svg>'''


def shaped_glyphs(text: str, font_size: float, x: float, baseline: float, *, dark: bool = False) -> tuple[str, float]:
    font_data = FONT_PATH.read_bytes()
    hb_face = hb.Face(font_data)
    hb_font = hb.Font(hb_face)
    hb_font.scale = (hb_face.upem, hb_face.upem)
    buffer = hb.Buffer()
    buffer.add_str(text)
    buffer.guess_segment_properties()
    hb.shape(hb_font, buffer, {"kern": True})

    tt_font = TTFont(FONT_PATH)
    glyph_set = tt_font.getGlyphSet()
    scale = font_size / hb_face.upem
    cursor = 0.0
    paths: list[str] = []

    for info, position in zip(buffer.glyph_infos, buffer.glyph_positions, strict=True):
        glyph_name = tt_font.getGlyphName(info.codepoint)
        pen = SVGPathPen(glyph_set)
        glyph_set[glyph_name].draw(pen)
        d = pen.getCommands()
        glyph_x = x + (cursor + position.x_offset) * scale
        glyph_y = baseline - position.y_offset * scale
        if dark:
            fill = NIGHT_INK if info.cluster < 5 else NIGHT_BLUE
        else:
            fill = INK if info.cluster < 5 else BLUE
        paths.append(
            f'<path fill="{fill}" d="{d}" transform="translate({glyph_x:.3f} {glyph_y:.3f}) scale({scale:.7f} {-scale:.7f})"/>'
        )
        cursor += position.x_advance

    tt_font.close()
    return "".join(paths), cursor * scale


def render_svg(source: Path, destination: Path, *, size: str | None = None) -> None:
    magick = shutil.which("magick")
    if not magick:
        raise RuntimeError("ImageMagick 'magick' executable is required")
    command = [magick, "-background", "none", str(source)]
    if size:
        command.extend(["-resize", size])
    destination_spec = f"PNG32:{destination}" if destination.suffix.lower() == ".png" else str(destination)
    command.extend(["-colorspace", "sRGB", "-strip", destination_spec])
    subprocess.run(command, check=True)


def build_core_assets() -> None:
    write(
        BRAND / "patchdock-mark.svg",
        svg_document(
            mark_group(BLUE, ORANGE),
            "0 0 512 512",
            "PatchDock splice-joint mark",
            "Two chamfered plates joined by a precise offset splice channel.",
        ),
    )
    write(
        BRAND / "patchdock-mark-dark.svg",
        svg_document(
            mark_group(NIGHT_BLUE, NIGHT_ORANGE),
            "0 0 512 512",
            "PatchDock splice-joint mark for dark backgrounds",
            "The high-contrast dark-background rendition of the PatchDock mark.",
        ),
    )
    write(
        BRAND / "patchdock-mark-monochrome.svg",
        svg_document(
            mark_group("#000000", "#000000"),
            "0 0 512 512",
            "PatchDock monochrome splice-joint mark",
            "The one-color PatchDock symbol for themed icons and single-ink use.",
        ),
    )

    play_body = f'<rect width="512" height="512" fill="{MIST}"/>{mark_group(BLUE, ORANGE)}'
    write(
        BRAND / "patchdock-play-store.svg",
        svg_document(
            play_body,
            "0 0 512 512",
            "PatchDock Google Play icon",
            "Opaque full-square Google Play artwork without baked-in rounding or shadow.",
            width=512,
            height=512,
        ),
    )

    editable_wordmark = f'''<text x="20" y="174" font-family="Segoe UI Semibold, Segoe UI, sans-serif" font-size="160" font-weight="600" letter-spacing="-3"><tspan fill="{INK}">Patch</tspan><tspan fill="{BLUE}">Dock</tspan></text>'''
    write(
        BRAND / "patchdock-wordmark-editable.svg",
        svg_document(
            editable_wordmark,
            "0 0 820 220",
            "PatchDock editable wordmark",
            "Editable text source for the PatchDock wordmark, set in Segoe UI Semibold.",
        ),
    )

    wordmark_paths, wordmark_width = shaped_glyphs("PatchDock", 160, 20, 174)
    wordmark_view_width = int(wordmark_width + 40)
    write(
        BRAND / "patchdock-wordmark.svg",
        svg_document(
            wordmark_paths,
            f"0 0 {wordmark_view_width} 220",
            "PatchDock outlined wordmark",
            "Portable outlined PatchDock wordmark with no font dependency.",
        ),
    )

    lockup_paths, lockup_width = shaped_glyphs("PatchDock", 148, 338, 208)
    lockup_width_total = int(338 + lockup_width + 34)
    lockup_body = mark_group(BLUE, ORANGE, "translate(-9 -64) scale(.75)") + lockup_paths
    write(
        BRAND / "patchdock-lockup.svg",
        svg_document(
            lockup_body,
            f"0 0 {lockup_width_total} 320",
            "PatchDock horizontal lockup",
            "The PatchDock splice-joint symbol and outlined wordmark.",
        ),
    )

    dark_lockup_paths, dark_lockup_width = shaped_glyphs("PatchDock", 148, 338, 208, dark=True)
    dark_lockup_width_total = int(338 + dark_lockup_width + 34)
    dark_lockup_body = mark_group(NIGHT_BLUE, NIGHT_ORANGE, "translate(-9 -64) scale(.75)") + dark_lockup_paths
    write(
        BRAND / "patchdock-lockup-dark.svg",
        svg_document(
            dark_lockup_body,
            f"0 0 {dark_lockup_width_total} 320",
            "PatchDock horizontal lockup for dark backgrounds",
            "The high-contrast PatchDock symbol and outlined wordmark.",
        ),
    )

    mono_paths, mono_width = shaped_glyphs("PatchDock", 148, 338, 208)
    mono_paths = mono_paths.replace(INK, "#000000").replace(BLUE, "#000000")
    mono_width_total = int(338 + mono_width + 34)
    mono_body = mark_group("#000000", "#000000", "translate(-9 -64) scale(.75)") + mono_paths
    write(
        BRAND / "patchdock-lockup-monochrome.svg",
        svg_document(
            mono_body,
            f"0 0 {mono_width_total} 320",
            "PatchDock monochrome horizontal lockup",
            "The one-color PatchDock symbol and outlined wordmark.",
        ),
    )

    reversed_paths = mono_paths.replace("#000000", "#FFFFFF")
    reversed_body = mark_group("#FFFFFF", "#FFFFFF", "translate(-9 -64) scale(.75)") + reversed_paths
    write(
        BRAND / "patchdock-lockup-reversed.svg",
        svg_document(
            reversed_body,
            f"0 0 {mono_width_total} 320",
            "PatchDock reversed horizontal lockup",
            "The all-white PatchDock symbol and outlined wordmark for dark backgrounds.",
        ),
    )

    editable_lockup = mark_group(BLUE, ORANGE, "translate(-9 -64) scale(.75)") + f'''<text x="338" y="208" font-family="Segoe UI Semibold, Segoe UI, sans-serif" font-size="148" font-weight="600" letter-spacing="-2.5"><tspan fill="{INK}">Patch</tspan><tspan fill="{BLUE}">Dock</tspan></text>'''
    write(
        BRAND / "patchdock-lockup-editable.svg",
        svg_document(
            editable_lockup,
            f"0 0 {lockup_width_total} 320",
            "PatchDock editable horizontal lockup",
            "Editable text source for the PatchDock horizontal logo.",
        ),
    )


def build_safe_zone_asset() -> None:
    body = f'''
<rect width="512" height="512" fill="#FFFFFF"/>
<rect x="100" y="100" width="312" height="312" rx="2" fill="none" stroke="#00A878" stroke-width="4" stroke-dasharray="12 10"/>
<rect x="116" y="116" width="280" height="280" fill="none" stroke="#005FAC" stroke-width="3"/>
{mark_group(BLUE, ORANGE)}
<text x="256" y="56" text-anchor="middle" fill="{INK}" font-family="Segoe UI, sans-serif" font-size="24" font-weight="600">108 dp adaptive canvas</text>
<text x="256" y="454" text-anchor="middle" fill="#51677A" font-family="Segoe UI, sans-serif" font-size="19">green: 66 dp safe zone · blue: 59 dp mark bounds</text>
'''
    write(
        BRAND / "patchdock-safe-zone.svg",
        svg_document(
            body,
            "0 0 512 512",
            "PatchDock adaptive-icon safe-zone proof",
            "The 59 dp mark bounds remain inside Android's 66 dp non-clipping safe zone.",
            width=512,
            height=512,
        ),
    )


def mask_icon(x: int, y: int, shape: str, label: str, *, bg: str = MIST, top: str = BLUE, bottom: str = ORANGE) -> str:
    clip_id = f"clip-{label.lower().replace(' ', '-')}"
    if shape == "circle":
        clip = f'<circle cx="{x + 90}" cy="{y + 90}" r="90"/>'
    elif shape == "squircle":
        clip = f'<path d="M{x+90} {y}C{x+154} {y} {x+180} {y+26} {x+180} {y+90}S{x+154} {y+180} {x+90} {y+180}S{x} {y+154} {x} {y+90}S{x+26} {y} {x+90} {y}Z"/>'
    elif shape == "rounded":
        clip = f'<rect x="{x}" y="{y}" width="180" height="180" rx="40"/>'
    elif shape == "teardrop":
        clip = f'<path d="M{x+90} {y}C{x+145} {y} {x+180} {y+38} {x+180} {y+93}V{y+180}H{x+87}C{x+34} {y+180} {x} {y+145} {x} {y+90}C{x} {y+36} {x+36} {y} {x+90} {y}Z"/>'
    else:
        clip = f'<rect x="{x}" y="{y}" width="180" height="180"/>'
    scale = 180 / 512
    return f'''
<defs><clipPath id="{clip_id}">{clip}</clipPath></defs>
<g clip-path="url(#{clip_id})">
  <rect x="{x}" y="{y}" width="180" height="180" fill="{bg}"/>
  {mark_group(top, bottom, f"translate({x} {y}) scale({scale:.8f})")}
</g>
<text x="{x+90}" y="{y+211}" text-anchor="middle" fill="{INK}" font-family="Segoe UI, sans-serif" font-size="18" font-weight="600">{label}</text>
'''


def build_qa_sheet() -> None:
    masks = "".join(
        [
            mask_icon(70, 150, "circle", "Circle"),
            mask_icon(300, 150, "squircle", "Squircle"),
            mask_icon(530, 150, "rounded", "Rounded square"),
            mask_icon(760, 150, "teardrop", "Teardrop"),
            mask_icon(990, 150, "square", "Square"),
            mask_icon(1220, 150, "square", "Full bleed"),
        ]
    )

    theme_specs = [
        (70, MIST, BLUE, ORANGE, "Color / light"),
        (300, NIGHT, NIGHT_BLUE, NIGHT_ORANGE, "Color / dark"),
        (530, "#EFEFEF", "#383838", "#777777", "Grayscale"),
        (760, "#D7E9FF", "#0052A5", "#0052A5", "Themed / light"),
        (990, "#FFFFFF", "#111111", "#111111", "Monochrome"),
        (1220, "#153153", "#B8D8FF", "#B8D8FF", "Themed / dark"),
    ]
    themes = "".join(mask_icon(x, 470, "squircle", label, bg=bg, top=top, bottom=bottom) for x, bg, top, bottom, label in theme_specs)

    small_sizes = []
    cursor_x = 70
    for size in [128, 64, 48, 32, 24]:
        scale = size / 512
        small_sizes.append(f'<g transform="translate({cursor_x} 820)"><rect width="{size}" height="{size}" fill="{MIST}" rx="{max(4, size*0.22):.1f}"/>{mark_group(BLUE, ORANGE, f"scale({scale:.8f})")}<text x="{size/2:.1f}" y="{size+30}" text-anchor="middle" fill="{INK}" font-family="Segoe UI, sans-serif" font-size="16">{size}px</text></g>')
        cursor_x += size + 70

    body = f'''
<rect width="1510" height="1060" fill="#FFFFFF"/>
<text x="70" y="64" fill="{INK}" font-family="Segoe UI, sans-serif" font-size="38" font-weight="700">PatchDock icon QA</text>
<text x="70" y="100" fill="#51677A" font-family="Segoe UI, sans-serif" font-size="20">59 dp mark · 66 dp guaranteed safe zone · flat two-shape system</text>
<text x="70" y="135" fill="{INK}" font-family="Segoe UI, sans-serif" font-size="20" font-weight="600">Adaptive masks</text>
{masks}
<text x="70" y="455" fill="{INK}" font-family="Segoe UI, sans-serif" font-size="20" font-weight="600">Color, grayscale, and themed-icon behavior</text>
{themes}
<text x="70" y="795" fill="{INK}" font-family="Segoe UI, sans-serif" font-size="20" font-weight="600">Raster-size legibility</text>
{''.join(small_sizes)}
<line x1="70" y1="1006" x2="1440" y2="1006" stroke="#D9E4EE" stroke-width="2"/>
<text x="70" y="1038" fill="#51677A" font-family="Segoe UI, sans-serif" font-size="17">Production geometry rendered directly from the same two paths used by Android resources.</text>
'''
    write(
        BRAND / "patchdock-brand-qa.svg",
        svg_document(
            body,
            "0 0 1510 1060",
            "PatchDock icon quality-assurance sheet",
            "Mask, theme, grayscale, and tiny-size checks for the PatchDock adaptive icon.",
            width=1510,
            height=1060,
        ),
    )


def build_rasters() -> None:
    render_svg(BRAND / "patchdock-mark.svg", BRAND / "patchdock-mark-1024.png", size="1024x1024")
    render_svg(BRAND / "patchdock-mark.svg", BRAND / "patchdock-mark-512.png", size="512x512")
    render_svg(BRAND / "patchdock-wordmark.svg", BRAND / "patchdock-wordmark.png", size="1600x")
    render_svg(BRAND / "patchdock-lockup.svg", BRAND / "patchdock-lockup.png", size="1800x")
    render_svg(BRAND / "patchdock-lockup-dark.svg", BRAND / "patchdock-lockup-dark.png", size="1800x")
    render_svg(BRAND / "patchdock-lockup-monochrome.svg", BRAND / "patchdock-lockup-monochrome.png", size="1800x")
    render_svg(BRAND / "patchdock-lockup-reversed.svg", BRAND / "patchdock-lockup-reversed.png", size="1800x")
    render_svg(BRAND / "patchdock-play-store.svg", BRAND / "patchdock-play-store-512.png")
    render_svg(BRAND / "patchdock-safe-zone.svg", BRAND / "patchdock-safe-zone.png")
    render_svg(BRAND / "patchdock-brand-qa.svg", BRAND / "patchdock-brand-qa.png")


def main() -> None:
    if not FONT_PATH.exists():
        raise FileNotFoundError(f"Required wordmark source font not found: {FONT_PATH}")
    BRAND.mkdir(parents=True, exist_ok=True)
    build_core_assets()
    build_safe_zone_asset()
    build_qa_sheet()
    build_rasters()


if __name__ == "__main__":
    main()
