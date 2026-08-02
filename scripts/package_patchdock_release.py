#!/usr/bin/env python3
"""Package PatchDock's APK, source snapshot, and complete brand kit."""

from __future__ import annotations

import argparse
import hashlib
import shutil
import zipfile
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.2.2"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / f"patchdock-manager-{VERSION}-release.apk"
BRAND = ROOT / "assets" / "brand"
EXPECTED_APK_SHA256 = "71E6C90F596E139C7673F70FE40D9F4CF117034EB0109434409404B12F11CC20"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def zip_write(archive: zipfile.ZipFile, source: Path, destination: str) -> None:
    if not source.is_file():
        raise FileNotFoundError(source)
    archive.write(source, PurePosixPath(destination).as_posix())


def source_files() -> list[Path]:
    excluded_directories = {".git", ".gradle", ".idea", "build", "__pycache__", ".pytest_cache"}
    excluded_files = {
        PurePosixPath("local.properties"),
        PurePosixPath("assets/brand/patchdock-imagegen-concept.png"),
    }
    excluded_suffixes = {".apk", ".jks", ".keystore", ".tmp"}
    files: list[Path] = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT)
        posix = PurePosixPath(relative.as_posix())
        if any(part in excluded_directories for part in posix.parts):
            continue
        if posix in excluded_files or path.suffix.lower() in excluded_suffixes:
            continue
        files.append(path)
    return sorted(files, key=lambda value: value.relative_to(ROOT).as_posix().lower())


def build_source_archive(output: Path) -> None:
    prefix = f"PatchDock-{VERSION}-source"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for source in source_files():
            destination = f"{prefix}/{source.relative_to(ROOT).as_posix()}"
            zip_write(archive, source, destination)

    with zipfile.ZipFile(output) as archive:
        invalid = [
            name
            for name in archive.namelist()
            if "/.git/" in name
            or "/build/" in name
            or name.endswith("/local.properties")
            or name.lower().endswith((".apk", ".jks", ".keystore", ".tmp"))
            or name.endswith("assets/brand/patchdock-imagegen-concept.png")
        ]
    if invalid:
        raise RuntimeError("Source archive contains excluded paths: " + ", ".join(invalid[:10]))


