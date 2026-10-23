package com.airlink.mobile.ringer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PhoneRingerManager {
    private const val TAG = "AirLinkRinger"
    private const val AUTO_STOP_DELAY_MS = 60_000L // 1 minute auto-stop

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    private val autoStopRunnable = Runnable {
        Log.d(TAG, "Auto-stopping ringtone after timeout")
        stopRinging()
    }

    fun startRinging(context: Context) {
        mainHandler.post {
            try {
                if (_isRinging.value) {
                    Log.d(TAG, "Already ringing; resetting timeout")
                    mainHandler.removeCallbacks(autoStopRunnable)
                    mainHandler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
                    return@post
                }

                val appContext = context.applicationContext
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

                val ringtone = RingtoneManager.getRingtone(appContext, ringtoneUri)
                if (ringtone != null) {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    ringtone.audioAttributes = attributes

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.isLooping = true
                        ringtone.volume = 1.0f
                    }

                    ringtone.play()
                    activeRingtone = ringtone
                }

                // 4. Vibrate pattern simultaneously
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

                // Schedule auto-stop after 60s
                mainHandler.removeCallbacks(autoStopRunnable)
                mainHandler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
            } catch (t: Throwable) {
                Log.e(TAG, "Error starting phone ringer: ${t.message}", t)
            }
        }
    }

    fun stopRinging() {
        mainHandler.post {
            try {
                mainHandler.removeCallbacks(autoStopRunnable)
                activeRingtone?.stop()
                activeRingtone = null

                vibrator?.cancel()
                vibrator = null

                _isRinging.value = false
                Log.d(TAG, "Find My Phone: Ringing stopped")
            } catch (t: Throwable) {
                Log.w(TAG, "Error stopping ringtone: ${t.message}")
            }
        }
    }
}
