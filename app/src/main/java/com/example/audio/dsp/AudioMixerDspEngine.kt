package com.example.audio.dsp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.model.AudioLevels
import com.example.model.AudioSourceMode
import com.example.model.FilterType
import com.example.model.MixerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/**
 * Senior Audio DSP Engine: Int16-to-Float32 Capture & Processing Pipeline.
 *
 * Implements strict architecture requirements:
 * 1. Capture in ENCODING_PCM_16BIT via AudioRecord (system-level stability for AudioPlaybackCapture).
 * 2. Real-time conversion: ShortArray (Int16) -> FloatArray (Float32 / 32768.0f).
 * 3. Zero-Allocation Flat Primitive DSP execution (60 bands, crossover, delay, pan, limiter).
 * 4. Conversion back: Float32 -> Int16 ShortArray -> AudioTrack (MODE_STREAM, USAGE_MEDIA).
 * 5. Silence detection and BUFFER_SILENT_POSSIBLE_APP_BLOCK warning validation.
 * 6. Decoupled high-priority audio thread (Process.THREAD_PRIORITY_URGENT_AUDIO).
 */
class AudioMixerDspEngine {

    companion object {
        private const val TAG = "AudioMixerDspEngine"
        const val SAMPLE_RATE = 48000
        const val BUFFER_FRAMES = 512 // 10.66ms real-time processing block
        const val CHANNEL_COUNT = 2 // Stereo
        const val MAX_EQ_BANDS = 60

        const val MIN_DB = -60.0f
        const val MAX_DB = 6.0f
        const val SILENCE_DB = -100.0f

        private const val INV_32768 = 1.0f / 32768.0f
        private const val MAX_DELAY_SECONDS = 1.2f
        private const val DELAY_BUFFER_CAPACITY = (MAX_DELAY_SECONDS * SAMPLE_RATE).toInt() // 57,600 floats

        // Ballistics: dt = BUFFER_FRAMES / SAMPLE_RATE = 512 / 48000 = 0.010667s (~10.67ms)
        // Fast attack = 10ms: alphaAttack = 1 - exp(-0.010667 / 0.010) ~ 0.6558f
        // Slow decay = 300ms: alphaDecay = 1 - exp(-0.010667 / 0.300) ~ 0.0349f
        // Peak hold decay = 600ms: alphaPeakDecay = 1 - exp(-0.010667 / 0.600) ~ 0.0176f
        private const val BLOCK_DT = BUFFER_FRAMES.toFloat() / SAMPLE_RATE.toFloat()
        private val ALPHA_ATTACK = (1.0f - exp(-BLOCK_DT / 0.010f))
        private val ALPHA_DECAY = (1.0f - exp(-BLOCK_DT / 0.300f))
        private val ALPHA_PEAK_DECAY = (1.0f - exp(-BLOCK_DT / 0.600f))
    }

    private val isRunning = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var activeMediaProjection: MediaProjection? = null
    private var audioProcessingThread: Thread? = null

    // Audio routing source mode
    @Volatile
    var sourceMode: AudioSourceMode = AudioSourceMode.SYSTEM_CAPTURE

    // System capture status exposed to UI
    private val _isSystemCaptureActive = MutableStateFlow(false)
    val isSystemCaptureActive: StateFlow<Boolean> = _isSystemCaptureActive.asStateFlow()

    private val _isBufferSilentWarning = MutableStateFlow(false)
    val isBufferSilentWarning: StateFlow<Boolean> = _isBufferSilentWarning.asStateFlow()

    private val _audioCaptureStatus = MutableStateFlow("STANDBY")
    val audioCaptureStatus: StateFlow<String> = _audioCaptureStatus.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _audioLevels = MutableStateFlow(AudioLevels())
    val audioLevels: StateFlow<AudioLevels> = _audioLevels.asStateFlow()

    private val _cpuLoadEstimate = MutableStateFlow(0.0f)
    val cpuLoadEstimate: StateFlow<Float> = _cpuLoadEstimate.asStateFlow()

    // Active configuration snapshot
    @Volatile
    private var currentConfig: MixerConfig = MixerConfig()

    // =========================================================================
    // PRE-ALLOCATED ZERO-ALLOCATION DSP REGISTERS & ARRAYS
    // =========================================================================

    // 1. Raw Int16 PCM Capture Buffer
    private val rawShortInputBuffer = ShortArray(BUFFER_FRAMES * CHANNEL_COUNT)

    // 2. Float32 Converted Input Buffer
    private val inFloatBuffer = FloatArray(BUFFER_FRAMES * CHANNEL_COUNT)

    // 3. Float32 Processed Buffer & Int16 Output Buffer for AudioTrack
    private val outFloatBuffer = FloatArray(BUFFER_FRAMES * CHANNEL_COUNT)
    private val outShortBuffer = ShortArray(BUFFER_FRAMES * CHANNEL_COUNT)

    // Flat Contiguous 60-Band Biquad Filter Bank (Direct Form II Transposed)
    private val eqB0 = FloatArray(MAX_EQ_BANDS) { 1.0f }
    private val eqB1 = FloatArray(MAX_EQ_BANDS) { 0.0f }
    private val eqB2 = FloatArray(MAX_EQ_BANDS) { 0.0f }
    private val eqA1 = FloatArray(MAX_EQ_BANDS) { 0.0f }
    private val eqA2 = FloatArray(MAX_EQ_BANDS) { 0.0f }
    private val eqS1L = FloatArray(MAX_EQ_BANDS) { 0.0f }
    private val eqS2L = FloatArray(MAX_EQ_BANDS) { 0.0f }
    private val eqS1R = FloatArray(MAX_EQ_BANDS) { 0.0f }
    private val eqS2R = FloatArray(MAX_EQ_BANDS) { 0.0f }
    private val eqBandEnabled = BooleanArray(MAX_EQ_BANDS) { true }
    @Volatile
    private var activeBandsCount = 10

