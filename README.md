# PatchDock

<p align="center">
  <img src="assets/brand/patchdock-lockup.svg" alt="PatchDock" width="620" />
</p>

<p align="center">
  <a href="https://ko-fi.com/X8K126YVER">
    <img height="42" src="https://storage.ko-fi.com/cdn/kofi2.png?v=3" alt="Buy me a coffee on Ko-fi" />
  </a>
</p>

<p align="center">
  <sub><em>If this project helps you, a coffee helps me keep working on it.</em></sub>
</p>

PatchDock is an Android manager for finding, verifying, patching, signing, and installing supported stock apps without moving the workflow to a PC. It ships with the TikTok patches from [`icysymmetra/tiktok-patches-for-morphe`](https://github.com/icysymmetra/tiktok-patches-for-morphe) and the official [`MorpheApp/morphe-patches`](https://github.com/MorpheApp/morphe-patches) bundle.

PatchDock is a distinctly branded derivative of [Morphe Manager](https://github.com/MorpheApp/morphe-manager). It is not an official MorpheApp, Google, Reddit, TikTok, ByteDance, APKMirror, ReVanced, or Shizuku product.

## Supported apps

PatchDock 0.2.2 exposes every app target in its two built-in patch bundles:

| App | Android package | Input required by the current bundle |
|---|---|---|
| TikTok | `com.zhiliaoapp.musically` | Exact plain APK for 43.8.3 |
| YouTube | `com.google.android.youtube` | Plain APK |
| YouTube Music | `com.google.android.apps.youtube.music` | Plain APK |
| Reddit | `com.reddit.frontpage` | APKM/split archive preferred by the current bundle |

The compatible versions are read from the installed patch bundle instead of being duplicated in UI code. PatchDock also works with user-added Morphe patch bundles: a target automatically gains the secure in-app APKMirror flow when the bundle declares an exact version and at least one valid stock signing-certificate SHA-256 fingerprint.

Third-party bundles are never silently installed. Patch bundles execute modification code locally, and some popular community bundles do not publish stock-certificate fingerprints in their compatibility metadata. Those sources remain explicit user choices and fall back to manual file selection when PatchDock cannot establish a certificate-pinned download policy.

## What this build does

1. Loads the two built-in GitHub manifests and accepts only release hashes embedded in the manager.
2. Reads each bundle's package, compatible-version, required-file-type, and publisher-certificate metadata.
3. Opens either an exact result or a focused search inside an HTTPS APKMirror-only WebView.
4. Lets the user initiate APKMirror's normal download flow.
5. Verifies package name, exact version, required APK/split shape, and publisher certificate before patching. Every APK module in a split archive must agree on package/version and share a trusted certificate.
6. Applies the selected `.mpp` patches locally on the Android device.
7. Signs the result with PatchDock's private on-device keystore.
8. Installs through Shizuku by default, with the standard Android package installer available as a fallback.

PatchDock never uploads the APK or app data. Downloads are capped at 1.5 GB, redirects are re-checked after following them, and TLS errors are always cancelled rather than bypassed.

## Trust model

There are two artifact policies:

- **Exact artifact pin:** TikTok 43.8.3 is checked byte-for-byte, including version code, file length, full APK SHA-256, package shape, and publisher certificate.
- **Bundle-derived target:** apps with several legitimate APKMirror variants are checked against the exact package/version, bundle-required package shape, and all publisher certificates declared by the enabled patch bundle. File length and full-file hash are not pinned because valid architecture/DPI variants differ.

Built-in patch releases remain fail-closed. A newly published `.mpp` is not accepted until its version and SHA-256 are included in a reviewed PatchDock update.

### Current built-in pins

| Item | Pinned value |
|---|---|
| TikTok package/version | `com.zhiliaoapp.musically` 43.8.3 (`2024308030`) |
| TikTok stock APK SHA-256 | `ff30d4d43eb2e5764a6ea1cd022168811553052c3a37b5b65caba079ba026bb9` |
| TikTok stock certificate SHA-256 | `9041803e91bcb814b4b4399fb5c85a91640b755e5e8ba76813814bf4cf2ab5ba` |
| TikTok patches | 0.4.1 — `58510605b618b6b5fe7bf9ae2d284857a07aa7c359cea189dcfb09efc7bb147c` |
| Official Morphe patches | 1.38.0 — `31d088b81414c65b9294e33feb3d5d7f14de3c694d84d74388ca3b22afc331f0` |

The machine-readable values live in `PatchDockCatalog.kt` and are covered by unit tests.

## Building

Requirements:

- JDK 17
- Android SDK with API 37 and Build Tools
- the compatible `morphe-library` and fixed `morphe-patcher` composite builds next to this repository
- the Morphe `jadb` 1.2.3 artifact in the local Maven repository

For the exact dependency revisions and the reason for the patcher fork, see [`DEPENDENCIES.md`](DEPENDENCIES.md). Then run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease -PsignAsDebug
```

Android requires APKs to have a signing identity. Development artifacts use Gradle's throwaway debug key; no production code-signing certificate is used or included. A user's APK-patching keystore is separate and remains in the app's private storage.

## Updating a built-in source

Never update only a visible version or mutable manifest URL. Fetch the released `.mpp`, verify the release-asset SHA-256 independently, add the version/hash to `PatchDockCatalog.kt`, update tests, and exercise at least one declared app target. Stock-artifact pins must be updated as a single reviewed unit.

## License and origin

PatchDock is licensed under the [GNU General Public License v3.0](LICENSE), including the additional Section 7 terms carried by Morphe Manager. The original copyright headers, contributors, license texts, and [NOTICE](NOTICE) are retained. PatchDock uses a new name, application ID, colors, and icon and makes its derivative origin explicit.

Morphe Manager itself builds on [Universal ReVanced Manager](https://github.com/Jman-Github/Universal-ReVanced-Manager) and [ReVanced Manager](https://github.com/ReVanced/revanced-manager). Each patch bundle has its own authors and license; review a third-party repository before adding it.
