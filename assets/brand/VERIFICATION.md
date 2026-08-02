# PatchDock brand verification

Verification date: 2026-08-01

## Outcome

PatchDock now uses an original **precision splice joint** identity throughout the Android app and project materials. The symbol is made from two chamfered plates separated by one deliberate offset channel: dock blue represents the verified source and patch orange represents the locally transformed result.

The old puzzle-piece tile was replaced rather than preserved. The new mark does not rely on a letter, stock Material symbol, arrow, package box, puzzle piece, gradient, shadow, or launcher-shaped container.

## Official specification baseline

The implementation was checked against current primary Android documentation:

- [Android adaptive icon design](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive): separate foreground/background layers, optional monochrome layer, `108 × 108dp` layers, a guaranteed `66 × 66dp` safe zone, clean unmasked edges, and `48–66dp` logo sizing.
- [Android Studio app icon guidance](https://developer.android.com/studio/write/create-app-icons): adaptive launcher resources under `mipmap`, separate foreground/background/monochrome inputs, and previews for circle, squircle, rounded-square, square, and full-bleed presentation.
- [Android themed-icon requirement](https://developer.android.com/distribute/aep/aep-req-theme-app-icons): include a `<monochrome>` drawable in the adaptive-icon XML.
- [Google Play icon specification](https://developer.android.com/distribute/google-play/resources/icon-design-specifications): `512 × 512px`, 32-bit PNG, sRGB, no larger than 1024KB, full-square artwork, and no baked outer rounding or shadow.

## Concept selection

Three independent black-only concepts were generated for exploration, then scored before color was introduced. Precision splice scored `22/25`; convergence scored `18/25` but read as an arrow/USB fork; cut-and-rebind scored `17/25` but read as a chain link. The selected concept was manually redrawn as two deterministic SVG/VectorDrawable paths. Full prompts and the scorecard are retained in `concepts/README.md`.

Adjacent visual territory was checked against Morphe, ReVanced Manager, and Shizuku. PatchDock avoids Morphe's gradient M, ReVanced's ringed diamond/V, and Shizuku's cat/hexagon.

## Adaptive-icon geometry

| Check | Result |
| --- | --- |
| Layer canvas | `108 × 108dp` |
| Master view box | `512 × 512` |
| Essential master bounds | `x=116…396`, `y=116…396` |
| Android optical footprint | `59.06 × 59.06dp` |
| Guaranteed safe zone | `66 × 66dp` |
| Extra safe-zone clearance | about `3.47dp` per side |
| Important shapes | 2 filled paths |
| Purpose-built monochrome | Present in all six adaptive variants |
| Legacy density assets | Not needed; project `minSdk=26` |
| Separate round artwork | Not justified; adaptive masks render correctly |

The generated mask sheet checks circle, squircle, rounded square, square, teardrop, and full-bleed previews. It also checks full color, grayscale, monochrome, themed light, themed dark, light background, and dark background at `128`, `64`, `48`, `32`, and `24px`. No essential geometry clips, the splice remains open at `32px`, and the single-color silhouette remains identifiable.

## Google Play asset

`patchdock-play-store-512.png` was verified as:

- exactly `512 × 512px`;
- PNG IHDR bit depth 8 and color type 6 (RGBA/32-bit);
- sRGB (`gamma=0.454545`);
- fully opaque despite retaining 32-bit RGBA encoding;
- 2,948 bytes, below the 1,024KB limit;
- full-square Mist background with no pre-rounded corners or outer drop shadow.

## Android system-surface QA

The release icon was installed only on an isolated Android 16 / API 36 Pixel emulator. The emulator ran headlessly on a private non-input Windows desktop tied to the hardware-ID-verified fourth virtual monitor. Captures were made through Android system APIs; no mouse, keyboard, gesture, clipboard, foreground desktop, or physical-phone interaction was used.

The unedited screenshots in `verification/` prove the packaged adaptive icon in:

- Pixel Launcher app drawer beside representative system and Google apps;
- the real Android Recents task switcher;
- Android App Info in Settings.

`patchdock-system-surfaces.png` is a contact sheet of those real captures, not a launcher mockup. Optical size, center, contrast, and channel clarity are consistent with neighboring icons.

## Build and automated verification

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:connectedDebugAndroidTest -PsignAsDebug --no-daemon
```

Result: `BUILD SUCCESSFUL` in 1m 30s; 211 actionable tasks.

- 18 JVM unit tests passed, including three branding contract tests for safe-zone bounds, dedicated monochrome resources, and the exact Play PNG contract.
- 5 Android navigation tests passed on `PatchDockBrandQA(AVD) - 16`.
- `aapt2` reports package `app.patchdock.manager`, label `PatchDock`, version `0.2.2`, version code `29760568`, `minSdk=26`, and `targetSdk=37`.
- The release contains all six `mipmap/ic_launcher*` variants plus packaged foreground, dark foreground, monochrome, and six background resources.
- The splash icon resolves to the new launcher foreground.
- The release APK is 17,156,426 bytes with SHA-256 `71E6C90F596E139C7673F70FE40D9F4CF117034EB0109434409404B12F11CC20`.
- APK Signature Scheme v2 verification passes with the minimum throwaway Android debug identity required for installable development artifacts. No production or real-trust-chain code signing was performed.

Per the task boundary, the connected physical phone was neither updated nor used for visual testing; no app was launched there.

## Reproduction

```powershell
python scripts/build_brand_assets.py
python scripts/build_brand_verification.py
```

The first command deterministically rebuilds the SVG/PNG production set from the same two path definitions used by Android. The second rebuilds the system-surface contact sheet from the retained raw captures.
