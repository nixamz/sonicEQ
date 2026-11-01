package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
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
import com.example.audio.dsp.AudioMixerDspEngine
import com.example.model.FilterType
import com.example.model.MixerConfig
import com.example.ui.components.EqCurveGraph
import com.example.ui.components.ParametricBandStrip
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ParametricEqScreen(
    config: MixerConfig,
    dspEngine: AudioMixerDspEngine,
    onSetBandCount: (Int) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onBandFrequencyChange: (Int, Float) -> Unit,
    onBandQChange: (Int, Float) -> Unit,
    onBandFilterTypeChange: (Int, FilterType) -> Unit,
    onToggleBandEnabled: (Int) -> Unit,
    onToggleEqEnabled: () -> Unit,
    onResetToFlat: () -> Unit,
    onLoadPreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBandId by remember { mutableStateOf<Int?>(1) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        // 1. EQ Header: Enable toggle, Band Count, Reset
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = config.eqEnabled,
                            onCheckedChange = { onToggleEqEnabled() },
                            colors = SwitchDefaults.colors(checkedThumbColor = StudioCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (config.eqEnabled) "PARAMETRIC EQ (ACTIVE)" else "PARAMETRIC EQ (BYPASS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (config.eqEnabled) StudioCyan else TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Reset all gains to 0 dB
                    OutlinedButton(
                        onClick = onResetToFlat,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Gains",
                            tint = StudioCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("FLAT", fontSize = 9.sp, color = StudioCyan)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Band Count Stepper & Slider (1 to 60 Bands)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE BANDS: ${config.bands.size} / 60",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioAmber,
                        fontFamily = FontFamily.Monospace
                    )

                    var localBandCount by remember(config.bands.size) { mutableFloatStateOf(config.bands.size.toFloat()) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(StudioDarkBg)
                                .border(0.5.dp, StudioBorder, CircleShape)
                                .clickable {
                                    if (config.bands.size > 1) {
                                        val newCount = config.bands.size - 1
                                        localBandCount = newCount.toFloat()
                                        onSetBandCount(newCount)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease Band Count",
                                tint = TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Slider(
                            value = localBandCount,
                            onValueChange = {
                                localBandCount = it
                                onSetBandCount(it.toInt())
                            },
                            valueRange = 1f..60f,
                            steps = 58,
                            modifier = Modifier
                                .width(140.dp)
                                .padding(horizontal = 6.dp)
                                .testTag("band_count_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = StudioAmber,
                                activeTrackColor = StudioAmber,
                                inactiveTrackColor = FaderTrackSlot
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(StudioDarkBg)
                                .border(0.5.dp, StudioBorder, CircleShape)
                                .clickable {
                                    if (config.bands.size < 60) {
                                        onSetBandCount(config.bands.size + 1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Band Count",
                                tint = TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Presets Bar
                Text(
                    text = "EQ PRESETS & CURVES:",
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "10-Band Graphic",
                        "31-Band 1/3 Octave ISO",
                        "60-Band Surgical Dense",
                        "Bass Heavy & Sub",
                        "Vocal Presence & Air",
                        "V-Curve Smile"
                    )
                    presets.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(StudioCardBg)
                                .border(0.5.dp, StudioBorder, RoundedCornerShape(4.dp))
                                .clickable { onLoadPreset(preset) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 9.sp,
                                color = StudioCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Real-Time Frequency Response Curve Canvas
        EqCurveGraph(
            bands = config.bands,
            dspEngine = dspEngine,
            selectedBandId = selectedBandId,
            onSelectBand = { id ->
                selectedBandId = id
                coroutineScope.launch {
                    val index = config.bands.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        lazyListState.animateScrollToItem(index)
                    }
                }
            },
            isEqEnabled = config.eqEnabled,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Horizontally Scrollable 1-60 Bands Rack
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BAND CHANNEL STRIPS (HORIZONTAL RACK)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "SCROLL ⇄ TO ACCESS 1-${config.bands.size} BANDS",
                        fontSize = 8.sp,
                        color = StudioAmber,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Horizontally scrollable bands rack
                LazyRow(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bands_lazy_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(config.bands, key = { it.id }) { band ->
                        ParametricBandStrip(
                            band = band,
                            isSelected = band.id == selectedBandId,
                            onSelect = { selectedBandId = band.id },
                            onGainChange = { gain -> onBandGainChange(band.id, gain) },
                            onFrequencyChange = { freq -> onBandFrequencyChange(band.id, freq) },
                            onQChange = { q -> onBandQChange(band.id, q) },
                            onFilterTypeChange = { type -> onBandFilterTypeChange(band.id, type) },
                            onToggleEnable = { onToggleBandEnabled(band.id) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
