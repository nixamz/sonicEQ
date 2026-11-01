package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.dsp.DspArchitectureRecommendation
import com.example.ui.theme.*

@Composable
fun DspTechSpecsScreen(
    cpuLoad: Float,
    activeBandsCount: Int,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        // Live DSP Engine Telemetry
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "REAL-TIME DSP ENGINE TELEMETRY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioCyan,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                val stats = listOf(
                    "Sample Rate" to "48,000 Hz (48 kHz)",
                    "Audio Channel Layout" to "Stereo (2 Channels)",
                    "Data Format" to "PCM 32-bit Floating Point (ENCODING_PCM_FLOAT)",
                    "Audio Buffer Size" to "512 frames (~10.66 ms latency budget)",
                    "Active Parametric Bands" to "$activeBandsCount / 60 Bands (Direct Form II Transposed)",
                    "Measured Audio Thread Load" to "${(cpuLoad * 100).toInt()}% budget utilized",
                    "Memory Allocation in Audio Loop" to "0 bytes (Zero-allocation pre-allocated buffers)"
                )

                stats.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, fontSize = 9.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        Text(text = value, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StudioNeonGreen, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Technical Comparison of Solutions
        Text(
            text = "AUDIO DSP ARCHITECTURE COMPARISON (1-60 BANDS)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = StudioCyan,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(6.dp))

        DspArchitectureRecommendation.COMPARISONS.forEach { item ->
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = StudioChassisDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        1.dp,
                        if (item.recommendationScore >= 9) StudioCyan.copy(alpha = 0.6f) else StudioBorder,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.recommendationScore >= 9) StudioCyan else TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (item.recommendationScore >= 9) StudioCyan else StudioCardBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${item.recommendationScore}/10",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.recommendationScore >= 9) StudioDarkBg else TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Max Bands: ${item.maxBands} | Latency: ${item.roundtripLatency}",
                        fontSize = 9.sp,
                        color = StudioAmber,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    item.pros.forEach { pro ->
                        Text(
                            text = "✓ $pro",
                            fontSize = 8.5.sp,
                            color = StudioNeonGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    item.cons.forEach { con ->
                        Text(
                            text = "✗ $con",
                            fontSize = 8.5.sp,
                            color = StudioRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
