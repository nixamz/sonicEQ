# sonicEQ

**A highly optimized, real-time Android audio mixer and DSP engine built on a strict zero-allocation architecture, explicitly hardcoded for the Snapdragon 750G (Samsung Galaxy A42 5G) on Android 12.**

## Description
This is a DSP application featuring a 60-band EQ, specifically optimized for the Snapdragon 750G engine or the Samsung A42 5G (Android 12). 

This application operates as a bare-metal Digital Signal Processing (DSP) engine designed to intercept and process internal Android audio streams in real-time. Built to aggressively bypass Android 12's strict memory management and privacy restrictions, it captures raw PCM audio from system applications via `AudioPlaybackCapture` (MediaProjection API), processes the signal through a custom 60-band EQ engine, and routes it back out via `AudioTrack`.

The entire audio pipeline operates on a strict **Zero-Allocation architecture**. Absolutely no new objects (such as `FloatArray` instantiations or wrapper classes) are created inside the main audio read/write loop. This design guarantees the DSP engine remains completely immune to Kotlin Garbage Collector (GC) pauses, eliminates Out of Memory (OOM) crashes, and ensures the Jetpack Compose UI runs flawlessly at 60fps under extreme computational load.

## Key Features & Technical Architecture
*   **Android 12 Audio Policy Bypass:** Implements a strict asynchronous Foreground Service lifecycle to handle the `MediaProjection` token, successfully preventing the fatal `could not register audio policy` system rejection.
*   **Int16-to-Float32 Pipeline:** Circumvents hardware abstraction layer (HAL) limitations by capturing audio in `ENCODING_PCM_16BIT`, normalizing it to `Float32` for high-precision DSP calculations, and converting it back to 16-bit for output streaming.
*   **Zero-Allocation 60-Band EQ:** Transposed Direct-Form II Biquad filters built entirely using flat primitive array manipulation. Computationally overkill, yet highly efficient.
*   **3-Way Crossover & Delay Ringbuffer:** Includes frequency-split faders (Low/Mid/High) and stereo feedback delay manipulation.
*   **Throttle-Safe VU Metering:** Real-time audio level rendering (Peak/RMS) mapped to a logarithmic dBFS scale, smoothed via an Exponential Moving Average (EMA) to prevent the DSP loop from choking the Main UI Thread.

## ⚠️ CRITICAL USAGE NOTE: AUDIO ROUTING & MUTING
Due to Android system limitations, the `AudioPlaybackCapture` API functions strictly as a sniffer/recorder, not an audio interceptor. **This application cannot natively mute the original source audio.** 

To prevent audio duplication (hearing both the unprocessed source and the DSP output simultaneously through the speaker), you MUST manually lower the volume inside your source media player (e.g., using a player with built-in, independent volume controls) while keeping the Android master volume up for this mixer. Otherwise, the original source audio will persist on the speaker alongside the processed output.

## Hardware Disclaimer
This codebase contains buffer configurations and thread management priorities strictly calibrated for the Qualcomm Snapdragon 750G SoC. Running this engine on different architectures (e.g., MediaTek) or operating systems with aggressive background execution limits (e.g., HyperOS) will likely require manual recalibration of the audio frame buffer sizes to prevent dropouts.
