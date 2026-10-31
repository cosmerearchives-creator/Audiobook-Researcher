# Audiobook Researcher

Android audiobook player with voice-triggered, timestamped research notes. Currently in **Milestone 1: Android foundation and updater**; audiobook playback and recording are not implemented yet.

## Build

Requires JDK 17, Android SDK 35, and Gradle 8.11.1. Run `gradle :app:assembleDebug`.

## Automatic updates — foundational requirement

The app checks the latest GitHub Release on launch and on demand, compares the tag `v0.1.<number>` against the installed APK versionCode, downloads `audiobook-researcher.apk` via Android DownloadManager, and launches Android's installer on return to the app. Android may require permission and a confirmation. Updates are not silent, and no update occurs during recording (recording not implemented yet).

Each push to `main` runs Android CI and produces a debug build artifact. To publish **installable, updatable signed releases**, set these GitHub Actions repository secrets:

- `ANDROID_KEYSTORE_BASE64`: base64 of the production release keystore
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep the original keystore and passwords backed up securely outside the repository. **All future releases must use the same signing certificate and application ID.** Never commit a keystore. Debug artifacts are not interchangeable with signed production updates.

Once secrets are configured, the workflow builds a signed APK and publishes it to GitHub Releases automatically. Each release uses the Actions run number for the APK versionCode and tag, making the updater's version check deterministic. The repository must allow Actions write access to contents.

## First-install and updater acceptance test

1. Configure signing secrets, push to main, and verify a release contains `audiobook-researcher.apk`.
2. Install that **signed** release APK on a device (not the debug APK).
3. Push a small change to main and verify a newer release appears.
4. Open the installed app, accept download, return to it when download finishes, grant Android's unknown-app install permission if prompted, and approve the installation.
5. Verify the app updated without visiting GitHub to fetch another APK.

Status: **not device-tested**. Do not treat v1.0 as finished until the above end-to-end test passes.

## Planned app features

Local MP3/M4B player, chapter support, notes triggered by “Hey Bookmark,” speech-to-text, retention of original voice audio, finish via “Save note” or 4 seconds of silence, spoken confirmation, 15-second context rewind, and best-effort external player integration.
