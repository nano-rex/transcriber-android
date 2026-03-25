# Android_Transcriber

Android app version of the `Linux_Transcriber` flow, built in plain Java and designed to be buildable **without Android Studio**.

It keeps the same overall logic as the Linux project where practical:
- bundled local Whisper models
- local/offline transcription
- simple model recommendation
- transcript generation
- overview + key points summary output

---

## Current Status

Working Android alpha.

This project is now a **real Android app**, not a shell-only wrapper:
- Android activity-based UI
- media picker
- microphone recording to local WAV
- local media import/prep
- on-device TFLite transcription
- basic speaker diarization output (`Speaker 1` / `Speaker 2`)
- overview + key points output
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
- pick local media files from Android storage
- record audio from microphone into app-local WAV
- import audio/video media into app-local storage
- normalize imported media into local WAV for transcription flow
- local Whisper TFLite transcription over full audio in 30-second chunks
- transcript display inside the app
- diarized transcript display in app UI
- overview + key points summary generation
- save transcript, diarized transcript, and summary as app-local text outputs

### Current bundled model tiers
- `tiny-en`
- `tiny`

### Planned / not finished yet
- stronger compatibility testing across more `.mp3`, `.m4a`, and `.mp4` files
- cleaner timestamped transcript chunk output in the Android UI
- release signing / production APK flow

---

## Project Structure

```text
Android_Transcriber/
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
cd /home/user/Downloads/Android_Transcriber
export JAVA_HOME=/home/user/Downloads/toolchains/jdk-17.0.18+8
export ANDROID_SDK_ROOT=/home/user/Downloads/android-sdk
export GRADLE_USER_HOME=/tmp/gradle-user-home
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
./gradlew :app:assembleDebug
```

If you prefer using system-installed Java/SDK instead, adjust `JAVA_HOME`, `ANDROID_SDK_ROOT`, and `PATH` accordingly.

### Output APK
Expected output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Current built debug APK on this machine:

```text
/home/user/Downloads/Android_Transcriber/app/build/outputs/apk/debug/app-debug.apk
```

---

## Install on Device

With USB debugging enabled:

```bash
cd /home/user/Downloads/Android_Transcriber
export JAVA_HOME=/home/user/Downloads/toolchains/jdk-17.0.18+8
export ANDROID_SDK_ROOT=/home/user/Downloads/android-sdk
export GRADLE_USER_HOME=/tmp/gradle-user-home
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## How the App Works

### In-app flow
1. app starts
2. bundled sample file is available by default
3. app recommends a model tier
4. user can pick a media file from device storage
5. app imports and prepares the file as local WAV
6. user starts transcription
7. app runs local Whisper TFLite inference
8. app shows transcript
9. app generates overview + key points
10. app writes transcript/diarized/summary text files to app-local storage

### Model selection logic
Current recommendation logic mirrors the Linux project at a lightweight level:
- higher-memory / more CPU devices can prefer larger tiers later
- low-resource fallback defaults to `tiny-en`

At the moment, the project is effectively optimized around the bundled tiny tiers.

---

## App-Local Output Files

The app writes outputs into its internal app files area.

Current output types:
- transcript text file (rough chunk timestamps)
- diarized transcript text file (rough speaker labels)
- summary text file

Generated filenames follow the imported WAV base name:
- `<name>.transcript.txt`
- `<name>.diarized.txt`
- `<name>.summary.txt`

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

That reference is used for the command-line Android build style, while the actual app logic here follows the `Linux_Transcriber` project direction.
