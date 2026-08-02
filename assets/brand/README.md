# PatchDock visual identity

PatchDock's symbol is a **precision splice joint**: two chamfered plates are separated by one deliberate offset channel. The upper blue plate represents a verified source; the lower orange plate represents the locally transformed result. The geometry communicates controlled modification without relying on a puzzle piece, download arrow, package box, gear, shield, or letter monogram.

## Production geometry

- Two filled paths; no strokes, gradients, shadows, masks, or effects.
- `512 × 512` master view box, mapped directly to Android's `108 × 108dp` adaptive-icon canvas.
- Essential bounds: `x=116…396`, `y=116…396` (`280/512 × 108 = 59.06dp`).
- Android's guaranteed central safe zone is `66 × 66dp`, so the mark retains about `3.47dp` of additional clearance on every side.
- The splice channel remains open at `24px` and remains unambiguous in a single color.

## Palette

| Role | Value | Use |
| --- | --- | --- |
| Dock blue | `#005FAC` | Primary plate; matches PatchDock's light-theme primary color |
| Patch orange | `#FF6B35` | Transformed-result plate and controlled accent |
| Dock ink | `#102A43` | Wordmark and high-contrast supporting typography |
| Mist | `#F4F7FB` | Default adaptive and Google Play background |
| Night | `#071827` | Dark icon background |
| Night blue | `#A4C9FF` | Dark-background primary plate |
| Night orange | `#FF9B73` | Dark-background accent plate |
| Night ink | `#E6EFF7` | Reversed wordmark |

## Master and export files

- `patchdock-mark.svg` — primary transparent symbol master
- `patchdock-mark-dark.svg` — high-contrast dark-background symbol
- `patchdock-mark-monochrome.svg` — purpose-built one-color silhouette
- `patchdock-mark-1024.png` / `patchdock-mark-512.png` — transparent raster exports
- `patchdock-play-store.svg` / `patchdock-play-store-512.png` — opaque full-square Play artwork; no baked mask or shadow
- `patchdock-wordmark-editable.svg` — editable Segoe UI Semibold text source
- `patchdock-wordmark.svg` — portable outlined wordmark
- `patchdock-lockup-editable.svg` — editable horizontal lockup source
- `patchdock-lockup.svg` / `.png` — primary outlined horizontal lockup
- `patchdock-lockup-dark.svg` / `.png` — full-color dark-background lockup
- `patchdock-lockup-monochrome.svg` / `.png` — single-color lockup
- `patchdock-lockup-reversed.svg` / `.png` — white reversed lockup
- `patchdock-safe-zone.svg` / `.png` — numerical adaptive-icon bounds proof
- `patchdock-brand-qa.svg` / `.png` — mask, theme, grayscale, and tiny-size checks
- `patchdock-system-surfaces.png` — real Pixel launcher, Recents, and App Info rendering proof
- `verification/` — the three unedited Android 16 screenshots used by the system-surface sheet
- `concepts/` — ImageGen exploration references and selection scorecard; never shipped as product artwork

The final wordmark uses Segoe UI Semibold from Windows as a design source. Portable SVGs contain glyph outlines and do not embed or redistribute the font. Editable SVGs retain live text for future controlled revisions.

## Usage

Keep clear space around a standalone symbol equal to at least one quarter of the visible mark width. Use the primary mark on light neutral surfaces, the dark variant on deep surfaces, and the monochrome asset when the platform applies its own tint. The Google Play asset must remain a full square; Play applies its own presentation mask and shadow.

Do not stretch, rotate, outline, rearrange, reconnect, or recolor individual plates outside this palette. Do not place the wordmark inside a launcher icon. Do not add a container, border, drop shadow, glow, texture, or third pictogram.

## Rebuilding

Run `python scripts/build_brand_assets.py` from the repository root. The script deterministically regenerates all production SVG and PNG exports from the same two path definitions. Android VectorDrawables use matching coordinates under `app/src/main/res/drawable/`.

After replacing any of the raw screenshots in `verification/`, run `python scripts/build_brand_verification.py` to rebuild the real-system contact sheet. Those screenshots were captured pointer-free on an isolated Android 16 (API 36) Pixel emulator; they are evidence, not composited launcher mockups.
