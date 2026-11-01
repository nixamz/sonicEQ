package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.FilterType
import com.example.model.ParametricBand
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Individual channel strip for a single Parametric EQ Band.
 * Features:
 * - Band ID and Bypass toggle
 * - Direct Frequency (Hz) editor modal
 * - Precision vertical Gain slider (-24dB to +24dB)
 * - Filter type selector (Peak, Shelf, Cut, Notch)
 * - Q factor adjuster
 */
@Composable
fun ParametricBandStrip(
    band: ParametricBand,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onGainChange: (Float) -> Unit,
    onFrequencyChange: (Float) -> Unit,
    onQChange: (Float) -> Unit,
    onFilterTypeChange: (FilterType) -> Unit,
    onToggleEnable: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFreqDialog by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }

    val borderColor = when {
        isSelected -> StudioAmber
        band.isEnabled -> StudioBorder
        else -> StudioBorder.copy(alpha = 0.4f)
    }

    val stripBg = when {
        isSelected -> StudioCardBg
        band.isEnabled -> StudioSurface
        else -> StudioChassisDark.copy(alpha = 0.6f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .testTag("band_strip_${band.id}")
            .width(82.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(stripBg)
            .border(if (isSelected) 1.5.dp else 0.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        // Band Header & Enable Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${band.id}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) StudioAmber else StudioCyan,
                fontFamily = FontFamily.Monospace
            )

            // On/Off Bypass Button
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (band.isEnabled) StudioNeonGreen.copy(alpha = 0.2f) else StudioCardBg)
                    .border(
                        0.5.dp,
                        if (band.isEnabled) StudioNeonGreen else TextMuted,
                        CircleShape
                    )
                    .clickable { onToggleEnable() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Toggle Band ${band.id}",
                    tint = if (band.isEnabled) StudioNeonGreen else TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Frequency Label & Tap to Edit
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(StudioDarkBg)
                .border(0.5.dp, StudioBorder, RoundedCornerShape(4.dp))
                .clickable { showFreqDialog = true }
                .padding(vertical = 3.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            val freqText = if (band.frequency >= 1000f) {
                String.format(Locale.US, "%.1fk", band.frequency / 1000f)
            } else {
                "${band.frequency.toInt()}Hz"
            }
            Text(
                text = freqText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (band.isEnabled) TextPrimary else TextMuted,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Filter Type Selector Button
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(StudioCardBg)
                    .border(0.5.dp, StudioBorder, RoundedCornerShape(3.dp))
                    .clickable { showTypeMenu = true }
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = band.filterType.shortName,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StudioAmber,
                    fontFamily = FontFamily.Monospace
                )
            }

            DropdownMenu(
                expanded = showTypeMenu,
                onDismissRequest = { showTypeMenu = false },
                modifier = Modifier.background(StudioSurface)
            ) {
                FilterType.values().forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = type.displayName,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        },
                        onClick = {
                            onFilterTypeChange(type)
                            showTypeMenu = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Gain Value Readout
        val gainText = when {
            band.gainDb > 0.05f -> String.format(Locale.US, "+%.1f", band.gainDb)
            band.gainDb < -0.05f -> String.format(Locale.US, "%.1f", band.gainDb)
            else -> "0.0"
        }
        Text(
            text = "$gainText dB",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                !band.isEnabled -> TextMuted
                abs(band.gainDb) < 0.1f -> TextSecondary
                band.gainDb > 0 -> StudioCyan
                else -> StudioOrange
            },
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Vertical Gain Slider (-24 dB to +24 dB)
        BandGainSlider(
            gainDb = band.gainDb,
            isEnabled = band.isEnabled,
            onGainChange = onGainChange,
            modifier = Modifier
                .height(130.dp)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Q Factor Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(StudioDarkBg)
                    .clickable { onQChange((band.q - 0.2f).coerceAtLeast(0.1f)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease Q",
                    tint = TextSecondary,
                    modifier = Modifier.size(10.dp)
                )
            }

            Text(
                text = String.format(Locale.US, "Q:%.1f", band.q),
                fontSize = 8.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(StudioDarkBg)
                    .clickable { onQChange((band.q + 0.2f).coerceAtMost(10f)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase Q",
                    tint = TextSecondary,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }

    // Modal to customize specific center frequency (Hz)
    if (showFreqDialog) {
        FrequencyInputDialog(
            bandId = band.id,
            currentFreq = band.frequency,
            onDismiss = { showFreqDialog = false },
            onConfirm = { newFreq ->
                onFrequencyChange(newFreq)
                showFreqDialog = false
            }
        )
    }
}

/**
 * Vertical gain slider for the band strip.
 */
@Composable
private fun BandGainSlider(
    gainDb: Float,
    isEnabled: Boolean,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val minDb = -24.0f
    val maxDb = 24.0f
    var sliderGain by remember(gainDb) { mutableFloatStateOf(gainDb) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(gainDb) {
        if (!isDragging) {
            sliderGain = gainDb
        }
    }

    val fraction = ((sliderGain - minDb) / (maxDb - minDb)).coerceIn(0.0f, 1.0f)

    Box(
        modifier = modifier
            .pointerInput(minDb, maxDb, isEnabled) {
                if (!isEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true

                    val heightPx = size.height
                    val topY = 8.dp.toPx()
                    val bottomY = heightPx - 8.dp.toPx()
                    val usableH = (bottomY - topY).coerceAtLeast(1f)

                    fun calculateGainFromY(y: Float): Float {
                        val clampedY = y.coerceIn(topY, bottomY)
                        val frac = 1.0f - ((clampedY - topY) / usableH)
                        val raw = minDb + frac * (maxDb - minDb)
                        return if (abs(raw) < 0.35f) 0.0f else (raw * 10f).roundToInt() / 10f
                    }

                    val initialGain = calculateGainFromY(down.position.y)
                    sliderGain = initialGain
                    onGainChange(initialGain)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            change.consume()
                            val newGain = calculateGainFromY(change.position.y)
                            sliderGain = newGain
                            onGainChange(newGain)
                        } else {
                            break
                        }
                    }
                    isDragging = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = 3.dp.toPx()
            val trackX = (size.width - trackWidth) / 2f
            val topY = 8.dp.toPx()
            val bottomY = size.height - 8.dp.toPx()
            val usableH = bottomY - topY

            // Draw center groove
            drawRoundRect(
                color = FaderTrackSlot,
                topLeft = Offset(trackX, topY),
                size = androidx.compose.ui.geometry.Size(trackWidth, usableH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )

            // 0 dB Center mark
            val centerZeroY = bottomY - (0.5f * usableH)
            drawLine(
                color = StudioNeonGreen.copy(alpha = 0.7f),
                start = Offset(trackX - 6.dp.toPx(), centerZeroY),
                end = Offset(trackX + trackWidth + 6.dp.toPx(), centerZeroY),
                strokeWidth = 1.dp.toPx()
            )

            // Thumb cap
            val thumbH = 18.dp.toPx()
            val thumbW = 28.dp.toPx()
            val thumbY = bottomY - (fraction * usableH) - (thumbH / 2f)
            val thumbX = (size.width - thumbW) / 2f

            // Thumb body
            drawRoundRect(
                color = if (isEnabled) FaderCapSilver else Color.DarkGray,
                topLeft = Offset(thumbX, thumbY),
                size = androidx.compose.ui.geometry.Size(thumbW, thumbH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Center indicator
            drawLine(
                color = if (isEnabled) StudioCyan else TextMuted,
                start = Offset(thumbX + 2.dp.toPx(), thumbY + thumbH / 2f),
                end = Offset(thumbX + thumbW - 2.dp.toPx(), thumbY + thumbH / 2f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
fun FrequencyInputDialog(
    bandId: Int,
    currentFreq: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var textValue by remember { mutableStateOf(currentFreq.toInt().toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = StudioCardBg),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Band #$bandId Center Frequency",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioCyan
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Frequency (Hz)") },
                    suffix = { Text("Hz") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick frequency step buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val quickFreqs = listOf(60, 250, 1000, 4000, 12000)
                    quickFreqs.forEach { qf ->
                        OutlinedButton(
                            onClick = { textValue = qf.toString() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            val label = if (qf >= 1000) "${qf / 1000}k" else "${qf}"
                            Text(label, fontSize = 9.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsed = textValue.toFloatOrNull() ?: currentFreq
                            onConfirm(parsed.coerceIn(20f, 20000f))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
                    ) {
                        Text("Apply", color = StudioDarkBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
