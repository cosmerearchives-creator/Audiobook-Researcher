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

## Offline Bookmark detector integration (October 2026)

The current source now attempts to use the sherpa-onnx v2.0.1 Android AAR and the English GigaSpeech Zipformer keyword-spotting model, with a generated BOOKMARK keyword file. The Android build workflow downloads these files and checks the published AAR SHA-256. The hands-free microphone service has been connected to a Kotlin AudioRecord/KeywordSpotter bridge rather than repeating Android SpeechRecognizer sessions. The bridge releases the microphone before starting the AAC/M4A note recorder.

**Verification status:** Source changes are pushed; CI compilation, APK installation, native loading, real-time detection and background/lock-screen behavior must still be confirmed. No reliability claims are made. The legacy foreground SpeechRecognizer code still exists in MainActivity but is not used by the Hands-Free Mode button. Note transcription and spoken Save note remain incomplete.

## User-configurable wake phrases

The player now includes **Wake phrase → Edit phrase** under the audiobook controls.
Choose an English phrase of 1–5 words; it is stored locally in the existing
`library` SharedPreferences. The default is **Hey Bookmark** rather than a
single-word trigger to reduce accidental activations during narration.
Saving a new phrase stops the current listening session if one is active.
Tap **Start hands-free mode** to start listening with the newly selected phrase.

At APK build time, `scripts/make-bookmark-keyword.py` exports the English
SentencePiece BPE piece-score vocabulary alongside the model. At runtime,
`OfflineBookmarkDetector` tokenizes the user's new phrase and passes the
tokenized sequence to sherpa-onnx `KeywordSpotter.createStream(...)`.
No new APK or cloud service is intended for subsequent phrase edits.

**Testing required:** Verify the generated token sequences match the chosen
sherpa model and confirm live microphone detection, false-trigger rates,
background listening, and the GitHub Actions build on a device. This feature
has been pushed but has not yet been verified as a working signed APK.

## Wake-word tuning and audiobook auto-ducking

The audiobook player's **Wake detection settings & test** panel now offers:
- Detection sensitivity (0–100), mapped to sherpa-onnx's keyword threshold.
- Speech activity gate (0–80), controlling when voice activity causes audio ducking.
- Live microphone level sampled from the 16 kHz wake-word microphone stream.
- Number of successful wake-word detections.
- A test mode that counts wake-word detections without recording notes or pausing playback.

During hands-free listening, sufficiently strong microphone activity can temporarily lower the **app's own audiobook playback volume by 80%** (to 20% original). Volume returns when activity subsides, on successful detection, when recording stops, or when the listening service shuts down. This does **not** change the phone's system-wide media volume. A cooldown reduces rapid volume pumping. The activity gate is a basic amplitude heuristic, not speaker isolation: audiobook narration or road noise may trigger it. Tuning sliders take effect after restarting hands-free listening; the settings panel stops an active session when saved.

The above controls are committed but require CI and on-device verification. Avoid driving while interacting with the diagnostic screen.


## Driving-oriented player UI and cover artwork

The active player now appears near the top of the screen ahead of the library,
notes, and settings. Its three playback controls use 78dp-tall hit targets,
with a wider central play/pause control; 15-second back and 30-second forward
remain available. Artwork and playback progress are presented in the same
high-contrast card, retaining the muted-purple application style.

Because Android's Storage Access Framework does not grant access to sibling
files merely from an imported audio document, tap **Find cover in audiobook
folder** once and select the folder containing the book. The app persists
permission to this folder and searches for JPG/JPEG/PNG/WEBP images, prioritizing
cover.*, folder.*, front.*, and filenames containing "cover" or "artwork".
The selected image URI is saved per book and remains available after app
restarts. It does not request whole-device storage permissions. Artwork folder
selection is not intended to be performed while driving.

These source changes still require CI and real-device verification.
