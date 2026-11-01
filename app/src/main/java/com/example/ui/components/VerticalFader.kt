package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Professional Hardware Audio Console Vertical Fader.
 *
 * State Management:
 * Uses local mutable state `remember(valueDb) { mutableFloatStateOf(valueDb) }`
 * to guarantee fluid, unblocked 60 FPS dragging without being frozen by recompositions.
 */
@Composable
fun VerticalFader(
    label: String,
    valueDb: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minDb: Float = -48.0f,
    maxDb: Float = 12.0f,
    height: Dp = 200.dp,
    width: Dp = 56.dp,
    accentColor: Color = StudioCyan,
    subLabel: String? = null,
    testTag: String = "fader_$label"
) {
    // Explicit Jetpack Compose state management requested:
    var sliderDb by remember(valueDb) { mutableFloatStateOf(valueDb) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(valueDb) {
        if (!isDragging) {
            sliderDb = valueDb
        }
    }

    val fraction = ((sliderDb - minDb) / (maxDb - minDb)).coerceIn(0.0f, 1.0f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .testTag(testTag)
            .width(width)
    ) {
        // Label
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        subLabel?.let {
            Text(
                text = it,
                fontSize = 9.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Readout badge (tap to reset to 0 dB)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(StudioCardBg)
                .border(0.5.dp, StudioBorder, RoundedCornerShape(4.dp))
                .clickable {
                    sliderDb = 0.0f
                    onValueChange(0.0f)
                }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            val formatted = when {
                sliderDb <= minDb + 0.5f -> "-∞ dB"
                sliderDb > 0.05f -> String.format(Locale.US, "+%.1f dB", sliderDb)
                sliderDb < -0.05f -> String.format(Locale.US, "%.1f dB", sliderDb)
                else -> "0.0 dB"
            }
            Text(
                text = formatted,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (abs(sliderDb) < 0.1f) StudioNeonGreen else TextPrimary,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Fader slot track & draggable knob container
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .pointerInput(minDb, maxDb) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true

                        val heightPx = size.height
                        val topY = 14.dp.toPx()
                        val bottomY = heightPx - 14.dp.toPx()
                        val usableHeight = (bottomY - topY).coerceAtLeast(1f)

                        fun calculateDbFromY(y: Float): Float {
                            val clampedY = y.coerceIn(topY, bottomY)
                            val frac = 1.0f - ((clampedY - topY) / usableHeight)
                            val raw = minDb + frac * (maxDb - minDb)
                            return if (abs(raw) < 0.4f) 0.0f else (raw * 10f).roundToInt() / 10f
                        }

                        // Immediate response on touch down
                        val initialDb = calculateDbFromY(down.position.y)
                        sliderDb = initialDb
                        onValueChange(initialDb)

                        // Process drag moves
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                val newDb = calculateDbFromY(change.position.y)
                                sliderDb = newDb
                                onValueChange(newDb)
                            } else {
                                break
                            }
                        }
                        isDragging = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidth = 4.dp.toPx()
                val trackX = (size.width - trackWidth) / 2f
                val topY = 14.dp.toPx()
                val bottomY = size.height - 14.dp.toPx()
                val usableHeight = bottomY - topY

                // Slot groove
                drawRoundRect(
                    color = FaderTrackSlot,
                    topLeft = Offset(trackX, topY),
                    size = Size(trackWidth, usableHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Calibration ticks
                val tickMarks = listOf(
                    12f to "+12",
                    6f to "+6",
                    0f to "0",
                    -6f to "-6",
                    -12f to "-12",
                    -24f to "-24",
                    -48f to "-∞"
                )

                for ((tickDb, _) in tickMarks) {
                    val tickFrac = ((tickDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                    val tickY = bottomY - (tickFrac * usableHeight)
                    val isZero = tickDb == 0f

                    drawLine(
                        color = if (isZero) StudioNeonGreen else FaderNotch.copy(alpha = 0.6f),
                        start = Offset(trackX - (if (isZero) 12.dp.toPx() else 6.dp.toPx()), tickY),
                        end = Offset(trackX - 2.dp.toPx(), tickY),
                        strokeWidth = if (isZero) 1.5.dp.toPx() else 1.dp.toPx()
                    )

                    drawLine(
                        color = if (isZero) StudioNeonGreen else FaderNotch.copy(alpha = 0.6f),
                        start = Offset(trackX + trackWidth + 2.dp.toPx(), tickY),
                        end = Offset(trackX + trackWidth + (if (isZero) 12.dp.toPx() else 6.dp.toPx()), tickY),
                        strokeWidth = if (isZero) 1.5.dp.toPx() else 1.dp.toPx()
                    )
                }

                // Fader Knob Cap
                val knobHeight = 28.dp.toPx()
                val knobWidth = 40.dp.toPx()
                val knobY = bottomY - (fraction * usableHeight) - (knobHeight / 2f)
                val knobX = (size.width - knobWidth) / 2f

                // Drop shadow
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(knobX, knobY + 2.dp.toPx()),
                    size = Size(knobWidth, knobHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Metallic Body
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            FaderCapSilver,
                            Color(0xFFCBD5E1),
                            Color(0xFF94A3B8),
                            Color(0xFF64748B)
                        ),
                        startY = knobY,
                        endY = knobY + knobHeight
                    ),
                    topLeft = Offset(knobX, knobY),
                    size = Size(knobWidth, knobHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Grip Grooves
                val grooveColor = Color(0xFF334155).copy(alpha = 0.7f)
                val grooveSpacing = 3.dp.toPx()
                for (g in -2..2) {
                    if (g != 0) {
                        val gy = knobY + (knobHeight / 2f) + (g * grooveSpacing)
                        drawLine(
                            color = grooveColor,
                            start = Offset(knobX + 6.dp.toPx(), gy),
                            end = Offset(knobX + knobWidth - 6.dp.toPx(), gy),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Center Notch Line
                val centerLineY = knobY + (knobHeight / 2f)
                drawLine(
                    color = if (isDragging) Color.White else accentColor,
                    start = Offset(knobX + 3.dp.toPx(), centerLineY),
                    end = Offset(knobX + knobWidth - 3.dp.toPx(), centerLineY),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }
    }
}
