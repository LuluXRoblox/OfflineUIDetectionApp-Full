# Offline UI Detection App

An offline Android app that captures the screen and detects configured UI states using regions of interest (ROIs) and grayscale templates. It can select a stored configuration when a matching state is found.

## Features

- Local screen capture with Android `MediaProjection`.
- ROI-based image processing and template/feature matching.
- ADS-gated weapon, scope, and stance detection.
- Multiple grayscale template samples per label.
- Detection throttled to approximately 10 frames per second.
- Configuration and template data stay on the device; the app has no network, Firebase, server, or cloud integration.

## Requirements

- Android Studio with Android SDK Platform 34 installed.
- JDK 17.
- Android 8.0 (API 26) or newer device/emulator.

## Open and build

1. Clone or download this repository.
2. In Android Studio, choose **Open** and select the repository root (the folder containing `settings.gradle`).
3. Allow Gradle to sync, then run the `app` configuration on a device or emulator.

The GitHub Actions workflow also builds the debug APK on pushes and pull requests. The generated APK is a build artifact and is not committed to the repository.

## Permissions and privacy

Screen capture requires the user to approve the Android `MediaProjection` prompt. The app also requests overlay permission for its floating overlay. Captured frames are processed locally; this project does not upload screen contents.

## Scope and limitations

- The detector identifies UI states and selects stored configurations; it does not calculate or predict recoil.
- Game interfaces and coordinates vary. The project is intentionally generic and does not ship universal, game-specific templates.
- ROI coordinates are normalized from 0 to 1.
- Templates are stored locally in `filesDir/templates.json`. A game-specific calibration flow should collect multiple samples for each target state.

## License

No license is currently included. Unless a license is added, this repository does not grant permission to reuse or redistribute its code.

## Auto Drag (Floating)

Floating panel sekarang memiliki **AUTO DRAG**:
- `Setiap X ms` mengatur interval pengulangan.
- `Turun Y px` mengatur jarak drag vertikal.
- `ON/OFF` menjalankan atau menghentikan loop drag.
- Drag dilakukan melalui Android Accessibility Service dan dimulai dari titik tengah layar.
- Aktifkan **Izin Drag** sekali di Pengaturan Aksesibilitas Android sebelum menekan ON.

Nilai tersimpan di local storage aplikasi. Interval dibatasi 50–60000 ms dan jarak 1–2000 px.
