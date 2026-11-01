package com.example.audio.dsp

import com.example.model.FilterType
import kotlin.math.*

/**
 * High-performance Robert Bristow-Johnson (RBJ) Bi-quad IIR Filter.
 * Implements Direct Form II Transposed for stereo channels with minimal memory footprint
 * and high numerical stability at 44.1kHz / 48kHz audio sample rates.
 */
class BiquadFilter {
    // Coefficients normalized by a0
    var b0: Float = 1.0f
        private set
    var b1: Float = 0.0f
        private set
    var b2: Float = 0.0f
        private set
    var a1: Float = 0.0f
        private set
    var a2: Float = 0.0f
        private set

    // State registers for Left channel (Direct Form II Transposed)
    private var s1L: Float = 0.0f
    private var s2L: Float = 0.0f

    // State registers for Right channel (Direct Form II Transposed)
    private var s1R: Float = 0.0f
    private var s2R: Float = 0.0f

    fun resetState() {
        s1L = 0.0f
        s2L = 0.0f
        s1R = 0.0f
        s2R = 0.0f
    }

    /**
     * Recomputes filter coefficients based on the filter type, center frequency,
     * gain in dB, and Q factor.
     */
    fun updateCoefficients(
        type: FilterType,
        frequency: Float,
        gainDb: Float,
        q: Float,
        sampleRate: Float = 48000.0f
    ) {
        val nyquist = sampleRate * 0.495f
        val clampedFreq = frequency.coerceIn(10.0f, nyquist)
        val clampedQ = q.coerceIn(0.1f, 10.0f)
        val a = 10.0.pow(gainDb.toDouble() / 40.0) // sqrt of linear gain for peaking/shelves
        val omega = 2.0 * PI * clampedFreq / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * clampedQ)

        var b0D: Double
        var b1D: Double
        var b2D: Double
        var a0D: Double
        var a1D: Double
        var a2D: Double

        when (type) {
            FilterType.PEAK -> {
                b0D = 1.0 + alpha * a
                b1D = -2.0 * cs
                b2D = 1.0 - alpha * a
                a0D = 1.0 + alpha / a
                a1D = -2.0 * cs
                a2D = 1.0 - alpha / a
            }
            FilterType.LOW_SHELF -> {
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                b0D = a * ((a + 1.0) - (a - 1.0) * cs + twoSqrtAAlpha)
                b1D = 2.0 * a * ((a - 1.0) - (a + 1.0) * cs)
                b2D = a * ((a + 1.0) - (a - 1.0) * cs - twoSqrtAAlpha)
                a0D = (a + 1.0) + (a - 1.0) * cs + twoSqrtAAlpha
                a1D = -2.0 * ((a - 1.0) + (a + 1.0) * cs)
                a2D = (a + 1.0) + (a - 1.0) * cs - twoSqrtAAlpha
            }
            FilterType.HIGH_SHELF -> {
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                b0D = a * ((a + 1.0) + (a - 1.0) * cs + twoSqrtAAlpha)
                b1D = -2.0 * a * ((a - 1.0) + (a + 1.0) * cs)
                b2D = a * ((a + 1.0) + (a - 1.0) * cs - twoSqrtAAlpha)
                a0D = (a + 1.0) - (a - 1.0) * cs + twoSqrtAAlpha
                a1D = 2.0 * ((a - 1.0) - (a + 1.0) * cs)
                a2D = (a + 1.0) - (a - 1.0) * cs - twoSqrtAAlpha
            }
            FilterType.LOW_PASS -> {
                b0D = (1.0 - cs) / 2.0
                b1D = 1.0 - cs
                b2D = (1.0 - cs) / 2.0
                a0D = 1.0 + alpha
                a1D = -2.0 * cs
                a2D = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0D = (1.0 + cs) / 2.0
                b1D = -(1.0 + cs)
                b2D = (1.0 + cs) / 2.0
                a0D = 1.0 + alpha
                a1D = -2.0 * cs
                a2D = 1.0 - alpha
            }
            FilterType.NOTCH -> {
                b0D = 1.0
                b1D = -2.0 * cs
                b2D = 1.0
                a0D = 1.0 + alpha
                a1D = -2.0 * cs
                a2D = 1.0 - alpha
            }
            FilterType.BAND_PASS -> {
                b0D = alpha
                b1D = 0.0
                b2D = -alpha
                a0D = 1.0 + alpha
                a1D = -2.0 * cs
                a2D = 1.0 - alpha
            }
        }

        // Normalize by a0
        val invA0 = (1.0 / a0D).toFloat()
        b0 = (b0D * invA0).toFloat()
        b1 = (b1D * invA0).toFloat()
        b2 = (b2D * invA0).toFloat()
        a1 = (a1D * invA0).toFloat()
        a2 = (a2D * invA0).toFloat()
    }

    /**
     * Processes a single left channel sample.
     */
    fun processSampleLeft(x: Float): Float {
        val y = b0 * x + s1L
        s1L = b1 * x - a1 * y + s2L
        s2L = b2 * x - a2 * y
        return y
    }

    /**
     * Processes a single right channel sample.
     */
    fun processSampleRight(x: Float): Float {
        val y = b0 * x + s1R
        s1R = b1 * x - a1 * y + s2R
        s2R = b2 * x - a2 * y
        return y
    }

    /**
     * Evaluates complex magnitude response in decibels (dB) at a target frequency f.
     * Used for real-time visual curve plotting.
     */
    fun getMagnitudeDbAt(frequency: Float, sampleRate: Float = 48000.0f): Float {
        val omega = 2.0 * PI * frequency / sampleRate
        val cosOmega = cos(omega)
        val cos2Omega = cos(2.0 * omega)
        val sinOmega = sin(omega)
        val sin2Omega = sin(2.0 * omega)

        // Numerator: b0 + b1*e^(-jw) + b2*e^(-2jw)
        val numReal = b0 + b1 * cosOmega + b2 * cos2Omega
        val numImag = -b1 * sinOmega - b2 * sin2Omega

        // Denominator: 1 + a1*e^(-jw) + a2*e^(-2jw)
        val denReal = 1.0 + a1 * cosOmega + a2 * cos2Omega
        val denImag = -a1 * sinOmega - a2 * sin2Omega

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        if (denMagSq < 1e-12) return 0.0f
        val magRatio = numMagSq / denMagSq
        val magDb = (10.0 * log10(max(1e-12, magRatio))).toFloat()
        return magDb.coerceIn(-48.0f, 48.0f)
    }
}
