package com.airlink.mobile.ringer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.airlink.mobile.R
import com.airlink.mobile.network.AirLinkWebSocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PhoneRingerManager {
    private const val TAG = "AirLinkRinger"
    private const val AUTO_STOP_DELAY_MS = 60_000L // 1 minute auto-stop
    private const val NOTIFICATION_ID = 2002
    private const val CHANNEL_ID = "airlink_find_my_phone_channel"
    private const val ACTION_STOP_FIND_PHONE = "com.airlink.mobile.action.STOP_FIND_PHONE"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var activeRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var lastAppContext: Context? = null

    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    private val autoStopRunnable = Runnable {
        Log.d(TAG, "Auto-stopping ringtone after timeout")
        stopRinging()
    }

    fun startRinging(context: Context) {
        mainHandler.post {
            try {
                val appContext = context.applicationContext
                lastAppContext = appContext

                if (_isRinging.value) {
                    Log.d(TAG, "Already ringing; resetting timeout")
                    mainHandler.removeCallbacks(autoStopRunnable)
                    mainHandler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
                    showAlarmNotification(appContext)
                    return@post
                }

                val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                // 1. Force ringer mode to NORMAL (override silent / vibrate)
                try {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not set ringerMode: ${t.message}")
                }

                // 2. Set max volume across ring, alarm, and music streams
                val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not set stream volume: ${t.message}")
                }

                // 3. Resolve default ringtone URI (fallback to alarm or notification)
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                // 4. Play ringtone using MediaPlayer with fallback to Ringtone
                try {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(appContext, ringtoneUri)
                        isLooping = true
                        setVolume(1.0f, 1.0f)
                        prepare()
                        start()
                    }
                    mediaPlayer = mp
                } catch (t: Throwable) {
                    Log.w(TAG, "MediaPlayer failed: ${t.message}, fallback to RingtoneManager")
                    val ringtone = RingtoneManager.getRingtone(appContext, ringtoneUri)
                    if (ringtone != null) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ringtone.isLooping = true
                            ringtone.volume = 1.0f
                        }
                        ringtone.play()
                        activeRingtone = ringtone
                    }
                }

                // 5. Vibrate pattern simultaneously
                try {
                    val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                        manager?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }

                    if (vib != null && vib.hasVibrator()) {
                        val pattern = longArrayOf(0, 800, 400, 800, 400)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
                        } else {
                            @Suppress("DEPRECATION")
                            vib.vibrate(pattern, 0)
                        }
                        vibrator = vib
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not activate vibrator: ${t.message}")
                }

                _isRinging.value = true
                Log.d(TAG, "Find My Phone: Ringing started at maximum volume")

                // 6. Post Full-Screen Intent high-priority notification & launch overlay Activity
                showAlarmNotification(appContext)

                // Schedule auto-stop after 60s
                mainHandler.removeCallbacks(autoStopRunnable)
                mainHandler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
            } catch (t: Throwable) {
                Log.e(TAG, "Error starting phone ringer: ${t.message}", t)
            }
        }
    }

    private fun showAlarmNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Find My Phone Alert",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority alarm notification when Mac triggers Find My Phone"
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableLights(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 400, 800, 400)
                    setSound(null, null) // Audio is managed directly by MediaPlayer
                }
                notificationManager.createNotificationChannel(channel)
            }

            val fullScreenIntent = Intent(context, FindMyPhoneActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                fullScreenIntent,
                flags
            )

            val stopBroadcastIntent = Intent(context, FindMyPhoneStopReceiver::class.java).apply {
                action = ACTION_STOP_FIND_PHONE
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID + 1,
                stopBroadcastIntent,
                flags
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Find My Phone Ringing")
                .setContentText("Mac is requesting to locate this device at maximum volume.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop Ringing",
                    stopPendingIntent
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)

            // Directly launch the overlay activity so it pops up immediately over any currently running app
            context.startActivity(fullScreenIntent)
        } catch (t: Throwable) {
            Log.e(TAG, "Error showing alarm notification: ${t.message}", t)
        }
    }

    private fun cancelAlarmNotification(context: Context?) {
        try {
            val ctx = context ?: lastAppContext ?: return
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (t: Throwable) {
            Log.w(TAG, "Error cancelling alarm notification: ${t.message}")
        }
    }

    fun stopRinging(context: Context? = null) {
        mainHandler.post {
            try {
                mainHandler.removeCallbacks(autoStopRunnable)

                // 1. Stop MediaPlayer
                try {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            it.stop()
                        }
                        it.reset()
                        it.release()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error stopping MediaPlayer: ${t.message}")
                } finally {
                    mediaPlayer = null
                }

                // 2. Stop Ringtone
                activeRingtone?.stop()
                activeRingtone = null

                // 3. Stop Vibrator
                vibrator?.cancel()
                vibrator = null

                // 4. Cancel Notification
                cancelAlarmNotification(context ?: lastAppContext)

                // 5. Update State
                _isRinging.value = false
                Log.d(TAG, "Find My Phone: Ringing stopped")

                // 6. Notify Mac over WebSocket
                AirLinkWebSocketManager.sendStopFindPhone()
            } catch (t: Throwable) {
                Log.w(TAG, "Error stopping ringtone: ${t.message}")
            }
        }
    }
}
