package com.example

import com.example.audio.dsp.BiquadFilter
import com.example.model.FilterType
import com.example.model.MixerConfig
import com.example.model.ParametricBand
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun `verify 60 band parametric eq generation`() {
        val bands60 = MixerConfig.defaultBands(60)
        assertEquals(60, bands60.size)
        assertEquals(20f, bands60.first().frequency, 5f)
        assertEquals(20000f, bands60.last().frequency, 100f)
        assertEquals(1, bands60.first().id)
        assertEquals(60, bands60.last().id)
    }

    @Test
    fun `verify biquad filter peaking calculation`() {
        val filter = BiquadFilter()
        filter.updateCoefficients(FilterType.PEAK, 1000f, 6.0f, 1.414f, 48000f)

        // At 1000Hz, magnitude response should boost by ~6dB
        val magAtCenter = filter.getMagnitudeDbAt(1000f, 48000f)
        assertTrue("Expected ~6dB boost at center freq, got $magAtCenter", abs(magAtCenter - 6.0f) < 0.2f)

        // At 50Hz, magnitude should remain close to 0dB
        val magAtLow = filter.getMagnitudeDbAt(50f, 48000f)
        assertTrue("Expected ~0dB at far low freq, got $magAtLow", abs(magAtLow) < 0.3f)
    }

    @Test
    fun `verify biquad shelf filters`() {
        val lowShelf = BiquadFilter()
        lowShelf.updateCoefficients(FilterType.LOW_SHELF, 200f, 8.0f, 0.707f, 48000f)

        val magSub = lowShelf.getMagnitudeDbAt(50f, 48000f)
        assertTrue("Expected ~8dB boost on low shelf, got $magSub", abs(magSub - 8.0f) < 0.5f)

        val highShelf = BiquadFilter()
        highShelf.updateCoefficients(FilterType.HIGH_SHELF, 8000f, -6.0f, 0.707f, 48000f)

        val magHigh = highShelf.getMagnitudeDbAt(16000f, 48000f)
        assertTrue("Expected ~-6dB cut on high shelf, got $magHigh", abs(magHigh - (-6.0f)) < 0.5f)
    }

    @Test
    fun `verify biquad state processing produces stable output`() {
        val filter = BiquadFilter()
        filter.updateCoefficients(FilterType.PEAK, 1000f, 3.0f, 1.414f, 48000f)

        var lastOut = 0.0f
        // Process 100 samples of 1000Hz sine wave
        for (i in 0 until 100) {
            val sample = kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0).toFloat()
            lastOut = filter.processSampleLeft(sample)
            assertFalse("Filter output became NaN", lastOut.isNaN())
            assertFalse("Filter output became Infinite", lastOut.isInfinite())
        }
    }

    @Test
    fun `verify AudioMixerDspEngine teardownCapture is idempotent and safe`() {
        val engine = com.example.audio.dsp.AudioMixerDspEngine()
        // Calling teardownCapture when stopped must succeed cleanly and reset state
        engine.teardownCapture()
        assertFalse(engine.isPlaying.value)
        assertFalse(engine.isSystemCaptureActive.value)
        assertEquals("STOPPED (Clean)", engine.audioCaptureStatus.value)

        // Multiple consecutive teardowns must not throw any exception
        engine.teardownCapture()
        assertEquals("STOPPED (Clean)", engine.audioCaptureStatus.value)
    }

    @Test
    fun `verify combined frequency response calculation is bounded`() {
        val engine = com.example.audio.dsp.AudioMixerDspEngine()
        val resp1k = engine.computeCombinedFrequencyResponseDb(1000f)
        assertTrue("Response at 1kHz should be finite", !resp1k.isNaN() && !resp1k.isInfinite())
        assertTrue("Response at 1kHz within bounds", resp1k in -36f..36f)
    }
}
