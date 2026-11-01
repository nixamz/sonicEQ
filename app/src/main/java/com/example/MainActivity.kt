package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AudioSourceMode
import com.example.ui.screens.DspTechSpecsScreen
import com.example.ui.screens.MixerConsoleScreen
import com.example.ui.screens.ParametricEqScreen
import com.example.ui.screens.RoutingEffectsScreen
import com.example.ui.theme.*
import com.example.viewmodel.MixerViewModel
import kotlinx.coroutines.flow.collectLatest

enum class MixerScreenTab(val label: String, val icon: ImageVector) {
    CONSOLE("MIXER", Icons.Default.Tune),
    EQ("PARAMETRIC EQ", Icons.Default.GraphicEq),
    ROUTING("ROUTING & FX", Icons.Default.MenuOpen),
    SPECS("DSP SPECS", Icons.Default.Info)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainMixerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMixerApp(viewModel: MixerViewModel = viewModel()) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsStateWithLifecycle()
    val audioLevels by viewModel.audioLevels.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val cpuLoad by viewModel.cpuLoad.collectAsStateWithLifecycle()
    val sourceMode by viewModel.sourceMode.collectAsStateWithLifecycle()
    val isSystemCaptureActive by viewModel.isSystemCaptureActive.collectAsStateWithLifecycle()
    val isBufferSilentWarning by viewModel.isBufferSilentWarning.collectAsStateWithLifecycle()
    val audioCaptureStatus by viewModel.audioCaptureStatus.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MixerScreenTab.CONSOLE) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Android 12 (API 31/32) MediaProjection Permission Launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.i("MainActivity", "[MediaProjection] Step 2: Permission dialog returned. resultCode=${result.resultCode} (RESULT_OK=${Activity.RESULT_OK})")
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.i("MainActivity", "[MediaProjection] Step 2b: Permission granted by user. Intent data present. Starting Foreground Service...")
            viewModel.startSystemCapture(context, result.resultCode, result.data!!)
        } else {
            Log.w("MainActivity", "[MediaProjection] Step 2b: Permission DENIED or cancelled by user (resultCode=${result.resultCode}).")
        }
    }

    // Android 13+ Notification Permission Launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted or denied */ }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setSourceMode(AudioSourceMode.MIC_INPUT)
            viewModel.dspEngine.start()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotif) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun requestSystemAudioCapture() {
        Log.i("MainActivity", "[MediaProjection] Step 1: Requesting MediaProjection permission intent from MediaProjectionManager...")
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkBg)
                    .border(0.5.dp, StudioBorder)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isPlaying) StudioNeonGreen else StudioAmber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SONICMIX DSP CONSOLE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = StudioCyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "${config.bands.size} BANDS | SD750G 48k",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioAmber,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MixerScreenTab.values().forEach { tab ->
                        val isSelected = tab == selectedTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) StudioCyan.copy(alpha = 0.2f) else StudioSurface)
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) StudioCyan else StudioBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedTab = tab }
                                .padding(vertical = 8.dp)
                                .testTag("tab_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) StudioCyan else TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tab.label,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) StudioCyan else TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(StudioDarkBg)
        ) {
            when (selectedTab) {
                MixerScreenTab.CONSOLE -> {
                    MixerConsoleScreen(
                        config = config,
                        audioLevels = audioLevels,
                        isPlaying = isPlaying,
                        cpuLoad = cpuLoad,
                        sourceMode = sourceMode,
                        isSystemCaptureActive = isSystemCaptureActive,
                        isBufferSilentWarning = isBufferSilentWarning,
                        audioCaptureStatus = audioCaptureStatus,
                        onRequestStartSystemCapture = { requestSystemAudioCapture() },
                        onStopSystemCapture = { viewModel.stopSystemCapture(context) },
                        onTogglePlay = {
                            if (isPlaying) {
                                viewModel.stopSystemCapture(context)
                            } else {
                                if (sourceMode == AudioSourceMode.SYSTEM_CAPTURE && !isSystemCaptureActive) {
                                    requestSystemAudioCapture()
                                } else {
                                    viewModel.togglePlayback(context)
                                }
                            }
                        },
                        onTriggerHardwareTestTone = { viewModel.triggerHardwareTestTone() },
                        onSelectSourceMode = { mode ->
                            if (mode == AudioSourceMode.SYSTEM_CAPTURE) {
                                requestSystemAudioCapture()
                            } else if (mode == AudioSourceMode.MIC_INPUT) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.setSourceMode(mode)
                                    viewModel.dspEngine.start()
                                }
                            } else {
                                viewModel.setSourceMode(mode)
                                viewModel.dspEngine.start()
                            }
                        },
                        onSaveConfig = { viewModel.saveCurrentConfiguration() },
                        onPreGainChange = { viewModel.updatePreGain(it) },
                        onPanChange = { viewModel.updatePan(it) },
                        onMainInputGainChange = { viewModel.updateMainInputGain(it) },
                        onMainOutputGainChange = { viewModel.updateMainOutputGain(it) },
                        onLowFadeChange = { viewModel.updateLowFade(it) },
                        onMidFadeChange = { viewModel.updateMidFade(it) },
                        onHighFadeChange = { viewModel.updateHighFade(it) },
                        onDelayTimeChange = { viewModel.updateDelayTime(it) },
                        onDelayFeedbackChange = { viewModel.updateDelayFeedback(it) },
                        onDelayMixChange = { viewModel.updateDelayMix(it) },
                        onToggleDelay = { viewModel.toggleDelayEnabled() },
                        onToggleLimiter = { viewModel.toggleLimiterEnabled() }
                    )
                }
                MixerScreenTab.EQ -> {
                    ParametricEqScreen(
                        config = config,
                        dspEngine = viewModel.dspEngine,
                        onSetBandCount = { viewModel.setBandCount(it) },
                        onBandGainChange = { id, gain -> viewModel.updateBandGain(id, gain) },
                        onBandFrequencyChange = { id, freq -> viewModel.updateBandFrequency(id, freq) },
                        onBandQChange = { id, q -> viewModel.updateBandQ(id, q) },
                        onBandFilterTypeChange = { id, type -> viewModel.updateBandFilterType(id, type) },
                        onToggleBandEnabled = { id -> viewModel.toggleBandEnabled(id) },
                        onToggleEqEnabled = { viewModel.toggleEqEnabled() },
                        onResetToFlat = { viewModel.resetEqGainsToFlat() },
                        onLoadPreset = { preset -> viewModel.loadPreset(preset) }
                    )
                }
                MixerScreenTab.ROUTING -> {
                    RoutingEffectsScreen(
                        config = config,
                        onCrossoverFrequenciesChange = { low, high -> viewModel.updateCrossoverFrequencies(low, high) },
                        onDelayTimeChange = { viewModel.updateDelayTime(it) },
                        onDelayFeedbackChange = { viewModel.updateDelayFeedback(it) },
                        onDelayMixChange = { viewModel.updateDelayMix(it) },
                        onToggleDelay = { viewModel.toggleDelayEnabled() }
                    )
                }
                MixerScreenTab.SPECS -> {
                    DspTechSpecsScreen(
                        cpuLoad = cpuLoad,
                        activeBandsCount = config.bands.size
                    )
                }
            }
        }
    }
}
