# Related-project research

Research snapshot: 2026-08-01

## Result

PatchDock now uses Morphe Manager's native multi-bundle patch pipeline with a two-level trust catalog. Built-in sources are release-hash pinned. App targets are discovered dynamically, but the in-app downloader is enabled only when an enabled patch bundle supplies an exact version and valid original-publisher certificate fingerprints.

This expands the default scope from TikTok alone to TikTok, YouTube, YouTube Music, and Reddit without introducing a generic “download and execute anything” path. User-added bundles remain supported and automatically benefit from the secure flow when they publish sufficient compatibility metadata.

## Live bundle inventory

The official [`MorpheApp/morphe-patches`](https://github.com/MorpheApp/morphe-patches) 1.38.0 release contains 133 patches and three non-universal target packages:

| Target | Package | Patches | File policy | Versions advertised on 2026-08-01 |
|---|---|---:|---|---|
| YouTube | `com.google.android.youtube` | 74 | `APK_REQUIRED` | 20.21.37, 20.31.42, 20.51.39, 21.04.223; experimental 21.05.265, 21.26.360, 21.28.204, 21.29.366 |
| YouTube Music | `com.google.android.apps.youtube.music` | 37 | `APK_REQUIRED` | 7.29.52, 9.15.51; experimental 9.28.51, 9.29.54, 9.30.52 |
| Reddit | `com.reddit.frontpage` | 18 | `APKM` preference in current metadata | 2026.04.0, 2026.14.0; experimental 2026.24.0, 2026.28.0, 2026.29.0 |

The manager consumes this metadata at runtime, so patch/version changes do not require a second hardcoded app table. PatchDock separately retains the exact TikTok 43.8.3 artifact policy from [`icysymmetra/tiktok-patches-for-morphe`](https://github.com/icysymmetra/tiktok-patches-for-morphe).

## Projects reviewed

| Project | Useful pattern | Decision for PatchDock |
|---|---|---|
| [Morphe Manager](https://github.com/MorpheApp/morphe-manager) | Kotlin/Compose manager, `.mpp` patch sources, dynamic compatibility metadata, on-device patching, keystore management, Shizuku installer | Keep as the GPLv3 foundation and restore its official app coverage through a separately branded, hash-pinned source. |
| [Morphe Patches](https://github.com/MorpheApp/morphe-patches) | First-party YouTube, YouTube Music, and Reddit patches with package, version, file-type, and certificate metadata | Add as a one-time built-in source. Pin 1.38.0's released `.mpp` SHA-256. |
| [TikTok Patches for Morphe](https://github.com/icysymmetra/tiktok-patches-for-morphe) | Version-specific patches for global TikTok `com.zhiliaoapp.musically` | Preserve as the default uid-0 source and keep the stricter byte-for-byte stock APK pin. |
| [Piko](https://github.com/crimera/piko) | Morphe patches for Instagram and X; current metadata demonstrates how community bundles widen app coverage | Do not silently bootstrap it. The reviewed 3.8.0 metadata did not declare original-app certificate fingerprints, so it cannot enter PatchDock's automatic trusted downloader path. Users can still add it explicitly and select files manually. |
| [ReVanced Manager](https://github.com/ReVanced/revanced-manager) | Clear download → select patches → patch → customize/install lifecycle | Preserve the familiar lifecycle while deriving compatible targets from the selected bundle. |
| [Obtainium](https://github.com/ImranR98/Obtainium) | Source adapters, release tracking, certificate visibility, direct-from-source updates | Keep source metadata separate from downloaded artifacts. Its APKMirror limitations reinforce a user-driven web flow rather than brittle hidden scraping. |
| [APKUpdater](https://github.com/rumboalla/apkupdater) | Aggregated search, update checks, direct/root install options | Useful future provider-adapter reference; not imported as a broad trust bypass. |
| [Aurora Store](https://github.com/whyorean/AuroraStore) | Older-version and split delivery | Not selected as a default provider because it relies on a reverse-engineered Play API and account/device configuration. |
| [Shizuku](https://shizuku.rikka.app/) and [Shizuku API](https://github.com/RikkaApps/Shizuku-API) | Delegated ADB/root-level system API access | Keep as the default installer with binder/authorization checks and the normal Android installer fallback. |
| [APKEditor / ARSCLib](https://github.com/REAndroid/APKEditor) | Split merging and resource-table/path handling | Continue using the local patcher correction that preserves resource paths and prevents the TikTok launch crash. |

## Hosting-source decision

APKMirror provides package/version/variant details, file hashes, and certificate fingerprints, but no stable public download API for this workflow. PatchDock therefore keeps the user in the site's normal flow:

1. Prefer an exact APKMirror result resolved for the bundle-selected package/version.
2. Fall back to a focused APKMirror search when a trusted exact URL is unavailable.
3. Restrict top-level navigation and every redirected download to HTTPS `apkmirror.com` hosts.
4. Cancel all WebView TLS errors. A failing advertisement or iframe is logged without misreporting the main document as insecure.
5. Infer `.apk`, `.apkm`, `.apks`, or `.xapk` from response metadata and retain that extension for the patch pipeline.
6. Verify package, exact version, required shape, and publisher certificate before returning the file to the manager.
7. For split archives, inspect every root APK module that will be merged; all modules must agree on package/version and have a common trusted certificate.

This follows Android's guidance to validate both URI scheme and host, disable unnecessary WebView file/content access, and never call `SslErrorHandler.proceed()` after a certificate failure: [unsafe URI loading](https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading), [unsafe file inclusion](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion), and [`WebViewClient.onReceivedSslError`](https://developer.android.com/reference/android/webkit/WebViewClient#onReceivedSslError(android.webkit.WebView,android.webkit.SslErrorHandler,android.net.http.SslError)).

## Signing and update model

There are two unrelated keys:

- **PatchDock APK key:** identifies the manager itself. Android requires every installable APK to be signed.
- **Patched-app key:** signs outputs produced by PatchDock. It must remain stable or Android cannot update the already-installed patched app without uninstalling it.

PatchDock keeps the user's patching key in private app storage and supports import/export. The manager development APK uses only Android's throwaway debug identity; no production signing or external trust certificate is used.

## Trust and update architecture

```text
embedded built-in source catalog
  ├─ TikTok patches version + mpp SHA-256
  └─ official Morphe patches version + mpp SHA-256
                    ↓
enabled bundle compatibility metadata
  ├─ package + exact supported version
  ├─ APK/APKM/APKS/XAPK requirement
  └─ original publisher certificate SHA-256
                    ↓
user-driven APKMirror flow → redirect/size/package/version/shape/certificate checks
                    ↓
local patch → private local key → Shizuku or PackageInstaller
```

An unknown built-in patch release fails closed until a PatchDock update adds its digest. A user-added third-party source is an explicit trust decision, but it still cannot enable automatic APK acceptance without publisher-certificate metadata.

## Next extensions

1. Move built-in source/version pins to a separately signed, rollback-protected TUF-style metadata channel rooted in an embedded public key.
2. Add a review bot that observes upstream releases, verifies GitHub asset digests, summarizes compatibility deltas, and proposes catalog updates without auto-publishing them.
3. Add resumable downloads while preserving the final full-file and certificate verification pass.
4. Add provider adapters only behind the same package/version/certificate policy; never add an arbitrary-download bypass to simple mode.
5. Offer a visible “metadata incomplete” explanation for third-party sources that omit publisher fingerprints.
