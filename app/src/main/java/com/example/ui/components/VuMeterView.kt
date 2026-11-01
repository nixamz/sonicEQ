package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioLevels
import com.example.ui.theme.*
import kotlin.math.log10
import kotlin.math.max

/**
 * Hardware LED Segment VU Meter.
 * Displays real-time audio RMS level and floating peak-hold marker
 * with professional color segmentation (Green -> Yellow -> Orange -> Red Clip).
 */
@Composable
fun VuMeter(
    label: String,
    rmsValue: Float,
    peakValue: Float,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    width: Dp = 24.dp,
    isMaster: Boolean = false,
    tag: String = "vu_meter_$label"
) {
    // Convert linear amplitude (0.0 .. 1.2) to normalized meter scale (0.0 to 1.0)
    // Scale: -60dB -> 0.0, 0dB -> 0.85, +6dB -> 1.0
    val rmsNorm = amplitudeToMeterNormalized(rmsValue)
    val peakNorm = amplitudeToMeterNormalized(peakValue)
    val isClipping = peakValue >= 0.98f

    val clipLedColor by animateColorAsState(
        targetValue = if (isClipping) VuLedClip else VuLedOff,
        animationSpec = tween(durationMillis = 80),
        label = "clip_led"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .testTag(tag)
            .padding(horizontal = 2.dp)
    ) {
        // CLIP LED indicator
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(clipLedColor)
                .border(
                    0.5.dp,
                    if (isClipping) VuLedClip.copy(alpha = 0.8f) else Color.DarkGray,
                    RoundedCornerShape(3.dp)
                )
        )

        Spacer(modifier = Modifier.height(3.dp))

        // Main LED ladder
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(3.dp))
                .background(FaderTrackBg)
                .border(
                    width = if (isMaster) 1.dp else 0.5.dp,
                    color = if (isMaster) StudioCyan.copy(alpha = 0.5f) else StudioBorder,
                    shape = RoundedCornerShape(3.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val totalHeight = size.height
                val totalWidth = size.width
                val segmentCount = 28
                val segmentGap = 1.5.dp.toPx()
                val segmentHeight = (totalHeight - (segmentCount - 1) * segmentGap) / segmentCount

                for (i in 0 until segmentCount) {
                    // bottom segment is index 0, top segment is index 27
                    val segIndexFromBottom = segmentCount - 1 - i
                    val segFraction = (segIndexFromBottom.toFloat() + 0.5f) / segmentCount
                    val y = i * (segmentHeight + segmentGap)

                    val isLitRms = rmsNorm >= segFraction
                    val isLitPeak = kotlin.math.abs(peakNorm - segFraction) < (1.0f / segmentCount)

                    val activeColor = when {
                        segFraction > 0.88f -> VuLedClip // Red > 0dB
                        segFraction > 0.78f -> VuLedWarning // Orange
                        segFraction > 0.65f -> VuLedCaution // Yellow
                        segFraction > 0.40f -> VuLedWarm // Lime
                        else -> VuLedNormal // Green
                    }

                    val color = when {
                        isLitPeak -> VuLedClip
                        isLitRms -> activeColor
                        else -> VuLedOff
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(1.5.dp.toPx(), y),
                        size = Size(totalWidth - 3.dp.toPx(), segmentHeight),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Meter Label
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isMaster) FontWeight.Bold else FontWeight.Medium,
            color = if (isMaster) StudioCyan else TextSecondary,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

/**
 * Rack displaying all 8 VU meters:
 * - 6 Custom Routing & Processing Meters: IN, TRIM, LOW, MID, HIGH, FX
 * - 2 Master Output Meters: OUT L, OUT R
 */
@Composable
fun AllVuMetersRack(
    audioLevels: AudioLevels,
    modifier: Modifier = Modifier,
    meterHeight: Dp = 120.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(StudioChassisDark)
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REAL-TIME VU LEVEL METERS (8 CHANNELS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = StudioCyan,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "dBFS RMS / PEAK HOLD",
                fontSize = 9.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Section 1: 6 Custom Audio Routing & Processing Indicators
            Row(
                modifier = Modifier
                    .weight(3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioSurface.copy(alpha = 0.7f))
                    .border(0.5.dp, StudioBorder.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // dB Scale markings
                DbScaleTicks(height = meterHeight)

                // 1. IN (Pre-trim)
                VuMeter(
                    label = "PRE",
                    rmsValue = audioLevels.inPreTrimRms,
                    peakValue = audioLevels.inPreTrimPeak,
                    height = meterHeight,
                    tag = "vu_in_pre"
                )

                // 2. TRIM (Post-trim)
                VuMeter(
                    label = "TRIM",
                    rmsValue = audioLevels.inPostTrimRms,
                    peakValue = audioLevels.inPostTrimPeak,
                    height = meterHeight,
                    tag = "vu_in_trim"
                )

                // 3. LOW (Low crossover band)
                VuMeter(
                    label = "LOW",
                    rmsValue = audioLevels.crossoverLowRms,
                    peakValue = audioLevels.crossoverLowPeak,
                    height = meterHeight,
                    tag = "vu_crossover_low"
                )

                // 4. MID (Mid crossover band)
                VuMeter(
                    label = "MID",
                    rmsValue = audioLevels.crossoverMidRms,
                    peakValue = audioLevels.crossoverMidPeak,
                    height = meterHeight,
                    tag = "vu_crossover_mid"
                )

                // 5. HIGH (High crossover band)
                VuMeter(
                    label = "HIGH",
                    rmsValue = audioLevels.crossoverHighRms,
                    peakValue = audioLevels.crossoverHighPeak,
                    height = meterHeight,
                    tag = "vu_crossover_high"
                )

                // 6. FX (Delay return)
                VuMeter(
                    label = "DELAY",
                    rmsValue = audioLevels.delayReturnRms,
                    peakValue = audioLevels.delayReturnPeak,
                    height = meterHeight,
                    tag = "vu_fx_delay"
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Section 2: 2 Dedicated Main Output Indicators (Left and Right)
            Row(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioCardBg)
                    .border(1.dp, StudioCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // 7. OUT L
                VuMeter(
                    label = "OUT L",
                    rmsValue = audioLevels.masterLeftRms,
                    peakValue = audioLevels.masterLeftPeak,
                    height = meterHeight,
                    isMaster = true,
                    tag = "vu_out_left"
                )

                // 8. OUT R
                VuMeter(
                    label = "OUT R",
                    rmsValue = audioLevels.masterRightRms,
                    peakValue = audioLevels.masterRightPeak,
                    height = meterHeight,
                    isMaster = true,
                    tag = "vu_out_right"
                )
            }
        }
    }
}

@Composable
fun DbScaleTicks(height: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(height)
            .padding(end = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        Text("+6", fontSize = 7.sp, color = VuLedClip, fontFamily = FontFamily.Monospace)
        Text("0", fontSize = 7.sp, color = VuLedCaution, fontFamily = FontFamily.Monospace)
        Text("-6", fontSize = 7.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        Text("-12", fontSize = 7.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        Text("-24", fontSize = 7.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        Text("-48", fontSize = 7.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
    }
}

private fun amplitudeToMeterNormalized(amp: Float): Float {
    if (amp <= 0.0001f) return 0.0f
    // Convert linear amplitude to dB
    val db = (20.0 * log10(max(1e-5, amp.toDouble()))).toFloat()
    // Map -60dB -> 0.0, 0dB -> 0.85, +6dB -> 1.0
    return when {
        db <= -60f -> 0.0f
        db <= 0f -> (db + 60f) / 60f * 0.85f
        else -> 0.85f + (db / 6f).coerceIn(0f, 1f) * 0.15f
    }.coerceIn(0.0f, 1.0f)
}
