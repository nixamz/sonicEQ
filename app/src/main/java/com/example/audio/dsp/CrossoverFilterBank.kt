package com.example.audio.dsp

import com.example.model.FilterType
import kotlin.math.pow

/**
 * 3-Way Crossover Filter Bank.
 * Splits incoming audio into:
 * - Low Band (< crossoverLowFreq)
 * - Mid Band (crossoverLowFreq .. crossoverHighFreq)
 * - High Band (> crossoverHighFreq)
 *
 * Applies individual crossover fader gains (Low Fade, Mid Fade, High Fade)
 * and measures individual band RMS energy for the VU meters.
 */
class CrossoverFilterBank {
    private val lowPassL = BiquadFilter()
    private val lowPassR = BiquadFilter()

    private val midHighPassL = BiquadFilter()
    private val midHighPassR = BiquadFilter()
    private val midLowPassL = BiquadFilter()
    private val midLowPassR = BiquadFilter()

    private val highPassL = BiquadFilter()
    private val highPassR = BiquadFilter()

    private var lowFreq: Float = 250.0f
    private var highFreq: Float = 4000.0f
    private var lowGainLinear: Float = 1.0f
    private var midGainLinear: Float = 1.0f
    private var highGainLinear: Float = 1.0f

    init {
        updateFrequencies(250.0f, 4000.0f)
        updateGains(0.0f, 0.0f, 0.0f)
    }

    fun updateFrequencies(lowCutHz: Float, highCutHz: Float, sampleRate: Float = 48000.0f) {
        lowFreq = lowCutHz.coerceIn(40.0f, 1000.0f)
        highFreq = highCutHz.coerceIn(1200.0f, 16000.0f)

        // Q = 0.7071 for Butterworth alignment
        val qButterworth = 0.7071f
        lowPassL.updateCoefficients(FilterType.LOW_PASS, lowFreq, 0f, qButterworth, sampleRate)
        lowPassR.updateCoefficients(FilterType.LOW_PASS, lowFreq, 0f, qButterworth, sampleRate)

        midHighPassL.updateCoefficients(FilterType.HIGH_PASS, lowFreq, 0f, qButterworth, sampleRate)
        midHighPassR.updateCoefficients(FilterType.HIGH_PASS, lowFreq, 0f, qButterworth, sampleRate)
        midLowPassL.updateCoefficients(FilterType.LOW_PASS, highFreq, 0f, qButterworth, sampleRate)
        midLowPassR.updateCoefficients(FilterType.LOW_PASS, highFreq, 0f, qButterworth, sampleRate)

        highPassL.updateCoefficients(FilterType.HIGH_PASS, highFreq, 0f, qButterworth, sampleRate)
        highPassR.updateCoefficients(FilterType.HIGH_PASS, highFreq, 0f, qButterworth, sampleRate)
    }

    fun updateGains(lowDb: Float, midDb: Float, highDb: Float) {
        lowGainLinear = dbToLinear(lowDb)
        midGainLinear = dbToLinear(midDb)
        highGainLinear = dbToLinear(highDb)
    }

    private fun dbToLinear(db: Float): Float {
        return if (db <= -47.0f) 0.0f else 10.0.pow(db.toDouble() / 20.0).toFloat()
    }

    /**
     * Processes a single stereo frame and returns the summed stereo output.
     * Also outputs individual band amplitudes into the provided bandMetrics out array:
     * bandMetrics[0] = lowAmp, bandMetrics[1] = midAmp, bandMetrics[2] = highAmp
     */
    fun processStereo(
        inL: Float,
        inR: Float,
        outSum: FloatArray, // size >= 2
        bandMetrics: FloatArray // size >= 3
    ) {
        // Low Band
        val lowL = lowPassL.processSampleLeft(inL)
        val lowR = lowPassR.processSampleRight(inR)

        // Mid Band
        val midL = midLowPassL.processSampleLeft(midHighPassL.processSampleLeft(inL))
        val midR = midLowPassR.processSampleRight(midHighPassR.processSampleRight(inR))

        // High Band
        val highL = highPassL.processSampleLeft(inL)
        val highR = highPassR.processSampleRight(inR)

        // Track energy
        val lowAmp = (kotlin.math.abs(lowL) + kotlin.math.abs(lowR)) * 0.5f
        val midAmp = (kotlin.math.abs(midL) + kotlin.math.abs(midR)) * 0.5f
        val highAmp = (kotlin.math.abs(highL) + kotlin.math.abs(highR)) * 0.5f
        bandMetrics[0] = lowAmp
        bandMetrics[1] = midAmp
        bandMetrics[2] = highAmp

        // Scaled sum
        outSum[0] = (lowL * lowGainLinear) + (midL * midGainLinear) + (highL * highGainLinear)
        outSum[1] = (lowR * lowGainLinear) + (midR * midGainLinear) + (highR * highGainLinear)
    }
}
