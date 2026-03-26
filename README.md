# Transcriber Android

Android client for the `Transcriber Desktop` project. It is written in plain Java, built with Gradle from the shell, and intended to work locally without Android Studio.

## What It Does
- offline on-device Whisper TFLite transcription
- bundled `tiny-en` and `tiny` by default
- optional local `small` model for manual testing
- local audio enhancement with bundled AI denoise before transcription
- local diarized transcript output
- local summary and key points
- saved outputs screen with search and delete
- custom `.tflite` model import through the app

## Project Layout
- `app/src/main/java/com/convoy/androidtranscriber/` app code
- `app/src/main/assets/models/` bundled TFLite models
- `app/src/main/assets/audio/` sample audio
- `app/src/main/assets/denoise/` bundled RNNoise model for AI denoise
- `app/src/main/res/` layouts and resources
- `scripts/` local helper scripts for staging bigger models
- `releases/` built APK copies

## Build From Source
Required:
- JDK 17
- Android SDK command-line tools
- Android platform/build tools for API 34

Build:

```bash
cd /home/user/github/transcriber-android
export JAVA_HOME=/home/user/Downloads/toolchains/jdk-17.0.18+8
export ANDROID_SDK_ROOT=/home/user/Downloads/android-sdk
export TMPDIR=/home/user/.tmp-gradle
export GRADLE_USER_HOME=/home/user/.gradle-user-home-clean
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
export GRADLE_OPTS='-Djava.io.tmpdir=/home/user/.tmp-gradle -XX:-UsePerfData'
export _JAVA_OPTIONS='-Djava.io.tmpdir=/home/user/.tmp-gradle -XX:-UsePerfData'
./gradlew :app:assembleDebug
```

APK output:
- `app/build/outputs/apk/debug/app-debug.apk`
- `releases/transcriber-android-debug.apk`

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Offline Model Setup
Bundled by default:
- `whisper-tiny.en.tflite`
- `whisper-tiny.tflite`

Optional local test model:
- shared source: `/home/user/github/transcriber-desktop/models/whisper-small.tflite`

To stage it into Android assets for a local test build:

```bash
cd /home/user/github/transcriber-android
./scripts/stage_local_models.sh
```

That copies the shared desktop model into:

```text
app/src/main/assets/models/whisper-small.tflite
```

The staged file is ignored by git, so it stays local only.

The desktop setup script can stage this file locally:

```bash
cd /home/user/github/transcriber-desktop
./scripts/prepare_offline_models.sh
```

You can also import extra `.tflite` models from inside the app using `Manage Models`.

## App Flow
1. Choose a bundled or imported model.
2. Pick media or record audio.
3. The app converts audio to enhanced 16 kHz mono WAV and runs bundled AI denoise.
4. Transcription runs fully on-device.
5. Transcript, diarized transcript, summary, and metadata are saved locally.

## Saved Output Files
The app writes local output groups using the WAV base name:
- `<name>.transcript.txt`
- `<name>.diarized.txt`
- `<name>.summary.txt`
- `<name>.meta.json`

## Notes
- This app is intentionally minimal.
- Diarization is local and approximate.
- Summary generation is lightweight heuristic logic, not a full LLM pipeline.
- Large bundled models increase APK size and build time.
