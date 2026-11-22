# Form Mobile - unofficial Android port

A local, offline Android WebView shell for the original [youraipulse/Form](https://github.com/youraipulse/Form) editor.

## What this port includes

- Original WebGL effects and UI bundled as offline assets from **upstream `dist/`**.
- Compact, touch-friendly portrait / landscape layout with separate preview and scrollable controls.
- Android system file picker for SVG, transparent PNG, WebP and JPEG (upstream rejects images without transparency).
- Export of PNG and VP8/VP9 WebM to **Downloads/Form** through Android MediaStore.
- 1K quality option (selected by default to reduce mobile GPU pressure), plus 2K and 4K.
- Local HTTPS-like WebView asset origin, needed for secure-context browser APIs such as WebCodecs.
- No account, API key or background server.

**Caveats:** This is a source project, not a pre-built APK. Android build tools are unavailable in the environment used to prepare it, so **the APK has not been compiled or tested on a phone**. 4K video encoding may fail depending on GPU memory and Android System WebView/WebCodecs support. PNG is the safer output. Android 10+ required.

## Build APK via GitHub Actions (no Android Studio required)

1. Create a new repository in your GitHub account.
2. Upload **the contents** of this directory to the new repository (including `.github/workflows/android-apk.yml`). The repository should use `main` as the default branch.
3. Open repository **Actions -> Build Form Android APK -> Run workflow**. Or push to `main` to run automatically.
4. When the workflow succeeds, open its **Artifacts** section and download `Form-Mobile-debug-APK`.
5. Unpack the artifact ZIP and install `app-debug.apk` on Android 10 or newer. Android may request permission to install apps from that source.

## Build locally with Android Studio

- JDK 17, Android SDK Platform 35, Gradle 8.9, Android Gradle Plugin 8.7.3.
- Open the project folder in Android Studio. Use the IDE Gradle 8.9 installation, or run `gradle :app:assembleDebug` with Gradle 8.9 installed.
- No Node.js or npm needed. The Gradle build automatically downloads the pinned Form upstream ZIP, extracts its `dist/` directory and injects the mobile CSS / JS.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Technical design

`app/build.gradle.kts` has `prepareFormAssets`, which fetches Form at pinned revision `dfcdb61d9f86832ef1104a97d5141f8592546b07`, copies upstream `dist/` assets and injects `form-mobile.css` and `form-mobile.js`. Network is needed **during the first build**, not for normal app usage.

`MainActivity` serves bundled assets through `shouldInterceptRequest` at `https://appassets.androidplatform.net/`, keeping page origin secure and local. It opens SVG/PNG files through `ACTION_OPEN_DOCUMENT`. A restricted JS bridge transfers blob downloads to MediaStore in small Base64 chunks. Other network resources are blocked in the WebView; external links open in a separate browser.

The upstream library produces PNG via canvas and 6-second WebM using Mediabunny / WebCodecs. We do not modify its shaders or loop-generation logic.

## Tests to run on a real phone

1. Open the app offline and confirm the default animated Heatmap renders.
2. Import transparent SVG and PNG; test all three materials and presets.
3. Change sliders, selected colors, aspect ratio, and pause/resume.
4. Export 1K PNG and confirm it appears under Downloads/Form.
5. Export 1K WebM and verify 6 seconds and seamless looping on device.
6. Try 2K/4K only after basic exports work; check device memory and codec support.
7. Rotate to landscape and return to portrait without losing the current logo.

## Upstream licenses and redistribution

This project is an **unofficial adaptation**, not maintained by upstream. Upstream includes third-party component license notices in its `dist/`, which this build packages with the application. The original project does not appear to declare an overall repository license in the inspected tree. Public distribution of the original application's code/assets may require permission from its author. Do not present this package as an officially endorsed release.
