package com.airlink.mobile.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Monitors battery percentage and charging status using Intent.ACTION_BATTERY_CHANGED.
 * Dispatches updates to [onBatteryChanged] whenever a significant change occurs (plugged/unplugged,
 * percentage change) or periodically every 2 minutes.
 */
class BatteryMonitor(
    private val context: Context,
    private val onBatteryChanged: (level: Int, isCharging: Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var currentLevel: Int = -1
        private set

    @Volatile
    var isCharging: Boolean = false
        private set

    private var lastSentLevel: Int = -1
    private var lastSentCharging: Boolean? = null
    private var lastSentTimestamp: Long = 0L

    private var isRegistered = false

    private val periodicRunnable = object : Runnable {
        override fun run() {
            sendUpdate(force = true)
            mainHandler.postDelayed(this, PERIODIC_INTERVAL_MS)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                handleBatteryIntent(intent)
            }
        }
    }

    fun start() {
        if (isRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // ACTION_BATTERY_CHANGED is a system sticky broadcast
        val stickyIntent = ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        isRegistered = true

        // Read initial sticky state immediately
        if (stickyIntent != null) {
            handleBatteryIntent(stickyIntent)
        }

        // Schedule periodic update every 2 minutes
        mainHandler.removeCallbacks(periodicRunnable)
        mainHandler.postDelayed(periodicRunnable, PERIODIC_INTERVAL_MS)
        Log.d(TAG, "BatteryMonitor started (periodic interval: 2 minutes)")
    }

    fun stop() {
        if (!isRegistered) return
        try {
            appContext.unregisterReceiver(receiver)
        } catch (t: Throwable) {
            Log.w(TAG, "Error unregistering receiver: ${t.message}")
        }
        isRegistered = false
        mainHandler.removeCallbacks(periodicRunnable)
        Log.d(TAG, "BatteryMonitor stopped")
    }

    /**
     * Forces immediate dispatch of current battery status (e.g. upon establishing connection).
     */
    fun sendCurrentStatus() {
        if (currentLevel >= 0) {
            sendUpdate(force = true)
        } else {
            val sticky = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (sticky != null) {
                handleBatteryIntent(sticky)
            }
        }
    }

    private fun handleBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val computedLevel = if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            level.coerceIn(0, 100)
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val chargingStatus = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val pluggedCharging = chargingStatus || plugType != 0

        currentLevel = computedLevel
        isCharging = pluggedCharging

        // Significant change: charging state toggled, or percentage level changed
        val chargingChanged = lastSentCharging == null || lastSentCharging != pluggedCharging
        val levelChanged = lastSentLevel == -1 || Math.abs(computedLevel - lastSentLevel) >= 1

        if (chargingChanged || levelChanged) {
            sendUpdate(force = false)
        }
    }

    private fun sendUpdate(force: Boolean) {
        if (currentLevel < 0) return
        val now = System.currentTimeMillis()

        if (!force && lastSentCharging == isCharging && lastSentLevel == currentLevel) {
            return
        }

        lastSentLevel = currentLevel
        lastSentCharging = isCharging
        lastSentTimestamp = now

        Log.d(TAG, "Battery status update: level=$currentLevel, isCharging=$isCharging (forced=$force)")
        onBatteryChanged(currentLevel, isCharging)
    }

    companion object {
        private const val TAG = "AirLinkBattery"
        const val PERIODIC_INTERVAL_MS = 2 * 60 * 1000L // 2 minutes
    }
}