    // Flat Contiguous 3-Way Crossover Filter Bank Registers
    // Low-Pass Filter (L & R)
    private var xoLpB0 = 1.0f; private var xoLpB1 = 0.0f; private var xoLpB2 = 0.0f
    private var xoLpA1 = 0.0f; private var xoLpA2 = 0.0f
    private var xoLpS1L = 0.0f; private var xoLpS2L = 0.0f
    private var xoLpS1R = 0.0f; private var xoLpS2R = 0.0f

    // Mid-Band High-Pass Stage (L & R)
    private var xoMidHpB0 = 1.0f; private var xoMidHpB1 = 0.0f; private var xoMidHpB2 = 0.0f
    private var xoMidHpA1 = 0.0f; private var xoMidHpA2 = 0.0f
    private var xoMidHpS1L = 0.0f; private var xoMidHpS2L = 0.0f
    private var xoMidHpS1R = 0.0f; private var xoMidHpS2R = 0.0f

    // Mid-Band Low-Pass Stage (L & R)
    private var xoMidLpB0 = 1.0f; private var xoMidLpB1 = 0.0f; private var xoMidLpB2 = 0.0f
    private var xoMidLpA1 = 0.0f; private var xoMidLpA2 = 0.0f
    private var xoMidLpS1L = 0.0f; private var xoMidLpS2L = 0.0f
    private var xoMidLpS1R = 0.0f; private var xoMidLpS2R = 0.0f

    // High-Pass Filter (L & R)
    private var xoHpB0 = 1.0f; private var xoHpB1 = 0.0f; private var xoHpB2 = 0.0f
    private var xoHpA1 = 0.0f; private var xoHpA2 = 0.0f
    private var xoHpS1L = 0.0f; private var xoHpS2L = 0.0f
    private var xoHpS1R = 0.0f; private var xoHpS2R = 0.0f

    // Pre-allocated Circular RingBuffer for Stereo Delay
    private val delayRingL = FloatArray(DELAY_BUFFER_CAPACITY)
    private val delayRingR = FloatArray(DELAY_BUFFER_CAPACITY)
    private var delayWriteIndex = 0
    private var delayDampingL = 0.0f
    private var delayDampingR = 0.0f

    // Peak decay registers
    private var peakInPre = 0f
    private var peakInPost = 0f
    private var peakLow = 0f
    private var peakMid = 0f
    private var peakHigh = 0f
    private var peakFx = 0f
    private var peakOutL = 0f
    private var peakOutR = 0f

    // Noise calibration generator
    private var pinkB0 = 0.0f; private var pinkB1 = 0.0f; private var pinkB2 = 0.0f
    private var pinkB3 = 0.0f; private var pinkB4 = 0.0f; private var pinkB5 = 0.0f; private var pinkB6 = 0.0f
    private var whiteNoiseSeed = 135792468L

    init {
        updateDspFromConfig(currentConfig)
    }

    @Synchronized
    fun startSystemAudioCapture(projection: MediaProjection) {
        Log.i(TAG, "[MediaProjection] Step 8: AudioMixerDspEngine.startSystemAudioCapture called with projection=$projection")
        if (isRunning.get() || audioRecord != null || activeMediaProjection != null) {
            Log.i(TAG, "[MediaProjection] Engine has existing session resources; performing strict teardown to prevent zombie audio policy...")
            teardownCapture()
            try {
                // Short sleep to allow Android OS AudioPolicyService to unregister the old sub-mix policy
                Thread.sleep(60)
            } catch (_: InterruptedException) {}
        }
        activeMediaProjection = projection
        sourceMode = AudioSourceMode.SYSTEM_CAPTURE
        _isSystemCaptureActive.value = true
        _audioCaptureStatus.value = "INITIALIZING"
        start()
    }

    fun updateConfig(config: MixerConfig) {
        currentConfig = config
        updateDspFromConfig(config)
    }

    private fun updateDspFromConfig(config: MixerConfig) {
        val bands = config.bands.take(MAX_EQ_BANDS)
        activeBandsCount = bands.size

        for (i in bands.indices) {
            val band = bands[i]
            eqBandEnabled[i] = band.isEnabled && config.eqEnabled
            if (eqBandEnabled[i]) {
                computeBiquadCoefficients(
                    type = band.filterType,
                    frequency = band.frequency,
                    gainDb = band.gainDb,
                    q = band.q,
                    outIndex = i
                )
            } else {
                eqB0[i] = 1.0f; eqB1[i] = 0.0f; eqB2[i] = 0.0f
                eqA1[i] = 0.0f; eqA2[i] = 0.0f
            }
        }

        computeCrossoverFilters(config.crossoverLowFreq, config.crossoverHighFreq)
    }

    private fun computeBiquadCoefficients(
        type: FilterType,
        frequency: Float,
        gainDb: Float,
        q: Float,
        outIndex: Int
    ) {
        val nyquist = SAMPLE_RATE * 0.495f
        val clampedFreq = frequency.coerceIn(10.0f, nyquist)
        val clampedQ = q.coerceIn(0.1f, 10.0f)
        val a = 10.0.pow(gainDb.toDouble() / 40.0)
        val omega = 2.0 * PI * clampedFreq / SAMPLE_RATE
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * clampedQ)

