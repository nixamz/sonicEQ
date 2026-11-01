package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.dsp.AudioMixerDspEngine

/**
 * Foreground Service for Android 10/11/12+ MediaProjection System Audio Capture.
 *
 * Android 12 (API 31/32) Compliance:
 * - Declared with android:foregroundServiceType="mediaProjection" in AndroidManifest.xml.
 * - Calls startForeground() immediately in onStartCommand() to prevent ForegroundServiceStartNotAllowedException.
 * - Obtains MediaProjection token and delivers it to the zero-allocation AudioMixerDspEngine.
 */
class SystemAudioCaptureService : Service() {

    companion object {
        private const val TAG = "SystemAudioCaptureSvc"
        private const val NOTIFICATION_ID = 4801
        private const val CHANNEL_ID = "sonic_system_audio_capture_channel"

        const val ACTION_START_CAPTURE = "com.example.action.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        // Shared engine instance reference
        var activeEngine: AudioMixerDspEngine? = null

        fun startCapture(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, SystemAudioCaptureService::class.java).apply {
                action = ACTION_START_CAPTURE
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopCapture(context: Context) {
            val intent = Intent(context, SystemAudioCaptureService::class.java).apply {
                action = ACTION_STOP_CAPTURE
            }
            context.startService(intent)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SystemAudioCaptureService = this@SystemAudioCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_CAPTURE) {
            stopSystemCapture()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_CAPTURE) {
            Log.i(TAG, "[MediaProjection] Step 3: Service onStartCommand received ACTION_START_CAPTURE.")

            // Android 12 requirement: Immediately call startForeground() with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            val notification = buildNotification("System Audio Capture Active")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isServiceRunning = true
            Log.i(TAG, "[MediaProjection] Step 4: Foreground service started with type=MEDIA_PROJECTION. isServiceRunning=true.")

            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            }

            Log.i(TAG, "[MediaProjection] Step 5: Preparing to get MediaProjection. resultCode=$resultCode, resultData=${if (resultData != null) "PRESENT" else "NULL"}")

            if (resultCode != 0 && resultData != null) {
                // Android 12 Async Lifecycle: Allow AMS a brief 100ms window to bind the FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                // before creating the AudioRecord capture policy, preventing "could not register audio policy" errors.
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isServiceRunning) return@postDelayed
                    try {
                        // Strict cleanup of any old media projection
                        try {
                            mediaProjection?.stop()
                        } catch (_: Exception) {}
                        mediaProjection = null

                        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        Log.i(TAG, "[MediaProjection] Step 6: Calling MediaProjectionManager.getMediaProjection(resultCode, resultData)...")
                        val projection = projectionManager.getMediaProjection(resultCode, resultData)

                        if (projection != null) {
                            mediaProjection = projection
                            Log.i(TAG, "[MediaProjection] Step 7: MediaProjection instance successfully acquired: $projection")

                            // Register required MediaProjection Callback
                            projection.registerCallback(object : MediaProjection.Callback() {
                                override fun onStop() {
                                    Log.w(TAG, "[MediaProjection] Callback.onStop() fired: MediaProjection token was revoked or stopped by system.")
                                    stopSystemCapture()
                                }
                            }, Handler(Looper.getMainLooper()))
                            Log.i(TAG, "[MediaProjection] Step 7b: MediaProjection.Callback registered on MainLooper.")

                            // Deliver projection to the zero-allocation DSP engine
                            if (activeEngine != null) {
                                Log.i(TAG, "[MediaProjection] Step 8: Delivering MediaProjection to AudioMixerDspEngine.")
                                activeEngine?.startSystemAudioCapture(projection)
                            } else {
                                Log.e(TAG, "[MediaProjection] ERROR: activeEngine reference is NULL! Cannot start DSP engine.")
                            }
                        } else {
                            Log.e(TAG, "[MediaProjection] ERROR: MediaProjectionManager.getMediaProjection returned NULL! Permission token invalid.")
                            activeEngine?.teardownCapture()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[MediaProjection] EXCEPTION obtaining MediaProjection: ${e.message}", e)
                        activeEngine?.teardownCapture()
                    }
                }, 100)
            } else {
                Log.w(TAG, "[MediaProjection] ERROR: Invalid resultCode ($resultCode) or resultData was null.")
            }
        }

        return START_STICKY
    }

    /**
     * Strict Zombie Policy Prevention Cleanup:
     * audioRecord?.stop() -> audioRecord?.release() -> mediaProjection?.stop()
     */
    private fun stopSystemCapture() {
        Log.i(TAG, "[Teardown] stopSystemCapture triggered in Foreground Service.")
        isServiceRunning = false

        // 1. audioRecord?.stop() -> audioRecord?.release() via activeEngine.teardownCapture()
        try {
            activeEngine?.teardownCapture()
        } catch (e: Exception) {
            Log.w(TAG, "[Teardown] Exception in activeEngine.teardownCapture()", e)
        }

        // 2. mediaProjection?.stop() strictly after AudioRecord is released
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        // 3. Stop Foreground notification and service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        Log.i(TAG, "[Teardown] System Audio Capture Service stopped cleanly.")
    }

    override fun onDestroy() {
        stopSystemCapture()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SonicMix System Audio DSP"
            val descriptionText = "Captures internal device playback and routes it through the DSP mixer"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, SystemAudioCaptureService::class.java).apply {
            action = ACTION_STOP_CAPTURE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SonicMix DSP Active")
            .setContentText(status)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Stop Capture", pendingStop)
            .build()
    }
}
