package com.example.model

enum class FilterType(val displayName: String, val shortName: String) {
    PEAK("Peaking EQ (Bell)", "PEAK"),
    LOW_SHELF("Low Shelf", "LOSH"),
    HIGH_SHELF("High Shelf", "HISH"),
    LOW_PASS("Low Pass (LPF)", "LPF"),
    HIGH_PASS("High Pass (HPF)", "HPF"),
    NOTCH("Band Notch", "NOTCH"),
    BAND_PASS("Band Pass (BPF)", "BPF")
}
