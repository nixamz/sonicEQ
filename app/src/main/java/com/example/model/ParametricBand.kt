package com.example.model

/**
 * Represents a single band in the 1-60 parametric EQ system.
 *
 * @param id Unique identifier (1..60)
 * @param frequency Center frequency in Hertz (20.0 Hz to 20000.0 Hz)
 * @param gainDb Gain in decibels (-24.0 dB to +24.0 dB)
 * @param q Quality factor / resonance (0.1 to 10.0, default ~1.414)
 * @param filterType Filter curve shape (Peaking, Shelf, Cut, etc.)
 * @param isEnabled Whether this individual band is active or bypassed
 */
data class ParametricBand(
    val id: Int,
    val frequency: Float,
    val gainDb: Float = 0.0f,
    val q: Float = 1.414f,
    val filterType: FilterType = FilterType.PEAK,
    val isEnabled: Boolean = true
)
