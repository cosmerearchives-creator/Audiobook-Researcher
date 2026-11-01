package com.cosmerearchives.audiobookresearcher

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.*
import kotlin.concurrent.thread
import org.json.JSONObject
import java.util.Locale

/** Single-purpose, offline 16 kHz Bookmark detector. */
class OfflineBookmarkDetector(private val context: Context, private val onDetected: Runnable, private val onError: java.util.function.Consumer<String>, private val onLevel: java.util.function.IntConsumer) {
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
                val sensitivity = context.getSharedPreferences("library", Context.MODE_PRIVATE).getInt("wakeSensitivity", 60).coerceIn(0,100)
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
                    keywordsFile = "", // Do not load the bundled default Hey Bookmark keyword.
                    keywordsThreshold = 0.50f - sensitivity * 0.004f
                )
                spotter = KeywordSpotter(assetManager = context.assets, config = config)
                val phrase = context.getSharedPreferences("library", Context.MODE_PRIVATE)
                    .getString("wakePhrase", "Hey Bookmark") ?: "Hey Bookmark"
                val tokens = tokenizePhrase(phrase, dir)
                val normalizedPhrase = phrase.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")
                stream = spotter.createStream(tokens.joinToString(" ") + " @" + normalizedPhrase.replace(" ", "_"))
                val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(size > 0) { "No compatible microphone buffer" }
                val mic = AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
                microphone = mic
                require(mic.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
                mic.startRecording()
                val buffer = ShortArray(1600)
                var lastLevel=0L
                while (running) {
                    val n = mic.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    val samples = FloatArray(n) { buffer[it] / 32768.0f }
                    val now = android.os.SystemClock.elapsedRealtime()
                    if(now-lastLevel>=200){
                        val rms = kotlin.math.sqrt(samples.map { it*it }.average()).toFloat()
                        val level = (rms*1200).toInt().coerceIn(0,100)
                        main.post { onLevel.accept(level) }
                        lastLevel=now
                    }
                    stream.acceptWaveform(samples, 16000)
                    while (running && spotter.isReady(stream)) {
                        spotter.decode(stream)
                        val result = spotter.getResult(stream).keyword
                        if (result.isNotBlank() && result.trim().uppercase(Locale.ROOT).replace("_", " ") == normalizedPhrase) {
                            running = false
                            detected = true
                            break
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("WakeDetector","Wake detector failed",e)
                if (running) {
                    val detail = e.javaClass.simpleName + ": " + (e.message ?: "unknown error")
                    context.getSharedPreferences("library",Context.MODE_PRIVATE).edit().putString("wakeLastError",detail).apply()
                    main.post { onError.accept(detail) }
                }
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
    private fun tokenizePhrase(phrase: String, directory: String): List<String> {
        val json = context.assets.open("$directory/bpe-vocab.json").bufferedReader().use { JSONObject(it.readText()) }
        val words = phrase.uppercase(Locale.ROOT).trim().split(Regex("\\s+"))
        val output = mutableListOf<String>()
        for (word in words) {
            val chars = ("▁" + word).map { it.toString() }.toMutableList()
            // SentencePiece BPE merges highest-score adjacent candidate first.
            while (chars.size > 1) {
                var bestIndex = -1
                var bestScore = Double.NEGATIVE_INFINITY
                for (i in 0 until chars.size - 1) {
                    val candidate = chars[i] + chars[i + 1]
                    if (!json.has(candidate)) continue
                    val score = json.optDouble(candidate, Double.NEGATIVE_INFINITY)
                    if (score > bestScore) { bestIndex = i; bestScore = score }
                }
                if (bestIndex < 0) break
                chars[bestIndex] += chars[bestIndex + 1]
                chars.removeAt(bestIndex + 1)
            }
            require(chars.all { json.has(it) }) { "Wake phrase contains unsupported letters" }
            output.addAll(chars)
        }
        require(output.isNotEmpty()) { "Wake phrase cannot be empty" }
        return output
    }
    fun stop() {
        running = false
        try { microphone?.stop() } catch (_: Exception) {}
    }
}
