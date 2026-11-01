package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Studio hardware rotary knob for pre-gain, delay, feedback, etc.
 * Uses local mutableFloatStateOf to guarantee immediate, fluid touch dragging.
 */
@Composable
fun RotaryKnob(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier,
    displayValue: String = "${value.toInt()}",
    size: Dp = 64.dp,
    accentColor: Color = StudioCyan,
    centerValue: Float? = null,
    testTag: String = "knob_$label"
) {
    var knobValue by remember(value) { mutableFloatStateOf(value) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDragging) {
            knobValue = value
        }
    }

    val fraction = ((knobValue - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val startAngle = 135f
    val sweepAngle = 270f
    val currentAngle = startAngle + (fraction * sweepAngle)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .testTag(testTag)
            .width(size + 16.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(minValue, maxValue) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        var lastY = down.position.y
                        var lastX = down.position.x

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                val dy = change.position.y - lastY
                                val dx = change.position.x - lastX
                                lastY = change.position.y
                                lastX = change.position.x

                                val deltaFrac = (-dy + dx) / 200f
                                val currentFrac = ((knobValue - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
                                val newFrac = (currentFrac + deltaFrac).coerceIn(0f, 1f)
                                val rawVal = minValue + newFrac * (maxValue - minValue)
                                val finalVal = if (centerValue != null && abs(rawVal - centerValue) < (maxValue - minValue) * 0.035f) {
                                    centerValue
                                } else {
                                    rawVal
                                }
                                knobValue = finalVal
                                onValueChange(finalVal)
                            } else {
                                break
                            }
                        }
                        isDragging = false
                    }
                }
                .clickable {
                    centerValue?.let {
                        knobValue = it
                        onValueChange(it)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val radius = (this.size.width / 2f) - 6.dp.toPx()

                // Background arc track
                drawArc(
                    color = FaderTrackSlot,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // Active value arc
                val activeSweep = fraction * sweepAngle
                drawArc(
                    color = accentColor,
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // Knob Center Dial
                val innerRadius = radius - 5.dp.toPx()
                drawCircle(
                    color = StudioCardBg,
                    radius = innerRadius,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = StudioBorder,
                    radius = innerRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Pointer line
                val rad = Math.toRadians(currentAngle.toDouble())
                val px = cx + (innerRadius * 0.85f * cos(rad)).toFloat()
                val py = cy + (innerRadius * 0.85f * sin(rad)).toFloat()
                drawLine(
                    color = if (isDragging) Color.White else accentColor,
                    start = Offset(cx, cy),
                    end = Offset(px, py),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Display value box
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(StudioCardBg)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = displayValue,
                fontSize = 9.sp,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Horizontal Stereo Panning Slider (-100% Left to +100% Right)
 * with center detent at 0.
 */
@Composable
fun HorizontalPanSlider(
    pan: Float,
    onPanChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var localPan by remember(pan) { mutableFloatStateOf(pan) }

    LaunchedEffect(pan) {
        localPan = pan
    }

    Column(
        modifier = modifier
            .testTag("pan_slider_container")
            .clip(RoundedCornerShape(6.dp))
            .background(StudioSurface)
            .border(0.5.dp, StudioBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STEREO PANNING",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = StudioAmber,
                fontFamily = FontFamily.Monospace
            )

            val panText = when {
                localPan <= -0.98f -> "L 100%"
                localPan >= 0.98f -> "R 100%"
                localPan < -0.05f -> "L ${(abs(localPan) * 100).toInt()}%"
                localPan > 0.05f -> "R ${(localPan * 100).toInt()}%"
                else -> "CENTER (0)"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(StudioDarkBg)
                    .clickable {
                        localPan = 0.0f
                        onPanChange(0.0f)
                    }
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = panText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (abs(localPan) < 0.05f) StudioNeonGreen else StudioAmber,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("L", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Slider(
                value = localPan,
                onValueChange = { raw ->
                    val snapped = if (abs(raw) < 0.06f) 0.0f else (raw * 100f).toInt() / 100f
                    localPan = snapped
                    onPanChange(snapped)
                },
                valueRange = -1.0f..1.0f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .testTag("pan_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = StudioAmber,
                    activeTrackColor = StudioAmber,
                    inactiveTrackColor = FaderTrackSlot
                )
            )
            Text("R", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        }
    }
}
