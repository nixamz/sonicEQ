package com.example.model

enum class AudioSourceMode(val label: String, val description: String) {
    SYSTEM_CAPTURE("System Audio (MediaProjection)", "Captures real-time internal audio from other apps (Spotify, YouTube, Games) via AudioPlaybackCapture API"),
    MIC_INPUT("Live Microphone", "Hardware microphone input via AudioRecord"),
    CALIBRATION_NOISE("Pink Noise Calibration", "Acoustic calibration pink noise")
}
