# Audiobook Researcher

An Android audiobook research app with a muted-purple, light-neutral interface.

## Current development status

The first local-player iteration includes:
- Import local audio through Android's document picker with persistent access permissions.
- A saved audiobook library with resume positions.
- A basic player with play/pause, seek, 15-second rewind, and 30-second skip.
- A light neutral background, white rounded cards, and muted purple controls.
- The built-in GitHub Release updater from the initial foundation.
- Timestamped notes for each audiobook, saved locally across updates.
- Add notes by typing or using Android speech dictation (where a recognizer is available).
- Jump to the note timestamp with a 15-second context rewind.

**Not yet implemented:** continuous voice wake word, original voice recording retention, automatic silence detection, chapter parsing, external service controls, background media service, Bluetooth/lock-screen controls. Speech dictation currently uses an external Android recognizer and may require a network connection. This player is an early foundation and will not yet continue through all background playback conditions.

## Building and updates

JDK 17, Android SDK 35, Gradle 8.11.1. Run `gradle :app:assembleDebug`.

Each main-branch push triggers GitHub Actions. With the four signing secrets configured, CI publishes a signed release APK named `audiobook-researcher.apk` as `v0.1.<run_number>`. All updates require the original signing certificate. Never install the debug APK when testing production updates.

Configure repository Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Keep keystore backed up outside GitHub.

The app checks GitHub's latest Release on launch and on demand and downloads an update using DownloadManager. Android may require installer permission and user confirmation; installation is not silent. Return to the app after download for installation. **Do not declare v1.0 finished until an installed signed release successfully updates to a newer signed release without manually downloading another APK.**

## Voice-note defaults for subsequent development

"Hey Bookmark" activation; record and transcribe; stop using "Save note" or four seconds of silence; keep original audio; say "Note saved"; and jump back 15 seconds when reopening bookmarks.
