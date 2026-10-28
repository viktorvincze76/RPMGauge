package com.rpmgauge.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class RpmAnalyzer(private val context: Context) {
    private val _currentRpm = MutableStateFlow(0.0)
    val currentRpm: StateFlow<Double> = _currentRpm.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Number of propeller blades — used to detect and correct BPF harmonic interference
    @Volatile
    var bladeCount: Int = 2

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    /** Caller must hold the RECORD_AUDIO permission. */
    @SuppressLint("MissingPermission")
    fun start() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "Audio input is not supported on this device" }

        val record = AudioRecord(
            audioSource(), SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(minBuffer, FFT_SIZE * 2 * 2)
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("Could not open the microphone")
        }
        record.startRecording()

        audioRecord = record
        running = true
        _isRunning.value = true
        thread = Thread({ readLoop(record) }, "RpmAnalyzer").also { it.start() }
    }

    fun stop() {
        running = false
        audioRecord?.stop()  // unblocks a pending read()
        thread?.join(500)
        thread = null
        audioRecord?.release()
        audioRecord = null
        _isRunning.value = false
        _currentRpm.value = 0.0
    }

    // Prefer the raw, unfiltered mic signal (like AVAudioSession's .measurement mode)
    private fun audioSource(): Int {
        val am = context.getSystemService(AudioManager::class.java)
        val unprocessed = am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return if (unprocessed == "true") MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.MIC
    }

    private fun readLoop(record: AudioRecord) {
        val pcm = ShortArray(FFT_SIZE)
        while (running) {
            var offset = 0
            while (offset < FFT_SIZE && running) {
                val n = record.read(pcm, offset, FFT_SIZE - offset)
                if (n <= 0) return
                offset += n
            }
            if (running) process(pcm)
        }
    }

    private fun process(pcm: ShortArray) {
        val rpm = analyze(pcm, bladeCount) ?: return
        if (running) _currentRpm.value = rpm
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val FFT_SIZE = 4096

        // Target: 20,000 – 50,000 RPM  =>  333 – 833 Hz fundamental
        // A 2-stroke fires once per revolution, so 1 RPM = 1/60 Hz
        const val MIN_RPM = 20000.0
        const val MAX_RPM = 50000.0
        private const val MIN_HZ = MIN_RPM / 60   // ≈ 333 Hz
        private const val MAX_HZ = MAX_RPM / 60   // ≈ 833 Hz

        private val window = DoubleArray(FFT_SIZE) { 0.5 * (1 - cos(2 * PI * it / FFT_SIZE)) }

        /** Returns the RPM detected in one FFT_SIZE block of 16-bit PCM, or null if none is in range. */
        internal fun analyze(pcm: ShortArray, bladeCount: Int): Double? {
            // Apply Hann window, then FFT
            val re = DoubleArray(FFT_SIZE) { pcm[it] / 32768.0 * window[it] }
            val im = DoubleArray(FFT_SIZE)
            fft(re, im)

            val half = FFT_SIZE / 2
            val magnitudes = DoubleArray(half) { re[it] * re[it] + im[it] * im[it] }

            // Frequency resolution
            val freqResolution = SAMPLE_RATE.toDouble() / FFT_SIZE
            val minBin = (MIN_HZ / freqResolution).toInt()
            val maxBin = min((MAX_HZ / freqResolution).toInt(), half - 1)
            if (minBin > maxBin) return null

            // Find peak bin in target range
            val maxIdx = (minBin..maxBin).maxBy { magnitudes[it] }

            // A peak on the edge of the search window means the real peak lies outside
            // the target range (e.g. an over-revving engine), so don't report it
            if (maxIdx == minBin || maxIdx == maxBin) return null

            // Parabolic interpolation for sub-bin precision
            val y0 = magnitudes[maxIdx - 1]
            val y1 = magnitudes[maxIdx]
            val y2 = magnitudes[maxIdx + 1]
            val delta = 0.5 * (y0 - y2) / (y0 - 2 * y1 + y2)
            val peak = (maxIdx + delta) * freqResolution

            val rpm = peak * 60.0
            if (rpm < MIN_RPM || rpm > MAX_RPM) return null

            // BPF correction: if bladeCount > 1 the dominant peak might be the blade passage
            // frequency (BPF = fundamental × bladeCount). Check for a sub-harmonic at
            // peak/bladeCount with at least 15% of the BPF magnitude; if found, use it instead.
            var correctedRpm = rpm
            val blades = bladeCount
            if (blades > 1) {
                val fundamentalHz = peak / blades
                val fundamentalRpm = fundamentalHz * 60.0
                if (fundamentalRpm in MIN_RPM..MAX_RPM) {
                    val subBin = (fundamentalHz / freqResolution).toInt()
                    if (subBin in 0 until half && magnitudes[subBin] >= magnitudes[maxIdx] * 0.15) {
                        correctedRpm = fundamentalRpm
                    }
                }
            }

            return correctedRpm
        }

        /** In-place iterative radix-2 FFT. Size must be a power of two. */
        private fun fft(re: DoubleArray, im: DoubleArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    re[i] = re[j].also { re[j] = re[i] }
                    im[i] = im[j].also { im[j] = im[i] }
                }
            }
            var len = 2
            while (len <= n) {
                val angle = -2 * PI / len
                val wRe = cos(angle)
                val wIm = sin(angle)
                for (start in 0 until n step len) {
                    var curRe = 1.0
                    var curIm = 0.0
                    for (k in 0 until len / 2) {
                        val a = start + k
                        val b = a + len / 2
                        val tRe = re[b] * curRe - im[b] * curIm
                        val tIm = re[b] * curIm + im[b] * curRe
                        re[b] = re[a] - tRe
                        im[b] = im[a] - tIm
                        re[a] += tRe
                        im[a] += tIm
                        val nextRe = curRe * wRe - curIm * wIm
                        curIm = curRe * wIm + curIm * wRe
                        curRe = nextRe
                    }
                }
                len = len shl 1
            }
        }
    }
}
