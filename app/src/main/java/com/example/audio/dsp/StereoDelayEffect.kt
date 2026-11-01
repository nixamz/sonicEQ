package com.example.audio.dsp

import kotlin.math.abs

/**
 * Stereo Delay Effect with circular buffer, fractional delay time interpolation,
 * feedback damping, and wet/dry mix control.
 * Also computes delay wet return energy for the FX VU meter.
 */
class StereoDelayEffect(
    private val maxDelaySeconds: Float = 1.2f,
    private val sampleRate: Float = 48000.0f
) {
    private val bufferSize = (maxDelaySeconds * sampleRate).toInt().coerceAtLeast(1024)
    private val bufferL = FloatArray(bufferSize)
    private val bufferR = FloatArray(bufferSize)
    private var writeIndex = 0

    var delayTimeMs: Float = 240.0f
    var feedback: Float = 0.35f
    var mix: Float = 0.25f
    var isEnabled: Boolean = true

    // Damping one-pole low pass filter for realistic tape/analog delay decay
    private var dampingL = 0.0f
    private var dampingR = 0.0f

    fun reset() {
        bufferL.fill(0.0f)
        bufferR.fill(0.0f)
        writeIndex = 0
        dampingL = 0.0f
        dampingR = 0.0f
    }

    /**
     * Processes stereo input and returns wet/dry mixed stereo output.
     * fxMetric[0] receives the current wet return amplitude for the FX VU meter.
     */
    fun processStereo(
        inL: Float,
        inR: Float,
        out: FloatArray, // size >= 2
        fxMetric: FloatArray // size >= 1
    ) {
        if (!isEnabled || mix <= 0.001f) {
            out[0] = inL
            out[1] = inR
            fxMetric[0] = 0.0f
            return
        }

        // Fractional delay read index
        val delaySamples = (delayTimeMs * 0.001f * sampleRate).coerceIn(10.0f, (bufferSize - 10).toFloat())
        val intDelay = delaySamples.toInt()
        val frac = delaySamples - intDelay

        var readIndex0 = writeIndex - intDelay
        if (readIndex0 < 0) readIndex0 += bufferSize
        var readIndex1 = readIndex0 - 1
        if (readIndex1 < 0) readIndex1 += bufferSize

        // Linear interpolation
        val delayedL = bufferL[readIndex0] * (1.0f - frac) + bufferL[readIndex1] * frac
        val delayedR = bufferR[readIndex0] * (1.0f - frac) + bufferR[readIndex1] * frac

        // High frequency damping (6kHz soft filter)
        dampingL = dampingL * 0.35f + delayedL * 0.65f
        dampingR = dampingR * 0.35f + delayedR * 0.65f

        // Write input + feedback to circular buffer
        val clampedFeedback = feedback.coerceIn(0.0f, 0.95f)
        bufferL[writeIndex] = inL + (dampingL * clampedFeedback)
        bufferR[writeIndex] = inR + (dampingR * clampedFeedback)

        writeIndex++
        if (writeIndex >= bufferSize) {
            writeIndex = 0
        }

        val wetAmp = (abs(delayedL) + abs(delayedR)) * 0.5f * mix
        fxMetric[0] = wetAmp

        // Output mix
        val dry = 1.0f - (mix * 0.5f)
        out[0] = inL * dry + delayedL * mix
        out[1] = inR * dry + delayedR * mix
    }
}
