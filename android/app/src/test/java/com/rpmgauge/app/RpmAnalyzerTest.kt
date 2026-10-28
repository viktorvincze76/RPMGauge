package com.rpmgauge.app

import com.rpmgauge.app.RpmAnalyzer.Companion.FFT_SIZE
import com.rpmgauge.app.RpmAnalyzer.Companion.SAMPLE_RATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class RpmAnalyzerTest {
    /** Sum of sine tones as 16-bit PCM; each pair is (frequency Hz, amplitude 0..1). */
    private fun tones(vararg parts: Pair<Double, Double>) = ShortArray(FFT_SIZE) { i ->
        val t = i.toDouble() / SAMPLE_RATE
        (parts.sumOf { (hz, amp) -> amp * sin(2 * PI * hz * t) } * 32767 * 0.9).toInt().toShort()
    }

    @Test
    fun pureToneInRange() {
        // 500 Hz → 30,000 RPM (the value quoted in the App Review notes)
        // Within 0.25%: parabolic interpolation on a Hann-windowed power spectrum has a small bias
        for (hz in listOf(400.0, 500.0, 620.0, 750.0)) {
            val expected = hz * 60
            assertEquals(expected, RpmAnalyzer.analyze(tones(hz to 1.0), bladeCount = 1)!!, expected * 0.0025)
        }
    }

    @Test
    fun toneOutOfRangeIsIgnored() {
        assertNull(RpmAnalyzer.analyze(tones(200.0 to 1.0), bladeCount = 1))
        assertNull(RpmAnalyzer.analyze(tones(1200.0 to 1.0), bladeCount = 1))
        assertNull(RpmAnalyzer.analyze(tones(900.0 to 1.0), bladeCount = 1))
        assertNull(RpmAnalyzer.analyze(ShortArray(FFT_SIZE), bladeCount = 1))
    }

    @Test
    fun bladePassFrequencyIsCorrected() {
        // Engine at 400 Hz (24,000 RPM) with a louder 2-blade prop harmonic at 800 Hz
        val pcm = tones(400.0 to 0.3, 800.0 to 0.6)
        assertEquals(48000.0, RpmAnalyzer.analyze(pcm, bladeCount = 1)!!, 100.0)
        assertEquals(24000.0, RpmAnalyzer.analyze(pcm, bladeCount = 2)!!, 100.0)
    }
}
