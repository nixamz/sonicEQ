package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioLevels
import com.example.model.AudioSourceMode
import com.example.model.MixerConfig
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun MixerConsoleScreen(
    config: MixerConfig,
    audioLevels: AudioLevels,
    isPlaying: Boolean,
    cpuLoad: Float,
    sourceMode: AudioSourceMode,
    isSystemCaptureActive: Boolean,
    isBufferSilentWarning: Boolean = false,
    audioCaptureStatus: String = "STANDBY",
    onRequestStartSystemCapture: () -> Unit,
    onStopSystemCapture: () -> Unit,
    onTogglePlay: () -> Unit,
    onTriggerHardwareTestTone: () -> Unit = {},
    onSelectSourceMode: (AudioSourceMode) -> Unit,
    onSaveConfig: () -> Unit,
    onPreGainChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onMainInputGainChange: (Float) -> Unit,
    onMainOutputGainChange: (Float) -> Unit,
    onLowFadeChange: (Float) -> Unit,
    onMidFadeChange: (Float) -> Unit,
    onHighFadeChange: (Float) -> Unit,
    onDelayTimeChange: (Float) -> Unit,
    onDelayFeedbackChange: (Float) -> Unit,
    onDelayMixChange: (Float) -> Unit,
    onToggleDelay: () -> Unit,
    onToggleLimiter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 1. Audio Level Indicators (VU Meters) - 8 Real-time Channels
        AllVuMetersRack(
            audioLevels = audioLevels,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Option B: System Audio Capture (MediaProjection + AudioPlaybackCapture)
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ScreenShare,
                            contentDescription = "System Audio Capture",
                            tint = StudioCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OPTION B: SYSTEM AUDIO CAPTURE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Save Button
                    OutlinedButton(
                        onClick = onSaveConfig,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("save_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Configuration",
                            tint = StudioCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE", color = StudioCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Action: Start/Stop System Audio Capture via MediaProjection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSystemCaptureActive) {
                        Button(
                            onClick = onStopSystemCapture,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioRed),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stop_system_capture_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopScreenShare,
                                contentDescription = "Stop Capture",
                                tint = StudioDarkBg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "STOP SYSTEM CAPTURE",
                                fontWeight = FontWeight.Bold,
                                color = StudioDarkBg,
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = onRequestStartSystemCapture,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_system_capture_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenShare,
                                contentDescription = "Start Capture",
                                tint = StudioDarkBg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CAPTURE SYSTEM AUDIO",
                                fontWeight = FontWeight.Bold,
                                color = StudioDarkBg,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Direct DSP Toggle
                    Button(
                        onClick = onTogglePlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying) StudioAmber else StudioNeonGreen
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("transport_play_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play",
                            tint = StudioDarkBg,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "PAUSE" else "START DSP",
                            fontWeight = FontWeight.Bold,
                            color = StudioDarkBg,
                            fontSize = 10.sp
                        )
                    }

                    // 440Hz Hardware Routing Verification Tone Button
                    OutlinedButton(
                        onClick = onTriggerHardwareTestTone,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("test_tone_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Test 440Hz Tone",
                            tint = StudioCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "440Hz BEEP",
                            fontWeight = FontWeight.Bold,
                            color = StudioCyan,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Real-time status banner
                val isStatusError = audioCaptureStatus.contains("FAILED") || audioCaptureStatus.contains("ERROR") || audioCaptureStatus.contains("REJECTED")
                val statusColor = if (isStatusError) StudioRed else if (isSystemCaptureActive) StudioNeonGreen else StudioAmber

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(StudioDarkBg)
                        .border(
                            0.5.dp,
                            if (isStatusError) StudioRed else if (isSystemCaptureActive) StudioNeonGreen else StudioBorder,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSystemCaptureActive) "SYSTEM CAPTURE: $audioCaptureStatus" else "SYSTEM AUDIO: $audioCaptureStatus",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "ZERO-ALLOC 48kHz STEREO",
                                fontSize = 8.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = if (isSystemCaptureActive) {
                                "Capturing internal system audio via MediaProjection AudioPlaybackCapture -> Snapdragon 750G 60-band DSP -> AudioTrack"
                            } else {
                                "Tap 'Capture System Audio' to grant MediaProjection and route any media app through this 60-band DSP mixer in real-time."
                            },
                            fontSize = 8.5.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (isSystemCaptureActive && isBufferSilentWarning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(StudioRed.copy(alpha = 0.15f))
                            .border(1.dp, StudioRed, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(
                                text = "⚠️ BUFFER_SILENT_POSSIBLE_APP_BLOCK (100% Zeros)",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioRed,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "AudioRecord is recording, but received all zeros from system audio HAL.\n" +
                                        "• Why Simple Music plays to speaker: Android does NOT silence the source app when captured.\n" +
                                        "• Why it was silent: Simple Music likely has android:allowAudioPlaybackCapture=\"false\" or targetSdk < 29.\n" +
                                        "• HOW TO VERIFY: Open Google Chrome & play a YouTube video, or play Spotify / YouTube Music / VLC. Tap '440Hz BEEP' to test speaker hardware output.",
                                fontSize = 8.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Auxiliary source modes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AudioSourceMode.values().forEach { mode ->
                        val isSelected = mode == sourceMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) StudioCyan.copy(alpha = 0.2f) else StudioDarkBg)
                                .border(
                                    0.5.dp,
                                    if (isSelected) StudioCyan else StudioBorder,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectSourceMode(mode) }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val shortLabel = when (mode) {
                                AudioSourceMode.SYSTEM_CAPTURE -> "System Audio"
                                AudioSourceMode.MIC_INPUT -> "Mic Input"
                                AudioSourceMode.CALIBRATION_NOISE -> "Pink Noise"
                            }
                            Text(
                                text = shortLabel,
                                fontSize = 8.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) StudioCyan else TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Main Console Channel Faders: Main In, Low, Mid, High, Main Out
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "MIXER GAIN FADERS & 3-WAY CROSSOVER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioCyan,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 1. Main Input Fader
                    VerticalFader(
                        label = "MAIN IN",
                        subLabel = "Pre-FX",
                        valueDb = config.mainInputGainDb,
                        onValueChange = onMainInputGainChange,
                        accentColor = StudioCyan,
                        testTag = "fader_main_input"
                    )

                    // 2. Low Fade (Crossover Low band)
                    VerticalFader(
                        label = "LOW",
                        subLabel = "<${config.crossoverLowFreq.toInt()}Hz",
                        valueDb = config.lowFadeDb,
                        onValueChange = onLowFadeChange,
                        minDb = -24f,
                        maxDb = 12f,
                        accentColor = StudioYellow,
                        testTag = "fader_crossover_low"
                    )

                    // 3. Mid Fade (Crossover Mid band)
                    VerticalFader(
                        label = "MID",
                        subLabel = "${config.crossoverLowFreq.toInt()}-${config.crossoverHighFreq.toInt()}Hz",
                        valueDb = config.midFadeDb,
                        onValueChange = onMidFadeChange,
                        minDb = -24f,
                        maxDb = 12f,
                        accentColor = StudioYellow,
                        testTag = "fader_crossover_mid"
                    )

                    // 4. High Fade (Crossover High band)
                    VerticalFader(
                        label = "HIGH",
                        subLabel = ">${config.crossoverHighFreq.toInt()}Hz",
                        valueDb = config.highFadeDb,
                        onValueChange = onHighFadeChange,
                        minDb = -24f,
                        maxDb = 12f,
                        accentColor = StudioYellow,
                        testTag = "fader_crossover_high"
                    )

                    // 5. Main Output Fader
                    VerticalFader(
                        label = "MASTER",
                        subLabel = "Out L/R",
                        valueDb = config.mainOutputGainDb,
                        onValueChange = onMainOutputGainChange,
                        accentColor = StudioRed,
                        testTag = "fader_main_output"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Core Effects Strip: Trimmer Pre-gain, Stereo Panning, Stereo Delay
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "CORE EFFECTS & ROUTING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioCyan,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RotaryKnob(
                        label = "TRIMMER",
                        value = config.preGainDb,
                        onValueChange = onPreGainChange,
                        minValue = -24f,
                        maxValue = 24f,
                        centerValue = 0f,
                        displayValue = String.format(Locale.US, "%+.1fdB", config.preGainDb),
                        accentColor = StudioCyan,
                        testTag = "knob_trimmer"
                    )

                    RotaryKnob(
                        label = "TIME",
                        value = config.delayTimeMs,
                        onValueChange = onDelayTimeChange,
                        minValue = 10f,
                        maxValue = 1000f,
                        displayValue = "${config.delayTimeMs.toInt()}ms",
                        accentColor = StudioAmber,
                        testTag = "knob_delay_time"
                    )

                    RotaryKnob(
                        label = "FEEDBACK",
                        value = config.delayFeedback * 100f,
                        onValueChange = { onDelayFeedbackChange(it / 100f) },
                        minValue = 0f,
                        maxValue = 95f,
                        displayValue = "${(config.delayFeedback * 100).toInt()}%",
                        accentColor = StudioAmber,
                        testTag = "knob_delay_feedback"
                    )

                    RotaryKnob(
                        label = "WET MIX",
                        value = config.delayMix * 100f,
                        onValueChange = { onDelayMixChange(it / 100f) },
                        minValue = 0f,
                        maxValue = 100f,
                        displayValue = "${(config.delayMix * 100).toInt()}%",
                        accentColor = StudioAmber,
                        testTag = "knob_delay_mix"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                HorizontalPanSlider(
                    pan = config.pan,
                    onPanChange = onPanChange,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = config.delayEnabled,
                            onCheckedChange = { onToggleDelay() },
                            colors = SwitchDefaults.colors(checkedThumbColor = StudioAmber)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DELAY EFFECT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (config.delayEnabled) StudioAmber else TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = config.limiterEnabled,
                            onCheckedChange = { onToggleLimiter() },
                            colors = SwitchDefaults.colors(checkedThumbColor = StudioRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MASTER LIMITER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (config.limiterEnabled) StudioRed else TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
