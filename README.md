# Ditherer Android (unofficial)

Offline Android wrapper for the original [gyng/ditherer](https://github.com/gyng/ditherer), keeping its React/TypeScript WebGL2/WASM engine. Based on upstream MIT-licensed project, commit `f9608872658d53f4916a5eef4d3312019285a8f0`.

## Build

GitHub Actions: run **Build Ditherer Android APK** in this branch. It fetches the pinned upstream source, runs `npm ci` and `npm run build`, packages the output into Android assets and compiles a debug APK using Android Gradle Plugin 8.7.3 and Android API 35.

For local builds, install Node 22, Java 17, Android SDK 35, Gradle 8.9. Build upstream Ditherer then copy its `build/` files to `ditherer/src/main/assets/site/`. Also copy `ditherer/mobile/android-{mobile.css,bridge.js}` to that folder and inject the links before `</head>` and `</body>` respectively in `index.html`. Then run `gradle :ditherer:assembleDebug`.

## Android-specific features

- Android 10+ (API 29+), hardware-accelerated WebView with local appassets secure origin
- Offline operation after installation (bundled web assets)
- Pick media files with Android's document picker
- Chunked export of images, video and JSON files to `Downloads/Ditherer`
- Optional microphone permission for audio-reactive effects
- Original compact mobile task layout and no ad/network APIs

## Known limitations

- Video formats and high-end 3D/WebGL filters depend on GPU and Android System WebView.
- Browser-only APIs such as tab-audio capture and desktop filesystem access are unavailable.
- Camera, performance, video encoding and every preset must be verified on real devices; a successful APK build is not functional validation.
- Debug APK signing is not suitable for Play Store distribution.

## License

MIT. Preserve upstream copyright, LICENSE and bundled third-party notices. This is an independent port and is not affiliated with its original author.
