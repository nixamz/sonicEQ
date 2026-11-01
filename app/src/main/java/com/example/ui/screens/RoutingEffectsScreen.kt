package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MixerConfig
import com.example.ui.components.RotaryKnob
import com.example.ui.theme.*

@Composable
fun RoutingEffectsScreen(
    config: MixerConfig,
    onCrossoverFrequenciesChange: (Float, Float) -> Unit,
    onDelayTimeChange: (Float) -> Unit,
    onDelayFeedbackChange: (Float) -> Unit,
    onDelayMixChange: (Float) -> Unit,
    onToggleDelay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        // 1. Audio Signal Flow Block Diagram
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "AUDIO SIGNAL ROUTING ARCHITECTURE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioCyan,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                val routingSteps = listOf(
                    "1. Audio Source" to "Device Audio File (Decoded PCM) / Live Microphone Input",
                    "2. Trimmer Pre-Gain" to "Pre-gain adjustment [VU Meter: IN -> TRIM]",
                    "3. Main Input Fader" to "Channel gain scaling (-48dB to +12dB)",
                    "4. Parametric EQ Cascade" to "1 to 60 Biquad IIR filters in Direct Form II Transposed",
                    "5. 3-Way Crossover Bank" to "Low, Mid, High band splitting & fader gain [VU: LOW, MID, HIGH]",
                    "6. Stereo Delay FX" to "Circular buffer delay with feedback & wet mix [VU: FX]",
                    "7. Stereo Panning" to "Constant Power Pan Law: Gain_L = cos(θ), Gain_R = sin(θ)",
                    "8. Master Output Fader" to "Master level (-48dB to +12dB) + Soft-Knee Limiter [VU: OUT L, OUT R]"
                )

                routingSteps.forEachIndexed { index, (title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (index % 2 == 0) StudioSurface else StudioDarkBg)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioAmber,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = desc,
                                fontSize = 9.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. 3-Way Crossover Tuning Configuration
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "3-WAY CROSSOVER SPLIT FREQUENCIES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioCyan,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Controls the boundary frequencies separating the Low, Mid, and High crossover faders",
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RotaryKnob(
                        label = "LOW SPLIT",
                        value = config.crossoverLowFreq,
                        onValueChange = { onCrossoverFrequenciesChange(it, config.crossoverHighFreq) },
                        minValue = 50f,
                        maxValue = 800f,
                        centerValue = 250f,
                        displayValue = "${config.crossoverLowFreq.toInt()}Hz",
                        accentColor = StudioYellow,
                        testTag = "knob_crossover_low_freq"
                    )

                    RotaryKnob(
                        label = "HIGH SPLIT",
                        value = config.crossoverHighFreq,
                        onValueChange = { onCrossoverFrequenciesChange(config.crossoverLowFreq, it) },
                        minValue = 1000f,
                        maxValue = 12000f,
                        centerValue = 4000f,
                        displayValue = "${(config.crossoverHighFreq / 1000f).toInt()}kHz",
                        accentColor = StudioYellow,
                        testTag = "knob_crossover_high_freq"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Current Bands: Low (20Hz - ${config.crossoverLowFreq.toInt()}Hz) | Mid (${config.crossoverLowFreq.toInt()}Hz - ${config.crossoverHighFreq.toInt()}Hz) | High (${config.crossoverHighFreq.toInt()}Hz - 20kHz)",
                    fontSize = 9.sp,
                    color = StudioYellow,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Stereo Delay FX Routing
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STEREO DELAY EFFECT ROUTING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioCyan,
                        fontFamily = FontFamily.Monospace
                    )

                    Switch(
                        checked = config.delayEnabled,
                        onCheckedChange = { onToggleDelay() },
                        colors = SwitchDefaults.colors(checkedThumbColor = StudioAmber)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RotaryKnob(
                        label = "TIME",
                        value = config.delayTimeMs,
                        onValueChange = onDelayTimeChange,
                        minValue = 10f,
                        maxValue = 1000f,
                        displayValue = "${config.delayTimeMs.toInt()}ms",
                        accentColor = StudioAmber,
                        testTag = "knob_routing_delay_time"
                    )

                    RotaryKnob(
                        label = "FEEDBACK",
                        value = config.delayFeedback * 100f,
                        onValueChange = { onDelayFeedbackChange(it / 100f) },
                        minValue = 0f,
                        maxValue = 95f,
                        displayValue = "${(config.delayFeedback * 100).toInt()}%",
                        accentColor = StudioAmber,
                        testTag = "knob_routing_delay_feedback"
                    )

                    RotaryKnob(
                        label = "WET MIX",
                        value = config.delayMix * 100f,
                        onValueChange = { onDelayMixChange(it / 100f) },
                        minValue = 0f,
                        maxValue = 100f,
                        displayValue = "${(config.delayMix * 100).toInt()}%",
                        accentColor = StudioAmber,
                        testTag = "knob_routing_delay_mix"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
