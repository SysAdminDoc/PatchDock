# Android integration

The brand kit's `android-res/` directory is ready to merge into an Android app module's `src/main/res/` directory.

## Resource map

- `drawable/ic_launcher_foreground.xml` — primary two-color foreground
- `drawable/ic_launcher_foreground_dark.xml` — dark-background foreground
- `drawable/ic_launcher_monochrome.xml` — single-color alpha silhouette for system tinting
- `drawable/ic_launcher_background*.xml` — six full-bleed light/dark background choices
- `mipmap-anydpi-v26/ic_launcher*.xml` — matching adaptive-icon definitions with foreground, background, and monochrome layers
- `drawable/ic_notification.xml` — notification-safe single-color mark
- `drawable/ic_mpp.xml` — compact package/patch-file mark

Use the default manifest references:

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher" />
```

One adaptive definition is deliberately used for both icon references. Mask testing did not justify separate round geometry.

The PatchDock project has `minSdk=26`, the first Android release with adaptive-icon support, so it does not need legacy density PNGs. A different app supporting API 25 or earlier must generate conventional `mipmap-mdpi` through `mipmap-xxxhdpi` launcher assets from the master symbol.

`google-play/patchdock-play-store-512.png` is listing artwork only. Do not place it in packaged launcher resources, and do not add rounding or an outer shadow before uploading it to Google Play.
