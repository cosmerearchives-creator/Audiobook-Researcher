package com.cosmerearchives.audiobookresearcher

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.*
import kotlin.concurrent.thread

/** Single-purpose, offline 16 kHz Bookmark detector. */
class OfflineBookmarkDetector(private val context: Context, private val onDetected: Runnable, private val onError: java.util.function.Consumer<String>) {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var microphone: AudioRecord? = null
    private val main = Handler(Looper.getMainLooper())

    @Synchronized fun start() {
        if (running) return
        running = true
        worker = thread(name = "offline-bookmark-detector") {
            var spotter: KeywordSpotter? = null
            var stream: OnlineStream? = null
            var detected = false
            try {
                val dir = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
                val config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = "$dir/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                            decoder = "$dir/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                            joiner = "$dir/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
                        ),
                        tokens = "$dir/tokens.txt",
                        modelType = "zipformer2"
                    ),
                    keywordsFile = "$dir/keywords.txt",
                    keywordsThreshold = 0.25f
                )
                spotter = KeywordSpotter(assetManager = context.assets, config = config)
                stream = spotter.createStream()
                val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(size > 0) { "No compatible microphone buffer" }
                val mic = AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
                microphone = mic
                require(mic.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
                mic.startRecording()
                val buffer = ShortArray(1600)
                while (running) {
                    val n = mic.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    val samples = FloatArray(n) { buffer[it] / 32768.0f }
                    stream.acceptWaveform(samples, 16000)
                    while (running && spotter.isReady(stream)) {
                        spotter.decode(stream)
                        val result = spotter.getResult(stream).keyword
                        if (result.isNotBlank()) {
                            running = false
                            detected = true
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) main.post { onError.accept(e.message ?: e.javaClass.simpleName) }
            } finally {
                try { microphone?.stop() } catch (_: Exception) {}
                microphone?.release()
                microphone = null
                stream?.release()
                spotter?.release()
                running = false
                if (detected) main.post { onDetected.run() }
            }
        }
    }
    fun stop() {
        running = false
        try { microphone?.stop() } catch (_: Exception) {}
    }
}
