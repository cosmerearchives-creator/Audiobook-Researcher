# Codex

Codex is an Android audiobook player for local audio and timestamped research notes.

## Dictated bookmarks

1. Open an audiobook, then enable **Start a dictated note when opening Codex during playback** in Options.
2. While parked, try **“Hey Google, open Codex”** with a book playing. Opening Codex from its icon with this setting enabled also starts a note during playback.
3. When Android's dictation prompt is ready, speak the note. Codex saves the transcript against the book and playback position captured before dictation, then resumes a book that it paused.

The **Make a note** launcher shortcut starts the same flow for the loaded book, even if playback is paused. Codex declares an Android App Action for opening this feature. Assistant registration and Gemini routing for **“make a note in Codex”** must be verified on the target setup. Test locked-screen and Android Auto behavior separately.

Manual typing and original audio recording remain available from the Notes screen. The dictation prompt uses the device speech recognition service. No wake-word engine or background microphone listener is bundled.

## Building and updates

JDK 17, Android SDK 35, Gradle 8.11.1. Run `gradle :app:assembleDebug`.

Each main-branch push triggers GitHub Actions. With the four signing secrets configured, CI publishes a signed release APK named `audiobook-researcher.apk` as `v0.1.<run_number>`. All updates require the original signing certificate. Never install the debug APK when testing production updates.

Configure repository Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Keep keystore backed up outside GitHub.

The app checks GitHub's latest Release on launch and on demand and downloads an update using DownloadManager. Android may require installer permission and user confirmation; installation is not silent. Return to the app after download for installation. **Do not declare v1.0 finished until an installed signed release successfully updates to a newer signed release without manually downloading another APK.**

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


## Player-first navigation

The app opens the last selected audiobook in the dedicated full-screen Now Playing
screen (paused if it must restore the book into a new PlaybackService). The
top-left hamburger opens **Media Library**, **Notes**, **Options**, and **Tools**.
The Notes screen includes an Add note action.
The library is shown when no audiobook can be restored.

## Audiobooks folder import

In Media Library select **Choose Audiobooks folder** using Android's system folder picker. The app keeps persistent read access to that tree and scans its subfolders for common audiobook audio formats, adds them to the library, and uses nearby cover/folder/front image files when present. **Scan Audiobooks folder** finds newly added files without replacing positions or duplicating existing document URIs. Each discovered audio file is currently an individual library entry; multi-file/part-based audiobook grouping is not yet implemented. Actual playback depends on device codec support.

## Custom books and wrapping

Media Library now offers Create Book. Choose existing scanned audio parts, give
the collection a book title, then arrange its order. Custom groups override
metadata grouping; groups start collapsed, and expanding shows parts, per-part
playback status, wrapping editors, and an ungroup action. Ungrouping preserves
the source files and notes.

A wrapping editor for each part can skip the entire part during continuous
playback, or skip one or more time ranges entered in seconds (e.g.
`0-45, 3500-3560`). Options includes an Include wrapping switch to hear the
original material. Manually opening an entirely wrapped part remains possible.
The service automatically advances through ordered parts within a custom book,
saves part progress, and updates the player title/cover between parts.

Known limits: custom books currently select scanned audio files rather than
folder containers; selecting all files from several folders achieves a similar
group. Timed regions use seconds rather than a visual waveform editor. Metadata
album groups stay collapsed by default but do not auto-advance unless manually
assembled into a custom book. A successful CI build and on-device playback
validation have not yet been confirmed.
