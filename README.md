# Transcriber Android

Android client for `Transcriber Desktop`, built from the shell with Gradle. The app now uses a local `whisper.cpp` runtime instead of TensorFlow Lite.

## What It Does
- offline on-device transcription with bundled `whisper.cpp` models
- no ASR models bundled by default
- local audio enhancement and optional trimming before transcription
- local diarized transcript output
- transcript output only; Android AI summary model is not implemented yet
- saved outputs screen with search and delete
- settings page for preprocessing toggles

## Project Layout
- `app/src/main/java/com/convoy/androidtranscriber/` app code
- `app/src/main/java/com/convoy/androidtranscriber/engine/` Java bridge to `whisper.cpp`
- `app/src/main/cpp/` vendored `whisper.cpp` native build files
- `app/src/main/assets/models/` optional bundled `.bin` Whisper models if you choose to add them later
- `app/src/main/assets/audio/` sample audio
- `app/src/main/assets/denoise/` bundled RNNoise model for AI denoise
- `app/src/main/res/` layouts and resources

## Build From Source
Required:
- JDK 21
- Android SDK command-line tools
- Android platform/build tools for API 34
- Android NDK `25.2.9519653`
- Android CMake `3.22.1`

Build:

```bash
cd /home/user/github/transcriber-android
export ANDROID_SDK_ROOT=/home/user/Downloads/android-sdk
./gradlew :app:assembleDebug
```

APK output:
- `app/build/outputs/apk/debug/app-debug.apk`

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Offline Models
Default install ships with zero ASR models.

Use `Manage Models` inside the app to download one of these hosted models:
- `ggml-tiny.en.bin`
- `ggml-tiny.bin`
- `ggml-small.bin`
- `ggml-small-mandarin.bin`
- `ggml-small-malay.bin`
- `ggml-small-cantonese.bin`
- `ggml-small-hokkien.bin`

After download, the model is stored locally on the device and used fully offline.

## App Flow
1. Download and choose an installed model.
2. Pick media from the device.
3. The app optionally enhances and trims audio according to settings.
4. Transcription runs locally through `whisper.cpp`.
5. Transcript, diarized transcript, a summary placeholder message, and metadata are saved locally.

## Saved Output Files
The app writes local output groups using the WAV base name:
- `<name>.enhanced.wav`
- `<name>.transcript.txt`
- `<name>.diarized.srt`
- `<name>.summary.txt`
- `<name>.meta.json`

## Notes
- This app is intentionally minimal.
- Diarization is local and approximate.
- Android does not yet have a real local summary-model runtime.
- Downloaded models are stored locally on the device for offline reuse.
