# Reproducible dependency set

PatchDock's first release uses composite builds because the required TikTok resource-path fix is not yet in the published Morphe patcher and the public GitHub Maven package for Morphe's `jadb` fork requires credentials.

Run `scripts/bootstrap-dependencies.ps1` from PowerShell with JDK 17 available. By default, it creates or verifies sibling checkouts next to this repository and installs `app.morphe:jadb:1.2.3` into the current user's local Maven repository.

## Exact revisions

| Component | Repository | Revision |
|---|---|---|
| morphe-patcher | `https://github.com/SysAdminDoc/morphe-patcher.git` | `464c002ad086039561766f1fee8e1cb8126cf56d` |
| morphe-library | `https://github.com/MorpheApp/morphe-library.git` | `a5b1fb512306d497cad8a13c0399a5fb28553522` (`v1.4.0`) |
| jadb | `https://github.com/MorpheApp/jadb.git` | `d6db20b20b754cd3ac4c22e435b9802405d40051` (`v1.2.3`) |

The bootstrap script makes two narrowly scoped compatibility edits to the `morphe-library` checkout:

- Android Gradle Plugin `8.9.3` → `9.3.1`
- Kotlin `2.2.21` → `2.4.10`
- `android.newDsl=false` and `android.builtInKotlin=false`

## Why the patcher fork is required

TikTok 43.8.3 uses resource file paths that the upstream merge path normalized incorrectly. The patched revision preserves the original merged resource paths while rewriting the APK. Without it, the produced APK installs but crashes at launch because resources referenced by TikTok are no longer at the expected archive paths.

This is a source dependency, not a prebuilt opaque patcher. Review the one-commit difference from upstream before release:

```powershell
git -C ..\morphe-patcher show --stat --oneline 464c002
git -C ..\morphe-patcher show 464c002
```

## Build

```powershell
.\scripts\bootstrap-dependencies.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease -PsignAsDebug
```

`-PsignAsDebug` uses the Android SDK's throwaway development key only to make the manager APK installable. It is unrelated to the private keystore PatchDock uses for patched TikTok outputs.
