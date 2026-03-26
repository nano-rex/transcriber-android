# Transcriber Android

Android app version of the `Transcriber Desktop` flow, built in plain Java and designed to be buildable without Android Studio.

It keeps the same overall logic as the Linux project where practical:
- bundled local Whisper models
- custom `.tflite` model import for larger or alternate local models
- local/offline transcription
- speech-focused audio enhancement before transcription
- simple model recommendation
- timestamped transcript generation
- diarized transcript output
- overview + key points summary output
- saved outputs page with search and delete

---

## Current Status

Working Android alpha.

This project is now a **real Android app**, not a shell-only wrapper:
- Android activity-based UI
- media picker
- microphone recording to local WAV
- local media import/prep with enhancement
- on-device TFLite transcription
- local two-speaker diarization using acoustic clustering
- overview + key points output
- custom model manager for imported `.tflite` files
- debug APK successfully builds from source with Gradle CLI

---

## Current Features

### Implemented
- Java-based Android app UI
- buildable from command line without Android Studio
- bundled Whisper model assets stored inside the project
- bundled vocab/filter assets stored inside the project
- bundled sample audio asset
- local model recommendation logic based on device resources
- model tier selection in app UI
- custom model import and deletion in app UI
- pick local media files from Android storage
- record audio from microphone into app-local WAV
- import audio/video media into app-local storage
- enhance and normalize imported media into local WAV for transcription flow
- local Whisper TFLite transcription over full audio in 30-second chunks
- transcript display inside the app
- diarized transcript display in app UI
- overview + key points summary generation
- save transcript, diarized transcript, summary, and metadata as app-local outputs
- browse saved outputs inside the app
- search saved outputs by filename
- delete saved outputs from the app

### Current bundled model tiers
- `tiny-en`
- `tiny`

Additional models:
- import custom `.tflite` files from the app via `Manage Models`
- English-only models should include `.en` in the filename
- other imported models are treated as multilingual

### Planned / not finished yet
- stronger compatibility testing across more `.mp3`, `.m4a`, and `.mp4` files
- cleaner timestamped transcript chunk output in the Android UI
- release signing / production APK flow

---

## Project Structure

```text
transcriber-android/
├─ app/
│  ├─ src/main/java/com/convoy/androidtranscriber/
│  ├─ src/main/res/
│  ├─ src/main/assets/models/
│  ├─ src/main/assets/audio/
│  └─ build.gradle
├─ gradle/
├─ gradlew
├─ gradlew.bat
├─ build.gradle
├─ settings.gradle
├─ gradle.properties
└─ local.properties
```

Important source areas:
- `app/src/main/java/com/convoy/androidtranscriber/MainActivity.java`
- `app/src/main/java/com/convoy/androidtranscriber/asr/Whisper.java`
- `app/src/main/java/com/convoy/androidtranscriber/engine/WhisperEngineJava.java`
- `app/src/main/java/com/convoy/androidtranscriber/util/`

---

## Bundled Assets

### Models
Located in:
```text
app/src/main/assets/models/
```

Current bundled files:
- `whisper-tiny.en.tflite`
- `whisper-tiny.tflite`

### Vocab / filters
Located in:
```text
app/src/main/assets/
```

Files:
- `filters_vocab_en.bin`
- `filters_vocab_multilingual.bin`

### Sample audio
Located in:
```text
app/src/main/assets/audio/
```

Files:
- `jfk.wav`

---

## Build From Source

This project follows the **Java + Gradle + Android SDK command-line** workflow.
You do **not** need Android Studio.

### Prerequisites
You need:
- JDK 17
- Android SDK command-line tools
- Android SDK platform tools
- Android platform for API 34
- Android build tools for API 34

### Expected local toolchain on this machine
This machine uses:
- JDK: `/home/user/Downloads/toolchains/jdk-17.0.18+8`
- Android SDK: `/home/user/Downloads/android-sdk`

### local.properties
This project uses:

```properties
sdk.dir=/home/user/Downloads/android-sdk
```

---

## Build Commands

From the project root:

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

If you prefer using system-installed Java/SDK instead, adjust `JAVA_HOME`, `ANDROID_SDK_ROOT`, and `PATH` accordingly.

### Output APK
Expected output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Current built debug APK on this machine:

`app/build/outputs/apk/debug/app-debug.apk`

Release copy used in this repo:

`releases/transcriber-android-debug.apk`

---

## Install on Device

With USB debugging enabled:

```bash
cd /home/user/github/transcriber-android
export JAVA_HOME=/home/user/Downloads/toolchains/jdk-17.0.18+8
export ANDROID_SDK_ROOT=/home/user/Downloads/android-sdk
export TMPDIR=/home/user/.tmp-gradle
export GRADLE_USER_HOME=/home/user/.gradle-user-home-clean
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
export GRADLE_OPTS='-Djava.io.tmpdir=/home/user/.tmp-gradle -XX:-UsePerfData'
export _JAVA_OPTIONS='-Djava.io.tmpdir=/home/user/.tmp-gradle -XX:-UsePerfData'
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## How the App Works

### In-app flow
1. app starts
2. bundled sample file is available by default
3. app recommends a model tier
4. user can import additional `.tflite` models in `Manage Models`
5. user can pick a media file from device storage
6. app imports, enhances, and prepares the file as local WAV
7. user starts transcription
8. app runs local Whisper TFLite inference
9. app shows transcript
10. app generates overview + key points
11. app writes transcript/diarized/summary/meta files to app-local storage

### Model selection logic
Current recommendation logic mirrors the Linux project at a lightweight level:
- higher-memory / more CPU devices prefer stronger tiers if they are installed
- low-resource fallback defaults to `tiny-en`
- imported models can be selected immediately after installation

---

## App-Local Output Files

The app writes outputs into its internal app files area.

Current output types:
- transcript text file (rough chunk timestamps)
- diarized transcript text file (rough speaker labels)
- summary text file
- metadata json file

Generated filenames follow the imported WAV base name:
- `<name>.transcript.txt`
- `<name>.diarized.txt`
- `<name>.summary.txt`
- `<name>.meta.json`

---

## Notes and Limitations

- This is still an alpha.
- The Android UI is intentionally minimal.
- Media import should work for common audio/video sources, but broader file compatibility still needs more real-device testing.
- Summary generation is currently lightweight heuristic logic, matching the simple Linux alpha style rather than using a separate LLM.
- Small/medium model tiers are planned conceptually, but must be bundled first before they become truly available in app.

---

## Reference

This project is intentionally aligned with a **no Android Studio** workflow similar to:

```text
/home/user/github/Building-Android-Apps-with-Java-No-Android-Studio-
```

That reference is used for the command-line Android build style, while the actual app logic here follows the `Transcriber Desktop` project direction.
