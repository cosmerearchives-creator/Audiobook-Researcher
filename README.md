# Audiobook Researcher

An Android audiobook research app with a muted-purple, light-neutral interface.

## Current development status

The first local-player iteration includes:
- Import local audio through Android's document picker with persistent access permissions.
- A saved audiobook library with resume positions.
- A basic player with play/pause, seek, 15-second rewind, and 30-second skip.
- A light neutral background, white rounded cards, and muted purple controls.
- The built-in GitHub Release updater from the initial foundation.
- Foreground playback service with Android media notification play/pause and skip controls; playback is no longer owned by the screen activity.
- Timestamped notes for each audiobook, saved locally across updates.
- Add notes by typing or using Android speech dictation (where a recognizer is available).
- Jump to the note timestamp with a 15-second context rewind.
- Record an original AAC/M4A voice note into app-private storage, linked to the current book and timestamp.
- Auto-save after roughly four seconds of detected quiet following speech, or use Save note; replay the original recording from its bookmark. Microphone permission required.
- Experimental opt-in foreground “Bookmark” recognition using Android SpeechRecognizer. It pauses the book and starts an original-audio voice note. This is not a dedicated always-on wake-word engine; it stops when the app leaves the foreground and may mishear speech from the audiobook itself.

**Not yet implemented:** reliably verified screen-off/background “Bookmark” detection, spoken “Save note” recognition during audio recording, transcription of retained voice audio, chapter parsing, external service controls, full Bluetooth/lock-screen real-device verification. Silence detection is amplitude-based and needs tuning on actual devices and car microphones. Speech dictation currently uses an external Android recognizer and may require a network connection. This player is an early foundation and will not yet continue through all background playback conditions.

## Building and updates

JDK 17, Android SDK 35, Gradle 8.11.1. Run `gradle :app:assembleDebug`.

Each main-branch push triggers GitHub Actions. With the four signing secrets configured, CI publishes a signed release APK named `audiobook-researcher.apk` as `v0.1.<run_number>`. All updates require the original signing certificate. Never install the debug APK when testing production updates.

Configure repository Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Keep keystore backed up outside GitHub.

The app checks GitHub's latest Release on launch and on demand and downloads an update using DownloadManager. Android may require installer permission and user confirmation; installation is not silent. Return to the app after download for installation. **Do not declare v1.0 finished until an installed signed release successfully updates to a newer signed release without manually downloading another APK.**

## Voice-note defaults for subsequent development

"Bookmark" activation; record and transcribe; stop using "Save note" or four seconds of silence; keep original audio; say "Note saved"; and jump back 15 seconds when reopening bookmarks.

## Important voice activation limitations

The new voice activation mode is an **experimental foreground-only** recognizer session. Enable it while the app is visible and a book is playing. It is disabled on app pause; no claim of lock-screen wake-word support is made. Android recognizers are session-based and may stop or hear audiobook speech instead of the user. This mode cannot run simultaneously with the M4A recorder; the spoken Save note command and automatic transcription of retained audio remain unfinished. Do not rely on this feature while driving until validated hands-free on an actual device.

## Experimental background hands-free mode

The player now offers **Start hands-free mode (experimental)**. It starts a foreground service while the screen is visible, after microphone permission is granted. A persistent microphone notification has a Stop listening action. Android's speech recognizer listens in repeating sessions for “Bookmark”. On recognition, the service pauses the active local audiobook, records and saves an M4A file tagged to its playback position after roughly four seconds of detected quiet, and resumes playback. Notes are persisted to the same library preferences and will appear in the bookmark list when the app view is refreshed.

This is an experimental integration, **not** a promise of reliable hands-free operation while driving or with Bluetooth. Android speech recognition sessions may stop in the background, may use a network service, and can be disrupted by audiobook narration or audio routing. Microphone foreground service must be initiated while the app is visible on recent Android versions. The wake phrase is not an offline dedicated wake-word detector, and voice-note transcription and a spoken Save note command remain future work.