def build_brand_kit(output: Path) -> None:
    prefix = f"PatchDock-Brand-Kit-{VERSION}"
    brand_files: list[tuple[Path, str]] = [
        (BRAND / "README.md", "README.md"),
        (BRAND / "VERIFICATION.md", "VERIFICATION.md"),
        (BRAND / "ANDROID-INTEGRATION.md", "ANDROID-INTEGRATION.md"),
        (ROOT / "LICENSE", "LICENSE"),
        (ROOT / "NOTICE", "NOTICE"),
        (ROOT / "scripts" / "build_brand_assets.py", "tools/build_brand_assets.py"),
        (ROOT / "scripts" / "build_brand_verification.py", "tools/build_brand_verification.py"),
    ]

    for path in sorted(BRAND.glob("patchdock-*")):
        if not path.is_file() or path.name == "patchdock-imagegen-concept.png":
            continue
        if "play-store" in path.name:
            destination = f"google-play/{path.name}"
        elif path.name in {"patchdock-brand-qa.png", "patchdock-brand-qa.svg", "patchdock-safe-zone.png", "patchdock-safe-zone.svg", "patchdock-system-surfaces.png"}:
            destination = f"qa/{path.name}"
        else:
            destination = f"masters/{path.name}"
        brand_files.append((path, destination))

    for path in sorted((BRAND / "concepts").glob("*")):
        if path.is_file():
            brand_files.append((path, f"concepts/{path.name}"))
    for path in sorted((BRAND / "verification").glob("*")):
        if path.is_file():
            brand_files.append((path, f"verification/{path.name}"))

    drawable_names = [
        "ic_launcher_background.xml",
        "ic_launcher_background_dark_1.xml",
        "ic_launcher_background_dark_2.xml",
        "ic_launcher_background_dark_3.xml",
        "ic_launcher_background_light_2.xml",
        "ic_launcher_background_light_3.xml",
        "ic_launcher_foreground.xml",
        "ic_launcher_foreground_dark.xml",
        "ic_launcher_monochrome.xml",
        "ic_mpp.xml",
        "ic_notification.xml",
    ]
    for name in drawable_names:
        brand_files.append((ROOT / "app" / "src" / "main" / "res" / "drawable" / name, f"android-res/drawable/{name}"))
    for path in sorted((ROOT / "app" / "src" / "main" / "res" / "mipmap-anydpi-v26").glob("ic_launcher*.xml")):
        brand_files.append((path, f"android-res/mipmap-anydpi-v26/{path.name}"))

    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for source, destination in brand_files:
            zip_write(archive, source, f"{prefix}/{destination}")

    required = {
        f"{prefix}/masters/patchdock-mark.svg",
        f"{prefix}/masters/patchdock-mark-1024.png",
        f"{prefix}/masters/patchdock-lockup.svg",
        f"{prefix}/google-play/patchdock-play-store-512.png",
        f"{prefix}/qa/patchdock-brand-qa.png",
        f"{prefix}/qa/patchdock-system-surfaces.png",
        f"{prefix}/android-res/drawable/ic_launcher_foreground.xml",
        f"{prefix}/android-res/drawable/ic_launcher_background.xml",
        f"{prefix}/android-res/drawable/ic_launcher_monochrome.xml",
        f"{prefix}/android-res/mipmap-anydpi-v26/ic_launcher.xml",
    }
    with zipfile.ZipFile(output) as archive:
        present = set(archive.namelist())
    missing = required - present
    if missing:
        raise RuntimeError("Brand kit is incomplete: " + ", ".join(sorted(missing)))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=ROOT.parent.parent / "outputs",
        help="Directory for user-facing release artifacts",
    )
    args = parser.parse_args()
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    if not APK.is_file():
        raise FileNotFoundError(APK)
    actual_apk_hash = sha256(APK)
    if actual_apk_hash != EXPECTED_APK_SHA256:
        raise RuntimeError(f"Unexpected release APK SHA-256: {actual_apk_hash}")

    direct_outputs = {
        f"PatchDock-{VERSION}.apk": APK,
        f"PatchDock-{VERSION}-verification.md": BRAND / "VERIFICATION.md",
        "PatchDock-icon.svg": BRAND / "patchdock-mark.svg",
        "PatchDock-icon-1024.png": BRAND / "patchdock-mark-1024.png",
        "PatchDock-icon-monochrome.svg": BRAND / "patchdock-mark-monochrome.svg",
        "PatchDock-logo.svg": BRAND / "patchdock-lockup.svg",
        "PatchDock-logo.png": BRAND / "patchdock-lockup.png",
        "PatchDock-logo-dark.svg": BRAND / "patchdock-lockup-dark.svg",
        "PatchDock-logo-dark.png": BRAND / "patchdock-lockup-dark.png",
        "PatchDock-logo-monochrome.svg": BRAND / "patchdock-lockup-monochrome.svg",
        "PatchDock-logo-reversed.svg": BRAND / "patchdock-lockup-reversed.svg",
        "PatchDock-Google-Play-512.png": BRAND / "patchdock-play-store-512.png",
        "PatchDock-brand-QA.png": BRAND / "patchdock-brand-qa.png",
        "PatchDock-Android-system-surfaces.png": BRAND / "patchdock-system-surfaces.png",
    }
    for destination, source in direct_outputs.items():
        if not source.is_file():
            raise FileNotFoundError(source)
        shutil.copy2(source, output_dir / destination)

    source_archive = output_dir / f"PatchDock-{VERSION}-source.zip"
    brand_archive = output_dir / f"PatchDock-{VERSION}-brand-kit.zip"
    build_source_archive(source_archive)
    build_brand_kit(brand_archive)

    delivered = [output_dir / name for name in direct_outputs]
    delivered.extend((source_archive, brand_archive))
    checksum_path = output_dir / f"PatchDock-{VERSION}-SHA256SUMS.txt"
    checksum_lines = [f"{sha256(path)} *{path.name}" for path in sorted(delivered, key=lambda value: value.name.lower())]
    checksum_path.write_text("\n".join(checksum_lines) + "\n", encoding="ascii", newline="\n")

    for path in sorted((*delivered, checksum_path), key=lambda value: value.name.lower()):
        print(f"{path.name}\t{path.stat().st_size}\t{sha256(path)}")


if __name__ == "__main__":
    main()
