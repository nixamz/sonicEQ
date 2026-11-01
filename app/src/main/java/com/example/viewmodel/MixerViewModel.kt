package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.dsp.AudioMixerDspEngine
import com.example.model.AudioLevels
import com.example.model.AudioSourceMode
import com.example.model.FilterType
import com.example.model.MixerConfig
import com.example.model.ParametricBand
import com.example.repository.MixerConfigRepository
import com.example.service.SystemAudioCaptureService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MixerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MixerConfigRepository(application)
    val dspEngine = AudioMixerDspEngine()

    private val _config = MutableStateFlow(MixerConfig())
    val config: StateFlow<MixerConfig> = _config.asStateFlow()

    val audioLevels: StateFlow<AudioLevels> = dspEngine.audioLevels
    val isPlaying: StateFlow<Boolean> = dspEngine.isPlaying
    val cpuLoad: StateFlow<Float> = dspEngine.cpuLoadEstimate
    val isSystemCaptureActive: StateFlow<Boolean> = dspEngine.isSystemCaptureActive
    val isBufferSilentWarning: StateFlow<Boolean> = dspEngine.isBufferSilentWarning
    val audioCaptureStatus: StateFlow<String> = dspEngine.audioCaptureStatus

    private val _sourceMode = MutableStateFlow(AudioSourceMode.SYSTEM_CAPTURE)
    val sourceMode: StateFlow<AudioSourceMode> = _sourceMode.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // Debounced autosave flow to prevent DataStore disk writes from blocking Compose main thread
    private val saveQueue = MutableSharedFlow<MixerConfig>(replay = 1)

    init {
        // Wire the active engine to the Foreground Service
        SystemAudioCaptureService.activeEngine = dspEngine

        // Load initial config from DataStore
        viewModelScope.launch {
            repository.activeConfigFlow.firstOrNull()?.let { savedConfig ->
                _config.value = savedConfig
                dspEngine.updateConfig(savedConfig)
            }
        }

        // Debounce background persistence
        viewModelScope.launch {
            saveQueue
                .debounce(600)
                .collectLatest { configToSave ->
                    repository.saveActiveConfig(configToSave)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dspEngine.teardownCapture()
        SystemAudioCaptureService.stopCapture(getApplication())
    }

    /**
     * Start System Audio Capture using MediaProjection & AudioPlaybackCapture API.
     * Starts the Foreground Service (Android 12 compliant).
     */
    fun startSystemCapture(context: Context, resultCode: Int, data: Intent) {
        viewModelScope.launch {
            _sourceMode.value = AudioSourceMode.SYSTEM_CAPTURE
            dspEngine.sourceMode = AudioSourceMode.SYSTEM_CAPTURE
            SystemAudioCaptureService.startCapture(context, resultCode, data)
            _userMessage.emit("System Audio Capture active! Capturing playback from other apps.")
        }
    }

    fun stopSystemCapture(context: Context) {
        viewModelScope.launch {
            SystemAudioCaptureService.stopCapture(context)
            dspEngine.teardownCapture()
            _userMessage.emit("System Audio Capture stopped.")
        }
    }

    fun togglePlayback(context: Context) {
        if (dspEngine.isPlaying.value) {
            stopSystemCapture(context)
        } else {
            if (_sourceMode.value == AudioSourceMode.SYSTEM_CAPTURE && !dspEngine.isSystemCaptureActive.value) {
                viewModelScope.launch {
                    _userMessage.emit("Please tap 'CAPTURE SYSTEM AUDIO' to grant screen capture permission.")
                }
            } else {
                dspEngine.start()
            }
        }
    }

    fun triggerHardwareTestTone() {
        dspEngine.triggerHardwareTestTone()
        viewModelScope.launch {
            _userMessage.emit("Emitting 1-second 440Hz test tone to verify speaker/headphone routing.")
        }
    }

    fun setSourceMode(mode: AudioSourceMode) {
        _sourceMode.value = mode
        dspEngine.sourceMode = mode
    }

    fun saveCurrentConfiguration() {
        viewModelScope.launch {
            val current = _config.value
            repository.saveToPermanentSlot(current)
            _userMessage.emit("Configuration saved to DataStore (${current.bands.size} EQ bands, Faders, Delay, Pan)")
        }
    }

    private fun applyConfigChange(mutate: (MixerConfig) -> MixerConfig) {
        val updated = mutate(_config.value)
        _config.value = updated
        dspEngine.updateConfig(updated)
        saveQueue.tryEmit(updated)
    }

    fun setBandCount(count: Int) {
        val clamped = count.coerceIn(1, 60)
        applyConfigChange { old ->
            val currentBands = old.bands
            val newBands = if (clamped == currentBands.size) {
                currentBands
            } else if (clamped > currentBands.size) {
                MixerConfig.defaultBands(clamped)
            } else {
                currentBands.take(clamped)
            }
            old.copy(bands = newBands)
        }
    }

    fun updateBandFrequency(bandId: Int, freq: Float) {
        val clampedFreq = freq.coerceIn(20f, 20000f)
        applyConfigChange { old ->
            old.copy(
                bands = old.bands.map {
                    if (it.id == bandId) it.copy(frequency = clampedFreq) else it
                }
            )
        }
    }

    fun updateBandGain(bandId: Int, gainDb: Float) {
        val clampedGain = gainDb.coerceIn(-24f, 24f)
        applyConfigChange { old ->
            old.copy(
                bands = old.bands.map {
                    if (it.id == bandId) it.copy(gainDb = clampedGain) else it
                }
            )
        }
    }

    fun updateBandQ(bandId: Int, q: Float) {
        val clampedQ = q.coerceIn(0.1f, 10f)
        applyConfigChange { old ->
            old.copy(
                bands = old.bands.map {
                    if (it.id == bandId) it.copy(q = clampedQ) else it
                }
            )
        }
    }

    fun updateBandFilterType(bandId: Int, type: FilterType) {
        applyConfigChange { old ->
            old.copy(
                bands = old.bands.map {
                    if (it.id == bandId) it.copy(filterType = type) else it
                }
            )
        }
    }

    fun toggleBandEnabled(bandId: Int) {
        applyConfigChange { old ->
            old.copy(
                bands = old.bands.map {
                    if (it.id == bandId) it.copy(isEnabled = !it.isEnabled) else it
                }
            )
        }
    }

    fun updatePreGain(gainDb: Float) {
        applyConfigChange { it.copy(preGainDb = gainDb.coerceIn(-24f, 24f)) }
    }

    fun updatePan(pan: Float) {
        applyConfigChange { it.copy(pan = pan.coerceIn(-1f, 1f)) }
    }

    fun updateMainInputGain(gainDb: Float) {
        applyConfigChange { it.copy(mainInputGainDb = gainDb.coerceIn(-48f, 12f)) }
    }

    fun updateMainOutputGain(gainDb: Float) {
        applyConfigChange { it.copy(mainOutputGainDb = gainDb.coerceIn(-48f, 12f)) }
    }

    fun updateLowFade(gainDb: Float) {
        applyConfigChange { it.copy(lowFadeDb = gainDb.coerceIn(-24f, 12f)) }
    }

    fun updateMidFade(gainDb: Float) {
        applyConfigChange { it.copy(midFadeDb = gainDb.coerceIn(-24f, 12f)) }
    }

    fun updateHighFade(gainDb: Float) {
        applyConfigChange { it.copy(highFadeDb = gainDb.coerceIn(-24f, 12f)) }
    }

    fun updateCrossoverFrequencies(lowHz: Float, highHz: Float) {
        applyConfigChange {
            it.copy(
                crossoverLowFreq = lowHz.coerceIn(50f, 800f),
                crossoverHighFreq = highHz.coerceIn(1000f, 12000f)
            )
        }
    }

    fun updateDelayTime(timeMs: Float) {
        applyConfigChange { it.copy(delayTimeMs = timeMs.coerceIn(10f, 1000f)) }
    }

    fun updateDelayFeedback(feedback: Float) {
        applyConfigChange { it.copy(delayFeedback = feedback.coerceIn(0f, 0.95f)) }
    }

    fun updateDelayMix(mix: Float) {
        applyConfigChange { it.copy(delayMix = mix.coerceIn(0f, 1f)) }
    }

    fun toggleDelayEnabled() {
        applyConfigChange { it.copy(delayEnabled = !it.delayEnabled) }
    }

    fun toggleEqEnabled() {
        applyConfigChange { it.copy(eqEnabled = !it.eqEnabled) }
    }

    fun toggleLimiterEnabled() {
        applyConfigChange { it.copy(limiterEnabled = !it.limiterEnabled) }
    }

    fun resetEqGainsToFlat() {
        applyConfigChange { old ->
            old.copy(
                bands = old.bands.map { it.copy(gainDb = 0.0f) }
            )
        }
    }

    fun loadPreset(presetName: String) {
        applyConfigChange { old ->
            when (presetName) {
                "10-Band Graphic" -> {
                    val bands = listOf(
                        ParametricBand(1, 31f, 2f, 1.4f, FilterType.LOW_SHELF),
                        ParametricBand(2, 63f, 3f, 1.4f, FilterType.PEAK),
                        ParametricBand(3, 125f, 1f, 1.4f, FilterType.PEAK),
                        ParametricBand(4, 250f, -1f, 1.4f, FilterType.PEAK),
                        ParametricBand(5, 500f, 0f, 1.4f, FilterType.PEAK),
                        ParametricBand(6, 1000f, 1f, 1.4f, FilterType.PEAK),
                        ParametricBand(7, 2000f, 2f, 1.4f, FilterType.PEAK),
                        ParametricBand(8, 4000f, 3f, 1.4f, FilterType.PEAK),
                        ParametricBand(9, 8000f, 4f, 1.4f, FilterType.PEAK),
                        ParametricBand(10, 16000f, 3f, 1.4f, FilterType.HIGH_SHELF)
                    )
                    old.copy(name = "10-Band Graphic", bands = bands)
                }
                "31-Band 1/3 Octave ISO" -> {
                    val standardFreqs = floatArrayOf(
                        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
                        200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
                        2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f
                    )
                    val bands = standardFreqs.mapIndexed { i, f ->
                        ParametricBand(
                            id = i + 1,
                            frequency = f,
                            gainDb = 0f,
                            q = 4.3f,
                            filterType = if (i == 0) FilterType.LOW_SHELF else if (i == standardFreqs.lastIndex) FilterType.HIGH_SHELF else FilterType.PEAK
                        )
                    }
                    old.copy(name = "31-Band 1/3 Octave ISO", bands = bands)
                }
                "60-Band Surgical Dense" -> {
                    val bands = MixerConfig.defaultBands(60)
                    old.copy(name = "60-Band Surgical Dense", bands = bands)
                }
                "Bass Heavy & Sub" -> {
                    val count = old.bands.size.coerceAtLeast(10)
                    val baseBands = MixerConfig.defaultBands(count).map { band ->
                        val gain = when {
                            band.frequency <= 80f -> 6.0f
                            band.frequency <= 200f -> 4.0f
                            band.frequency <= 500f -> 1.5f
                            band.frequency in 800f..2500f -> -2.0f
                            band.frequency >= 10000f -> 2.0f
                            else -> 0.0f
                        }
                        band.copy(gainDb = gain)
                    }
                    old.copy(name = "Bass Heavy & Sub", bands = baseBands, lowFadeDb = 3.0f)
                }
                "Vocal Presence & Air" -> {
                    val count = old.bands.size.coerceAtLeast(10)
                    val baseBands = MixerConfig.defaultBands(count).map { band ->
                        val gain = when {
                            band.frequency < 100f -> -4.0f
                            band.frequency in 150f..300f -> 1.0f
                            band.frequency in 500f..1000f -> -1.5f
                            band.frequency in 2500f..5000f -> 3.5f
                            band.frequency >= 10000f -> 4.5f
                            else -> 0.0f
                        }
                        band.copy(gainDb = gain)
                    }
                    old.copy(name = "Vocal Presence & Air", bands = baseBands, midFadeDb = 2.0f)
                }
                "V-Curve Smile" -> {
                    val count = old.bands.size
                    val baseBands = old.bands.mapIndexed { idx, band ->
                        val normalized = idx.toFloat() / (count - 1).coerceAtLeast(1)
                        val smile = (4.0f * (normalized - 0.5f) * (normalized - 0.5f))
                        val gain = (smile * 7.0f) - 3.0f
                        band.copy(gainDb = (gain * 10f).toInt() / 10f)
                    }
                    old.copy(name = "V-Curve Smile", bands = baseBands)
                }
                else -> old
            }
        }
    }
}
