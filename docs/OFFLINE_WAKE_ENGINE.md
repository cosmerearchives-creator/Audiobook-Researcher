# Offline “Bookmark” wake-word integration

Status: **asset preparation only; not yet integrated into the production APK.**

Goal: replace Android SpeechRecognizer sessions in HandsFreeService with continuous on-device
keyword spotting backed by sherpa-onnx, keeping a user-initiated microphone foreground service.

The separately dispatchable GitHub Actions workflow `Prepare offline wake-word assets` downloads:
- English Zipformer keyword spotter model: `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`.
- sherpa-onnx Android native runtime, pinned to `v2.0.1`.

The workflow validates archive sizes and records SHA-256 checksums; this is not yet
cryptographic provenance verification (upstream reference checksums should be pinned later).

Remaining implementation:
1. Bundle the exact Android JNI libraries and keyword-spotting Kotlin/Java wrapper matching the chosen runtime.
2. Bundle the required model encoder/decoder/joiner, tokens, and generated English keyword tokens/config.
3. Feed continuous 16-kHz mono PCM AudioRecord samples to the keyword spotter inside HandsFreeService.
4. Stop and release AudioRecord before starting the M4A note recorder; resume wake listening after saving.
5. Add diagnostics for detection/false triggers and microphone permissions.
6. Build signed APK and verify on-device in foreground, screen locked, and with Bluetooth.

No new signup, paid account, or Picovoice key required. Do not claim the new engine is active
until it is hooked up and verified; the existing APK still uses the Android recognizer.

References:
- https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxKws
- https://k2-fsa.github.io/sherpa/onnx/kws/index.html
