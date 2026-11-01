package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.dsp.AudioMixerDspEngine
import com.example.model.ParametricBand
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Interactive Real-Time 1-60 Band Parametric EQ Curve Graph.
 * Renders the exact mathematical magnitude response across 20Hz - 20kHz logarithmic spectrum.
 */
@Composable
fun EqCurveGraph(
    bands: List<ParametricBand>,
    dspEngine: AudioMixerDspEngine,
    selectedBandId: Int?,
    onSelectBand: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isEqEnabled: Boolean = true
) {
    val minFreq = 20.0f
    val maxFreq = 20000.0f
    val logMin = log10(minFreq)
    val logMax = log10(maxFreq)
    val minDb = -24.0f
    val maxDb = 24.0f

    // Sample 120 points logarithmically for high-precision smooth spline
    val numPoints = 120
    val sampleFreqs = remember {
        FloatArray(numPoints) { i ->
            val frac = i.toFloat() / (numPoints - 1)
            10.0.pow((logMin + frac * (logMax - logMin)).toDouble()).toFloat()
        }
    }

    Box(
        modifier = modifier
            .testTag("eq_curve_graph")
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(StudioDarkBg)
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bands) {
                    detectTapGestures { tapOffset ->
                        val tapX = tapOffset.x
                        val widthPx = size.width
                        val tapFrac = (tapX / widthPx).coerceIn(0f, 1f)
                        val tapFreq = 10.0.pow((logMin + tapFrac * (logMax - logMin)).toDouble()).toFloat()

                        // Find closest band
                        val closest = bands.minByOrNull { abs(log10(it.frequency) - log10(tapFreq)) }
                        closest?.let { onSelectBand(it.id) }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            fun freqToX(f: Float): Float {
                val clamped = f.coerceIn(minFreq, maxFreq)
                val frac = (log10(clamped) - logMin) / (logMax - logMin)
                return frac * width
            }

            fun dbToY(db: Float): Float {
                val clamped = db.coerceIn(minDb, maxDb)
                val frac = (clamped - minDb) / (maxDb - minDb)
                return (1.0f - frac) * height
            }

            // 1. Draw dB Horizontal Grid Lines
            val dbTicks = listOf(18f, 12f, 6f, 0f, -6f, -12f, -18f)
            for (db in dbTicks) {
                val y = dbToY(db)
                val isZero = db == 0f
                drawLine(
                    color = if (isZero) StudioBorder.copy(alpha = 0.9f) else StudioBorder.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (isZero) 1.5.dp.toPx() else 0.8.dp.toPx()
                )
            }

            // 2. Draw Frequency Logarithmic Vertical Grid Lines
            val freqTicks = listOf(
                50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f
            )
            for (f in freqTicks) {
                val x = freqToX(f)
                val isMajor = f == 100f || f == 1000f || f == 10000f
                drawLine(
                    color = if (isMajor) StudioBorder.copy(alpha = 0.6f) else StudioBorder.copy(alpha = 0.25f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = if (isMajor) 1.dp.toPx() else 0.5.dp.toPx()
                )
            }

            // 3. Compute and Draw Combined EQ Response Curve
            val curvePath = Path()
            val fillPath = Path()
            val zeroY = dbToY(0f)

            fillPath.moveTo(0f, zeroY)

            for (i in 0 until numPoints) {
                val freq = sampleFreqs[i]
                val magDb = if (isEqEnabled) dspEngine.computeCombinedFrequencyResponseDb(freq) else 0.0f
                val x = freqToX(freq)
                val y = dbToY(magDb)

                if (i == 0) {
                    curvePath.moveTo(x, y)
                    fillPath.lineTo(x, y)
                } else {
                    curvePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, zeroY)
            fillPath.close()

            // Draw Area Glow
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        StudioCyan.copy(alpha = 0.25f),
                        StudioCyan.copy(alpha = 0.03f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw Stroke Curve
            drawPath(
                path = curvePath,
                color = if (isEqEnabled) StudioCyan else TextMuted,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Draw Individual Band Indicator Nodes
            for (band in bands) {
                val bx = freqToX(band.frequency)
                val by = dbToY(if (isEqEnabled && band.isEnabled) band.gainDb else 0f)
                val isSelected = band.id == selectedBandId

                // Halo glow if selected
                if (isSelected) {
                    drawCircle(
                        color = StudioCyan.copy(alpha = 0.35f),
                        radius = 12.dp.toPx(),
                        center = Offset(bx, by)
                    )
                }

                // Node Center
                val nodeColor = when {
                    !band.isEnabled -> TextMuted
                    isSelected -> StudioAmber
                    abs(band.gainDb) > 0.5f -> StudioCyan
                    else -> TextSecondary
                }

                drawCircle(
                    color = nodeColor,
                    radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                    center = Offset(bx, by)
                )
            }
        }

        // Overlay Grid Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("+24dB", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text(
                text = "${bands.size} PARAMETRIC BANDS " + if (isEqEnabled) "(ACTIVE)" else "(BYPASSED)",
                fontSize = 9.sp,
                color = if (isEqEnabled) StudioCyan else TextMuted,
                fontFamily = FontFamily.Monospace
            )
            Text("-24dB", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("20Hz", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("100Hz", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("1kHz", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("10kHz", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("20kHz", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        }
    }
}