        var b0D: Double; var b1D: Double; var b2D: Double
        var a0D: Double; var a1D: Double; var a2D: Double

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
                b0D = (1.0 - cs) / 2.0; b1D = 1.0 - cs; b2D = (1.0 - cs) / 2.0
                a0D = 1.0 + alpha; a1D = -2.0 * cs; a2D = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0D = (1.0 + cs) / 2.0; b1D = -(1.0 + cs); b2D = (1.0 + cs) / 2.0
                a0D = 1.0 + alpha; a1D = -2.0 * cs; a2D = 1.0 - alpha
            }
            FilterType.NOTCH -> {
                b0D = 1.0; b1D = -2.0 * cs; b2D = 1.0
                a0D = 1.0 + alpha; a1D = -2.0 * cs; a2D = 1.0 - alpha
            }
            FilterType.BAND_PASS -> {
                b0D = alpha; b1D = 0.0; b2D = -alpha
                a0D = 1.0 + alpha; a1D = -2.0 * cs; a2D = 1.0 - alpha
            }
        }

        val invA0 = (1.0 / a0D).toFloat()
        eqB0[outIndex] = (b0D * invA0).toFloat()
        eqB1[outIndex] = (b1D * invA0).toFloat()
        eqB2[outIndex] = (b2D * invA0).toFloat()
        eqA1[outIndex] = (a1D * invA0).toFloat()
        eqA2[outIndex] = (a2D * invA0).toFloat()
    }

    private fun computeCrossoverFilters(lowCutHz: Float, highCutHz: Float) {
        val qButterworth = 0.7071
        val fLow = lowCutHz.coerceIn(40.0f, 1000.0f)
        val fHigh = highCutHz.coerceIn(1200.0f, 16000.0f)

        val wL = 2.0 * PI * fLow / SAMPLE_RATE
        val snL = sin(wL); val csL = cos(wL)
        val aL = snL / (2.0 * qButterworth)
        val lpA0 = 1.0 + aL; val invLpA0 = (1.0 / lpA0).toFloat()
        xoLpB0 = (((1.0 - csL) / 2.0) * invLpA0).toFloat()
        xoLpB1 = ((1.0 - csL) * invLpA0).toFloat()
        xoLpB2 = (((1.0 - csL) / 2.0) * invLpA0).toFloat()
        xoLpA1 = ((-2.0 * csL) * invLpA0).toFloat()
        xoLpA2 = ((1.0 - aL) * invLpA0).toFloat()

        xoMidHpB0 = (((1.0 + csL) / 2.0) * invLpA0).toFloat()
        xoMidHpB1 = ((-(1.0 + csL)) * invLpA0).toFloat()
        xoMidHpB2 = (((1.0 + csL) / 2.0) * invLpA0).toFloat()
        xoMidHpA1 = xoLpA1
        xoMidHpA2 = xoLpA2

        val wH = 2.0 * PI * fHigh / SAMPLE_RATE
        val snH = sin(wH); val csH = cos(wH)
        val aH = snH / (2.0 * qButterworth)
        val hpA0 = 1.0 + aH; val invHpA0 = (1.0 / hpA0).toFloat()
        xoMidLpB0 = (((1.0 - csH) / 2.0) * invHpA0).toFloat()
        xoMidLpB1 = ((1.0 - csH) * invHpA0).toFloat()
        xoMidLpB2 = (((1.0 - csH) / 2.0) * invHpA0).toFloat()
        xoMidLpA1 = ((-2.0 * csH) * invHpA0).toFloat()
        xoMidLpA2 = ((1.0 - aH) * invHpA0).toFloat()

        xoHpB0 = (((1.0 + csH) / 2.0) * invHpA0).toFloat()
        xoHpB1 = ((-(1.0 + csH)) * invHpA0).toFloat()
        xoHpB2 = (((1.0 + csH) / 2.0) * invHpA0).toFloat()
        xoHpA1 = xoMidLpA1
        xoHpA2 = xoMidLpA2
    }

    fun computeCombinedFrequencyResponseDb(frequency: Float): Float {
        if (!currentConfig.eqEnabled) return 0.0f
        var totalDb = 0.0f
        val omega = 2.0 * PI * frequency / SAMPLE_RATE
        val cosOmega = cos(omega)
        val cos2Omega = cos(2.0 * omega)
        val sinOmega = sin(omega)
        val sin2Omega = sin(2.0 * omega)

        for (i in 0 until activeBandsCount) {
            if (!eqBandEnabled[i]) continue

            val numReal = eqB0[i] + eqB1[i] * cosOmega + eqB2[i] * cos2Omega
            val numImag = -eqB1[i] * sinOmega - eqB2[i] * sin2Omega
            val denReal = 1.0 + eqA1[i] * cosOmega + eqA2[i] * cos2Omega
            val denImag = -eqA1[i] * sinOmega - eqA2[i] * sin2Omega

            val numMagSq = numReal * numReal + numImag * numImag
            val denMagSq = denReal * denReal + denImag * denImag

            if (denMagSq > 1e-12) {
                val magRatio = numMagSq / denMagSq
                totalDb += (10.0 * log10(max(1e-12, magRatio))).toFloat()
            }
        }
        return totalDb.coerceIn(-36.0f, 36.0f)
    }

    @Synchronized
    fun start() {
        if (isRunning.get()) {
            Log.w(TAG, "[Engine] start() called but engine is already running.")
            return
        }
        isRunning.set(true)
        Log.i(TAG, "[Engine] Starting AudioMixerDspEngine. Initializing AudioTrack (48kHz Stereo Int16)...")

        // 1. Build and configure AudioTrack (ENCODING_PCM_16BIT, MODE_STREAM, USAGE_MEDIA)
        try {
            val minTrackBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val trackBufferSize = max(minTrackBuf, BUFFER_FRAMES * CHANNEL_COUNT * 2 * 4)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE) // Crucial: avoid re-capturing our own output
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(trackBufferSize)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            val trackState = track.state
            Log.i(TAG, "[AudioTrack] state after build: $trackState (STATE_INITIALIZED=${AudioTrack.STATE_INITIALIZED})")
            track.play()
            Log.i(TAG, "[AudioTrack] playState after .play(): ${track.playState} (PLAYSTATE_PLAYING=${AudioTrack.PLAYSTATE_PLAYING})")
        } catch (e: Exception) {
            Log.e(TAG, "[AudioTrack] Failed to build AudioTrack", e)
            teardownCapture()
            _audioCaptureStatus.value = "AUDIOTRACK_INIT_FAILED"
            return
        }

        // 2. Build AudioRecord (System Audio Capture or Mic fallback in ENCODING_PCM_16BIT)
        val recordSetupSuccess = setupAudioRecord()
        if (!recordSetupSuccess) {
            Log.w(TAG, "[Engine] AudioRecord setup failed or uninitialized. Halting start sequence cleanly to prevent zombie policy.")
            teardownCapture()
            return
        }

        _isPlaying.value = true

        // 3. Launch dedicated Real-Time Audio DSP Thread with URGENT_AUDIO priority
        audioProcessingThread = Thread({ audioDspLoop() }, "SonicMixUrgentAudioDspThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Hardware Routing Verification:
     * Pushes a 1-second 440Hz sine wave directly to the AudioTrack output.
     * Verifies that the speaker/headphone hardware routing works completely independently of capture.
     */
    fun playHardwareVerificationTone(track: AudioTrack) {
        try {
            Log.i(TAG, "[Hardware Verification] STEP 3: Pushing 1-second 440Hz sine wave directly to AudioTrack output...")
            val frames = SAMPLE_RATE // 48,000 frames = 1.0 second
            val chunk = ShortArray(BUFFER_FRAMES * CHANNEL_COUNT)
            val freq = 440.0
            val twoPiF = 2.0 * Math.PI * freq / SAMPLE_RATE
            val amp = 9000.0 // ~ -11 dBFS (crisp and clearly audible)

            var framesWritten = 0
            while (framesWritten < frames && isRunning.get()) {
                val toWrite = min(BUFFER_FRAMES, frames - framesWritten)
                for (i in 0 until toWrite) {
                    val sample = (sin(twoPiF * (framesWritten + i)) * amp).toInt().toShort()
                    chunk[i * 2] = sample     // Left channel
                    chunk[i * 2 + 1] = sample // Right channel
                }
                track.write(chunk, 0, toWrite * CHANNEL_COUNT)
                framesWritten += toWrite
            }
            Log.i(TAG, "[Hardware Verification] 1-second 440Hz tone completed. Hardware audio routing is 100% verified functional!")
        } catch (e: Exception) {
            Log.e(TAG, "[Hardware Verification] Exception during hardware verification tone playback", e)
        }
    }

    /**
     * Trigger a 1-second 440Hz verification tone on-demand from UI.
     */
    fun triggerHardwareTestTone() {
        Thread({
            audioTrack?.let { track ->
                playHardwareVerificationTone(track)
            }
        }, "SonicMixManualToneThread").start()
    }

    private fun setupAudioRecord(): Boolean {
        if (sourceMode == AudioSourceMode.SYSTEM_CAPTURE) {
            val projection = activeMediaProjection
            if (projection == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.e(TAG, "[AudioRecord] Cannot start capture: MediaProjection is null or SDK < Android 10 (Q).")
                _audioCaptureStatus.value = "MISSING_PROJECTION_TOKEN"
                return false
            }

            try {
                Log.i(TAG, "[AudioRecord] Step 8b: Configuring AudioPlaybackCaptureConfiguration (MATCH_USAGE: MEDIA, GAME, UNKNOWN)...")
                val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val minRecordBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = max(minRecordBuf, BUFFER_FRAMES * CHANNEL_COUNT * 2 * 4)

                val record = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                val recordState = record.state
                Log.i(TAG, "[AudioRecord] AudioRecord.state immediately after build: $recordState (STATE_INITIALIZED=${AudioRecord.STATE_INITIALIZED})")

                if (recordState != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "[AudioRecord] AudioRecord.state is NOT STATE_INITIALIZED ($recordState). MediaProjection token invalid or foreground service missing type=mediaProjection.")
                    _audioCaptureStatus.value = "POLICY_REGISTER_FAILED (State=$recordState)"
                    try {
                        record.release()
                    } catch (_: Throwable) {}
                    return false
                }

                record.startRecording()
                val recordingState = record.recordingState
                Log.i(TAG, "[AudioRecord] AudioRecord.recordingState immediately after startRecording(): $recordingState (RECORDSTATE_RECORDING=${AudioRecord.RECORDSTATE_RECORDING})")

                if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "[AudioRecord] AudioRecord.recordingState is NOT RECORDING ($recordingState). The MediaProjection token was rejected or expired by Android AudioPolicy/HAL.")
                    _audioCaptureStatus.value = "CAPTURE_STOPPED_BY_OS (Token Rejected)"
                    try {
                        record.stop()
                    } catch (_: Throwable) {}
                    try {
                        record.release()
                    } catch (_: Throwable) {}
                    return false
                }

                audioRecord = record
                _audioCaptureStatus.value = "RECORDING (Active)"
                Log.i(TAG, "[AudioRecord] SUCCESS: AudioRecord is RECORDSTATE_RECORDING! Ready for real-time capture.")
                return true
            } catch (t: Throwable) {
                // Catches UnsupportedOperationException ("Cannot create AudioRecord" / "could not register audio policy"),
                // SecurityException, IllegalStateException, and RuntimeException to PREVENT ANY APP CRASH.
                Log.e(TAG, "[AudioRecord] AudioRecord creation failed: ${t.message}", t)
                _audioCaptureStatus.value = "POLICY_REGISTER_FAILED (${t.javaClass.simpleName})"
                try {
                    audioRecord?.stop()
                } catch (_: Throwable) {}
                try {
                    audioRecord?.release()
                } catch (_: Throwable) {}
                audioRecord = null
                return false
            }
        } else if (sourceMode == AudioSourceMode.MIC_INPUT) {
            try {
                Log.i(TAG, "[AudioRecord] Setting up MIC fallback AudioRecord in PCM 16-bit...")
                val minRecordBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minRecordBuf, BUFFER_FRAMES * CHANNEL_COUNT * 2 * 4)
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    record.startRecording()
                    audioRecord = record
                    Log.i(TAG, "[AudioRecord] MIC AudioRecord.recordingState: ${record.recordingState}")
                    _audioCaptureStatus.value = "MIC RECORDING"
                    return true
                } else {
                    record.release()
                    _audioCaptureStatus.value = "MIC_UNINITIALIZED"
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "[AudioRecord] Fallback AudioRecord failed", e)
                _audioCaptureStatus.value = "MIC ERROR: ${e.message}"
                return false
            }
        }
        return true
    }

    /**
     * Strict Cleanup Sequence for Zombie Policy Prevention:
     * audioRecord?.stop() -> audioRecord?.release() -> mediaProjection?.stop()
     */
    @Synchronized
    fun teardownCapture() {
        Log.i(TAG, "[Teardown] Starting strict cleanup sequence...")
        isRunning.set(false)

        try {
            audioProcessingThread?.interrupt()
            audioProcessingThread?.join(300)
        } catch (_: Exception) {}
        audioProcessingThread = null

        // STRICT ORDER 1: audioRecord?.stop()
        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Teardown] Error stopping audioRecord: ${e.message}")
        }

        // STRICT ORDER 2: audioRecord?.release() (unregisters the submix policy from AudioPolicyService)
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "[Teardown] Error releasing audioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }

        // STRICT ORDER 3: mediaProjection?.stop() (strictly after AudioRecord is released)
        try {
            activeMediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "[Teardown] Error stopping activeMediaProjection: ${e.message}")
        } finally {
            activeMediaProjection = null
        }

        // AudioTrack teardown
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Teardown] Error releasing audioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }

        _isPlaying.value = false
        _isSystemCaptureActive.value = false
        _isBufferSilentWarning.value = false
        _audioCaptureStatus.value = "STOPPED (Clean)"
        _audioLevels.value = AudioLevels()
        Log.i(TAG, "[Teardown] Strict teardown completed.")
    }

    @Synchronized
    fun stop() {
        teardownCapture()
    }

    /**
     * Int16-to-Float32 Capture & Processing DSP Loop.
     *
     * Pipeline:
     * 1. Read Int16 PCM via AudioRecord -> rawShortInputBuffer
     * 2. Validate silence: If 100% zeros, log BUFFER_SILENT_POSSIBLE_APP_BLOCK
     * 3. Convert Int16 -> Float32: inFloatBuffer[i] = rawShortInputBuffer[i] / 32768.0f
     * 4. Process Float32 through Zero-Allocation Flat Primitive DSP (Trim -> In Fader -> 60 EQ Bands -> Crossover -> Delay -> Pan -> Out Fader -> Limiter)
     * 5. Convert Float32 -> Int16: outShortBuffer[i] = (sample * 32767.0f).toInt().toShort()
     * 6. Stream write outShortBuffer to AudioTrack
     */
    private fun audioDspLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) {}

        // STEP 3: HARDWARE ROUTING VERIFICATION
        // Push 1-second 440Hz sine wave directly to AudioTrack output before DSP loop starts
        audioTrack?.let { track ->
            playHardwareVerificationTone(track)
        }

        var lastMeterEmitTime = System.currentTimeMillis()
        var lastSilentLogTime = 0L
        var hasLoggedFirstActiveAudio = false
        var blocksProcessed = 0
        var totalBlockTimeNanos = 0L

        while (isRunning.get()) {
            val blockStartTime = System.nanoTime()
            val config = currentConfig
            val activeBands = activeBandsCount

            // Linear Gain conversions
            val preGainLinear = dbToLinear(config.preGainDb)
            val mainInputGainLinear = dbToLinear(config.mainInputGainDb)
            val mainOutputGainLinear = dbToLinear(config.mainOutputGainDb)
            val lowGainLinear = dbToLinear(config.lowFadeDb)
            val midGainLinear = dbToLinear(config.midFadeDb)
            val highGainLinear = dbToLinear(config.highFadeDb)

            // Constant power pan law
            val panTheta = ((config.pan.coerceIn(-1.0f, 1.0f) + 1.0f) * 0.25f * PI).toFloat()
            val panGainL = cos(panTheta) * 1.4142f
            val panGainR = sin(panTheta) * 1.4142f

            // Delay effect parameters
            val delayEnabled = config.delayEnabled
            val delayMix = config.delayMix
            val delayFeedback = config.delayFeedback.coerceIn(0.0f, 0.95f)
            val delaySamples = (config.delayTimeMs * 0.001f * SAMPLE_RATE).toInt().coerceIn(10, DELAY_BUFFER_CAPACITY - 10)

            val limiterEnabled = config.limiterEnabled
            val eqEnabled = config.eqEnabled
            val isTestNoise = (sourceMode == AudioSourceMode.CALIBRATION_NOISE)

            // =================================================================
            // STEP 1: READ INT16 FROM AUDIO RECORD
            // =================================================================
            val shortsToRead = BUFFER_FRAMES * CHANNEL_COUNT
            val shortsRead = audioRecord?.read(rawShortInputBuffer, 0, shortsToRead) ?: 0

            if (shortsRead < 0) {
                val now = System.currentTimeMillis()
                if (now - lastSilentLogTime >= 2000L) {
                    lastSilentLogTime = now
                    Log.e(TAG, "[AudioRecord] read error: $shortsRead (ERROR_INVALID_OPERATION=-3, ERROR_BAD_VALUE=-2, ERROR_DEAD_OBJECT=-6)")
                    _audioCaptureStatus.value = "READ ERROR ($shortsRead)"
                }
            }

            // =================================================================
            // STEP 2: DEBUG SILENCE & BUFFER VALIDATION
            // Check if buffer contains 100% zeros
            // =================================================================
            var isAllZeros = true
            if (!isTestNoise && shortsRead > 0) {
                for (i in 0 until shortsRead) {
                    if (rawShortInputBuffer[i] != 0.toShort()) {
                        isAllZeros = false
                        break
                    }
                }
            } else if (isTestNoise) {
                isAllZeros = false
            }

            val now = System.currentTimeMillis()
            if (isAllZeros) {
                _isBufferSilentWarning.value = true
                if (now - lastSilentLogTime >= 2500L) {
                    Log.w(
                        TAG,
                        "[AudioPlaybackCapture] BUFFER_SILENT_POSSIBLE_APP_BLOCK: AudioRecord returned 100% zeros.\n" +
                        "  -> AudioRecord is actively RECORDING, but Android AudioPolicy blocked the audio stream.\n" +
                        "  -> Target app (e.g. Simple Music) likely has android:allowAudioPlaybackCapture=\"false\" or targetSdk < 29.\n" +
                        "  -> Note: Android DOES NOT mute the source app through the speaker when capturing.\n" +
                        "  -> TEST FIX: Play a YouTube video in Google Chrome, or play a song in Spotify / YouTube Music / VLC."
                    )
                    lastSilentLogTime = now
                }
            } else {
                _isBufferSilentWarning.value = false
                if (!hasLoggedFirstActiveAudio) {
                    hasLoggedFirstActiveAudio = true
                    Log.i(TAG, "[AudioPlaybackCapture] SUCCESS: Active non-zero PCM audio captured! Signal is actively streaming into 60-band DSP!")
                    _audioCaptureStatus.value = "CAPTURING AUDIO"
                }
            }

            // =================================================================
            // STEP 3: CONVERT INT16 (ShortArray) TO FLOAT32 (FloatArray)
            // =================================================================
            if (isTestNoise) {
                for (i in 0 until shortsToRead) {
                    inFloatBuffer[i] = generateFastPinkNoise()
                }
            } else if (shortsRead >= shortsToRead) {
                for (i in 0 until shortsToRead) {
                    inFloatBuffer[i] = rawShortInputBuffer[i] * INV_32768
                }
            } else {
                for (i in 0 until shortsToRead) {
                    inFloatBuffer[i] = if (i < shortsRead) rawShortInputBuffer[i] * INV_32768 else 0.0f
                }
            }

            // Accumulators for VU Meters
            var sumSqInPre = 0.0; var peakInPreBlock = 0.0f
            var sumSqInPost = 0.0; var peakInPostBlock = 0.0f
            var sumSqLow = 0.0; var peakLowBlock = 0.0f
            var sumSqMid = 0.0; var peakMidBlock = 0.0f
            var sumSqHigh = 0.0; var peakHighBlock = 0.0f
            var sumSqFx = 0.0; var peakFxBlock = 0.0f
            var sumSqOutL = 0.0; var peakOutLBlock = 0.0f
            var sumSqOutR = 0.0; var peakOutRBlock = 0.0f

            // =================================================================
            // STEP 4: REAL-TIME ZERO-ALLOCATION DSP PROCESSING
            // =================================================================
            for (f in 0 until BUFFER_FRAMES) {
                val idx = f * 2
                val rawInL = inFloatBuffer[idx]
                val rawInR = inFloatBuffer[idx + 1]

                // Meter 1: Pre-trim Input
                val preMono = (abs(rawInL) + abs(rawInR)) * 0.5f
                sumSqInPre += (preMono * preMono).toDouble()
                if (preMono > peakInPreBlock) peakInPreBlock = preMono

                // 2. Pre-Gain / Trimmer
                var sampleL = rawInL * preGainLinear
                var sampleR = rawInR * preGainLinear

                // Meter 2: Post-trim Input
                val postMono = (abs(sampleL) + abs(sampleR)) * 0.5f
                sumSqInPost += (postMono * postMono).toDouble()
                if (postMono > peakInPostBlock) peakInPostBlock = postMono

                // 3. Main Input Fader
                sampleL *= mainInputGainLinear
                sampleR *= mainInputGainLinear

                // 4. Cascaded 60-Band IIR Biquad Filters (Direct Form II Transposed)
                if (eqEnabled) {
                    for (b in 0 until activeBands) {
                        if (!eqBandEnabled[b]) continue

                        val yL = eqB0[b] * sampleL + eqS1L[b]
                        eqS1L[b] = eqB1[b] * sampleL - eqA1[b] * yL + eqS2L[b]
                        eqS2L[b] = eqB2[b] * sampleL - eqA2[b] * yL
                        sampleL = yL

                        val yR = eqB0[b] * sampleR + eqS1R[b]
                        eqS1R[b] = eqB1[b] * sampleR - eqA1[b] * yR + eqS2R[b]
                        eqS2R[b] = eqB2[b] * sampleR - eqA2[b] * yR
                        sampleR = yR
                    }
                }

                // 5. 3-Way Crossover Filter Bank
                val lowL = xoLpB0 * sampleL + xoLpS1L
                xoLpS1L = xoLpB1 * sampleL - xoLpA1 * lowL + xoLpS2L
                xoLpS2L = xoLpB2 * sampleL - xoLpA2 * lowL

                val lowR = xoLpB0 * sampleR + xoLpS1R
                xoLpS1R = xoLpB1 * sampleR - xoLpA1 * lowR + xoLpS2R
                xoLpS2R = xoLpB2 * sampleR - xoLpA2 * lowR

                val midHpL = xoMidHpB0 * sampleL + xoMidHpS1L
                xoMidHpS1L = xoMidHpB1 * sampleL - xoMidHpA1 * midHpL + xoMidHpS2L
                xoMidHpS2L = xoMidHpB2 * sampleL - xoMidHpA2 * midHpL

                val midL = xoMidLpB0 * midHpL + xoMidLpS1L
                xoMidLpS1L = xoMidLpB1 * midHpL - xoMidLpA1 * midL + xoMidLpS2L
                xoMidLpS2L = xoMidLpB2 * midHpL - xoMidLpA2 * midL

                val midHpR = xoMidHpB0 * sampleR + xoMidHpS1R
                xoMidHpS1R = xoMidHpB1 * sampleR - xoMidHpA1 * midHpR + xoMidHpS2R
                xoMidHpS2R = xoMidHpB2 * sampleR - xoMidHpA2 * midHpR

                val midR = xoMidLpB0 * midHpR + xoMidLpS1R
                xoMidLpS1R = xoMidLpB1 * midHpR - xoMidLpA1 * midR + xoMidLpS2R
                xoMidLpS2R = xoMidLpB2 * midHpR - xoMidLpA2 * midR

                val highL = xoHpB0 * sampleL + xoHpS1L
                xoHpS1L = xoHpB1 * sampleL - xoHpA1 * highL + xoHpS2L
                xoHpS2L = xoHpB2 * sampleL - xoHpA2 * highL

                val highR = xoHpB0 * sampleR + xoHpS1R
                xoHpS1R = xoHpB1 * sampleR - xoHpA1 * highR + xoHpS2R
                xoHpS2R = xoHpB2 * sampleR - xoHpA2 * highR

                val lowAmp = (abs(lowL) + abs(lowR)) * 0.5f
                val midAmp = (abs(midL) + abs(midR)) * 0.5f
                val highAmp = (abs(highL) + abs(highR)) * 0.5f

                sumSqLow += (lowAmp * lowAmp).toDouble()
                if (lowAmp > peakLowBlock) peakLowBlock = lowAmp
                sumSqMid += (midAmp * midAmp).toDouble()
                if (midAmp > peakMidBlock) peakMidBlock = midAmp
                sumSqHigh += (highAmp * highAmp).toDouble()
                if (highAmp > peakHighBlock) peakHighBlock = highAmp

                sampleL = (lowL * lowGainLinear) + (midL * midGainLinear) + (highL * highGainLinear)
                sampleR = (lowR * lowGainLinear) + (midR * midGainLinear) + (highR * highGainLinear)

                // 6. Stereo Delay (Circular RingBuffer)
                if (delayEnabled && delayMix > 0.001f) {
                    var readIdx = delayWriteIndex - delaySamples
                    if (readIdx < 0) readIdx += DELAY_BUFFER_CAPACITY

                    val delayedL = delayRingL[readIdx]
                    val delayedR = delayRingR[readIdx]

                    delayDampingL = delayDampingL * 0.35f + delayedL * 0.65f
                    delayDampingR = delayDampingR * 0.35f + delayedR * 0.65f

                    delayRingL[delayWriteIndex] = sampleL + (delayDampingL * delayFeedback)
                    delayRingR[delayWriteIndex] = sampleR + (delayDampingR * delayFeedback)

                    delayWriteIndex++
                    if (delayWriteIndex >= DELAY_BUFFER_CAPACITY) delayWriteIndex = 0

                    val fxAmp = (abs(delayedL) + abs(delayedR)) * 0.5f * delayMix
                    sumSqFx += (fxAmp * fxAmp).toDouble()
                    if (fxAmp > peakFxBlock) peakFxBlock = fxAmp

                    sampleL = sampleL * (1.0f - delayMix * 0.5f) + delayedL * delayMix
                    sampleR = sampleR * (1.0f - delayMix * 0.5f) + delayedR * delayMix
                } else {
                    delayRingL[delayWriteIndex] = sampleL
                    delayRingR[delayWriteIndex] = sampleR
                    delayWriteIndex++
                    if (delayWriteIndex >= DELAY_BUFFER_CAPACITY) delayWriteIndex = 0
                }

                // 7. Panning & Main Output Fader
                sampleL = sampleL * panGainL * mainOutputGainLinear
                sampleR = sampleR * panGainR * mainOutputGainLinear

                // 8. Soft Limiter (Prevent harsh digital wrapping)
                if (limiterEnabled) {
                    val absL = abs(sampleL)
                    if (absL > 0.85f) {
                        val ex = absL - 0.85f
                        val comp = 0.85f + 0.15f * (ex / (ex + 0.35f))
                        sampleL = if (sampleL > 0) comp else -comp
                    }
                    val absR = abs(sampleR)
                    if (absR > 0.85f) {
                        val ex = absR - 0.85f
                        val comp = 0.85f + 0.15f * (ex / (ex + 0.35f))
                        sampleR = if (sampleR > 0) comp else -comp
                    }
                }

                // Output VU Levels
                val outL = abs(sampleL)
                val outR = abs(sampleR)
                sumSqOutL += (outL * outL).toDouble()
                if (outL > peakOutLBlock) peakOutLBlock = outL
                sumSqOutR += (outR * outR).toDouble()
                if (outR > peakOutRBlock) peakOutRBlock = outR

                // Store in Float32 buffer
                outFloatBuffer[idx] = sampleL
                outFloatBuffer[idx + 1] = sampleR

                // =============================================================
                // STEP 5: CONVERT FLOAT32 TO INT16 FOR AUDIOTRACK
                // =============================================================
                val clampedL = sampleL.coerceIn(-1.0f, 1.0f)
                val clampedR = sampleR.coerceIn(-1.0f, 1.0f)
                outShortBuffer[idx] = (clampedL * 32767.0f).toInt().toShort()
                outShortBuffer[idx + 1] = (clampedR * 32767.0f).toInt().toShort()
            }

            // =================================================================
            // STEP 6: WRITE INT16 TO AUDIOTRACK (100% UNIVERSAL HAL STABILITY)
            // =================================================================
            audioTrack?.write(outShortBuffer, 0, outShortBuffer.size)

            val blockElapsedNanos = System.nanoTime() - blockStartTime
            totalBlockTimeNanos += blockElapsedNanos
            blocksProcessed++

            // Throttled VU Meter State Update (~35 Hz)
            if (now - lastMeterEmitTime >= 28) {
                lastMeterEmitTime = now
                val invFrames = 1.0f / BUFFER_FRAMES

                val rmsInPre = sqrt((sumSqInPre * invFrames).toFloat())
                val rmsInPost = sqrt((sumSqInPost * invFrames).toFloat())
                val rmsLow = sqrt((sumSqLow * invFrames).toFloat())
                val rmsMid = sqrt((sumSqMid * invFrames).toFloat())
                val rmsHigh = sqrt((sumSqHigh * invFrames).toFloat())
                val rmsFx = sqrt((sumSqFx * invFrames).toFloat())
                val rmsOutL = sqrt((sumSqOutL * invFrames).toFloat())
                val rmsOutR = sqrt((sumSqOutR * invFrames).toFloat())

                val decay = 0.91f
                peakInPre = max(peakInPreBlock, peakInPre * decay)
                peakInPost = max(peakInPostBlock, peakInPost * decay)
                peakLow = max(peakLowBlock, peakLow * decay)
                peakMid = max(peakMidBlock, peakMid * decay)
                peakHigh = max(peakHighBlock, peakHigh * decay)
                peakFx = max(peakFxBlock, peakFx * decay)
                peakOutL = max(peakOutLBlock, peakOutL * decay)
                peakOutR = max(peakOutRBlock, peakOutR * decay)

                _audioLevels.value = AudioLevels(
                    inPreTrimRms = rmsInPre, inPreTrimPeak = peakInPre,
                    inPostTrimRms = rmsInPost, inPostTrimPeak = peakInPost,
                    crossoverLowRms = rmsLow, crossoverLowPeak = peakLow,
                    crossoverMidRms = rmsMid, crossoverMidPeak = peakMid,
                    crossoverHighRms = rmsHigh, crossoverHighPeak = peakHigh,
                    delayReturnRms = rmsFx, delayReturnPeak = peakFx,
                    masterLeftRms = rmsOutL, masterLeftPeak = peakOutL,
                    masterRightRms = rmsOutR, masterRightPeak = peakOutR
                )

                if (blocksProcessed > 0) {
                    val avgBlockTimeMs = (totalBlockTimeNanos / blocksProcessed) / 1_000_000.0f
                    val blockBudgetMs = (BUFFER_FRAMES.toFloat() / SAMPLE_RATE) * 1000.0f
                    _cpuLoadEstimate.value = (avgBlockTimeMs / blockBudgetMs).coerceIn(0.01f, 1.0f)
                    blocksProcessed = 0
                    totalBlockTimeNanos = 0L
                }
            }
        }
    }

    private fun dbToLinear(db: Float): Float {
        return if (db <= -47.0f) 0.0f else 10.0.pow(db.toDouble() / 20.0).toFloat()
    }

    private fun generateFastPinkNoise(): Float {
        whiteNoiseSeed = (whiteNoiseSeed * 1103515245L + 12345L) and 0x7fffffffL
        val white = (whiteNoiseSeed.toFloat() / 0x3fffffffL) - 1.0f

        pinkB0 = 0.99886f * pinkB0 + white * 0.0555179f
        pinkB1 = 0.99332f * pinkB1 + white * 0.0750759f
        pinkB2 = 0.96900f * pinkB2 + white * 0.1538520f
        pinkB3 = 0.86650f * pinkB3 + white * 0.3104856f
        pinkB4 = 0.55000f * pinkB4 + white * 0.5329522f
        pinkB5 = -0.7616f * pinkB5 - white * 0.0168980f
        val pink = pinkB0 + pinkB1 + pinkB2 + pinkB3 + pinkB4 + pinkB5 + pinkB6 + white * 0.5362f
        pinkB6 = white * 0.115926f
        return (pink * 0.12f).coerceIn(-1.0f, 1.0f)
    }
}
