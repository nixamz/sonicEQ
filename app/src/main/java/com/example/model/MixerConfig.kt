package com.example.model

/**
 * Complete serializable mixer configuration state.
 * Contains:
 * - 1 to 60 EQ parametric bands
 * - Trimmer pre-gain (-24 to +24 dB)
 * - Main input fader (-inf / -48 to +12 dB)
 * - Main output fader (-inf / -48 to +12 dB)
 * - Low, Mid, High crossover faders (-24 to +12 dB)
 * - Crossover split frequencies (Low/Mid split, Mid/High split)
 * - Delay FX parameters (Time ms, Feedback %, Mix %)
 * - Panning position (-1.0 Left to +1.0 Right)
 */
data class MixerConfig(
    val name: String = "Current Session",
    val preGainDb: Float = 0.0f,
    val pan: Float = 0.0f, // -1.0 = 100% Left, 0.0 = Center, +1.0 = 100% Right
    val mainInputGainDb: Float = 0.0f,
    val mainOutputGainDb: Float = 0.0f,
    val lowFadeDb: Float = 0.0f,
    val midFadeDb: Float = 0.0f,
    val highFadeDb: Float = 0.0f,
    val crossoverLowFreq: Float = 250.0f, // Hz
    val crossoverHighFreq: Float = 4000.0f, // Hz
    val delayTimeMs: Float = 240.0f, // ms
    val delayFeedback: Float = 0.35f, // 0..0.95
    val delayMix: Float = 0.25f, // 0..1.0
    val delayEnabled: Boolean = true,
    val eqEnabled: Boolean = true,
    val limiterEnabled: Boolean = true,
    val bands: List<ParametricBand> = defaultBands(10)
) {
    companion object {
        fun defaultBands(count: Int): List<ParametricBand> {
            val clampedCount = count.coerceIn(1, 60)
            val minFreq = 20.0f
            val maxFreq = 20000.0f
            val logMin = Math.log10(minFreq.toDouble())
            val logMax = Math.log10(maxFreq.toDouble())

            return (0 until clampedCount).map { i ->
                val freq = if (clampedCount == 1) {
                    1000.0f
                } else {
                    val frac = i.toDouble() / (clampedCount - 1)
                    val logFreq = logMin + frac * (logMax - logMin)
                    Math.pow(10.0, logFreq).toFloat()
                }
                val roundedFreq = when {
                    freq < 100f -> Math.round(freq).toFloat()
                    freq < 1000f -> (Math.round(freq / 10f) * 10).toFloat()
                    else -> (Math.round(freq / 50f) * 50).toFloat()
                }
                val type = when {
                    i == 0 && clampedCount > 4 -> FilterType.LOW_SHELF
                    i == clampedCount - 1 && clampedCount > 4 -> FilterType.HIGH_SHELF
                    else -> FilterType.PEAK
                }
                ParametricBand(
                    id = i + 1,
                    frequency = roundedFreq.coerceIn(20f, 20000f),
                    gainDb = 0.0f,
                    q = 1.414f,
                    filterType = type,
                    isEnabled = true
                )
            }
        }
    }
}
