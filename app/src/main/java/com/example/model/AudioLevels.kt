package com.example.model

/**
 * Real-time audio indicators for the 8 VU meters:
 * - 6 custom routing & processing levels:
 *    1. inPreTrim: Input signal before pre-gain
 *    2. inPostTrim: Input signal after trimmer pre-gain
 *    3. crossoverLow: Low frequency band energy
 *    4. crossoverMid: Mid frequency band energy
 *    5. crossoverHigh: High frequency band energy
 *    6. delayReturn: Delay effect wet return signal
 * - 2 dedicated Main Output indicators:
 *    7. masterLeft: Final left channel output
 *    8. masterRight: Final right channel output
 *
 * All values are normalized between 0.0f (-60 dBFS) and 1.0f (+6 dBFS headroom)
 * where 0.909f represents nominal 0 dBFS.
 */
data class AudioLevels(
    val inPreTrimRms: Float = 0f,
    val inPreTrimPeak: Float = 0f,
    val inPostTrimRms: Float = 0f,
    val inPostTrimPeak: Float = 0f,
    val crossoverLowRms: Float = 0f,
    val crossoverLowPeak: Float = 0f,
    val crossoverMidRms: Float = 0f,
    val crossoverMidPeak: Float = 0f,
    val crossoverHighRms: Float = 0f,
    val crossoverHighPeak: Float = 0f,
    val delayReturnRms: Float = 0f,
    val delayReturnPeak: Float = 0f,
    val masterLeftRms: Float = 0f,
    val masterLeftPeak: Float = 0f,
    val masterRightRms: Float = 0f,
    val masterRightPeak: Float = 0f
) {
    companion object {
        const val MIN_DB = -60.0f
        const val MAX_DB = 6.0f
        const val NOMINAL_0DB_NORM = 60.0f / 66.0f // ~0.909f

        fun normToDb(norm: Float): Float {
            return MIN_DB + (norm.coerceIn(0f, 1f) * (MAX_DB - MIN_DB))
        }

        fun dbToNorm(dB: Float): Float {
            return ((dB - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)
        }
    }
}

