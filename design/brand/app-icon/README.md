# App icon and store artwork

Every file here is generated from `../logo/source/logo-v3-violet-dark.jpeg` (the violet squirrel with headphones) by `generate_app_icons.py`. The mark is cut out of the artwork pixel for pixel. Its colours and gradients are the original ones, and the background is the artwork's own flat `#141416`. The "TEMBRA AUDIO" wordmark appears only in the Play feature graphic, never in an icon.

```sh
python3 design/brand/app-icon/generate_app_icons.py            # everything
python3 design/brand/app-icon/generate_app_icons.py android    # or ios, previews
```

Requires Pillow and NumPy. The iOS preview also renders `AppIcon.icon` with Icon Composer's `ictool` from Xcode 27.1 beta, and is skipped without it. Re-running produces identical files.

The mark is sized by the circle that encloses it. That circle is 78 % of the visible icon: 56 dp of Android's 72 dp viewport (inside the 66 dp safe zone), and 799 px of the 1024 px iOS and Play canvases.

## Android (`android/`)

| File | Size | Spec |
|------|------|------|
| `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png` | 108, 162, 216, 324, 432 px | Adaptive icon foreground layer: 108 dp, transparent, mark inside the 66 dp safe zone |
| `res/mipmap-*/ic_launcher_monochrome.png` | same | White silhouette for Android 13+ themed icons |
| `res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | | `<adaptive-icon>` with background, foreground and monochrome layers |
| `res/values/ic_launcher_background.xml` | | `ic_launcher_background` = `#141416` |
| `play-store/icon-512.png` | 512 × 512, 32-bit PNG, sRGB, 108 KB | Google Play hi-res icon: full square, no shadow (Play adds the 30 % corner mask and the shadow) |
| `play-store/feature-graphic-1024x500.png` | 1024 × 500, 24-bit PNG, no alpha | Google Play feature graphic |

`minSdk` is 26, so the adaptive icon covers every device and no legacy `ic_launcher.png` bitmaps are needed.

Applied to the app on branch `brand/app-icons`: the `mipmap-*` folders and both XML files are in `app/androidApp/src/main/res/`, the placeholder `drawable/ic_launcher_foreground.xml` is gone, `ic_launcher_background` in `values/colors.xml` is `#141416` (so `res/values/ic_launcher_background.xml` here is not copied: two definitions of the colour would fail the build), and the manifest sets `android:roundIcon`.

## iOS (`ios/`)

| File | Size | Use |
|------|------|-----|
| `AppIcon.appiconset/` | 1024 × 1024 ×3 | Asset-catalog icon: light (opaque RGB), dark (transparent background) and tinted (opaque greyscale). Works on every iOS version; iOS 18+ uses the dark and tinted variants. Applied as `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset` |
| `AppIcon.icon/` | 1024 layer | Icon Composer document for iOS 26+ Liquid Glass: the squirrel as one glass layer on a solid `#141416` fill. Xcode builds the pre-iOS 26 fallback PNGs from it. Open it in Icon Composer to adjust the glass, shadow or per-appearance settings |
| `app-store/app-store-icon-1024.png` | 1024 × 1024, RGB, no alpha | App Store Connect marketing icon |

Use one of the two icon formats, named `AppIcon` (the target's `ASSETCATALOG_COMPILER_APPICON_NAME`). Both compile cleanly with `actool` from Xcode 27.1 beta at the app's iOS 16.0 deployment target.

## Other files

- `master/`: the cut-out mark (991 × 924), its monochrome silhouette and the wordmark, all on transparent backgrounds, for any further artwork.
- `previews/android-preview.png`: the adaptive icon with keylines, the circle, squircle and rounded-square masks, the themed icon, the Play icon, and real launcher sizes.
- `previews/ios-preview.png`: the asset-catalog variants under the iOS mask, plus `AppIcon.icon` rendered by `ictool` in all six Home Screen appearances.

Specs: [Google Play icon](https://developer.android.com/google-play/resources/icon-design-specifications), [Android adaptive icons](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive), [Apple app icons](https://developer.apple.com/design/human-interface-guidelines/app-icons).
