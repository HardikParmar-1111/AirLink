package com.airlink.mobile.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log
import com.airlink.mobile.network.AirLinkWebSocketManager

/**
 * BroadcastReceiver for TelephonyManager.ACTION_PHONE_STATE.
 * Registered in AndroidManifest.xml to ensure incoming calls wake the app
 * and notify the macOS companion even when the app is in the background or device is locked.
 *
 * Implements strict debouncing and delays "Unknown Caller" broadcasts so that rapid subsequent
 * broadcasts containing the resolved phone number take precedence, preventing duplicate WebSocket pushes.
 */
class AirLinkCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AirLink"
        private const val UNKNOWN_CALLER_DELAY_MS = 300L
        private const val RINGING_DEBOUNCE_WINDOW_MS = 4000L

        // Required companion variables for debouncing
        @Volatile
        var lastCallState: String? = null
        @Volatile
        var lastCallTime: Long = 0L

        @Volatile
        private var hasBroadcastForCurrentRing: Boolean = false
        @Volatile
        private var lastRingingNumber: String? = null

        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingUnknownCallerRunnable: Runnable? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != TelephonyManager.ACTION_PHONE_STATE_CHANGED && action != "android.intent.action.PHONE_STATE") return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d(TAG, "AirLinkCallReceiver: ACTION_PHONE_STATE received state=$stateStr")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val rawNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                val hasRealNumber = !rawNumber.isNullOrBlank()
                val number = if (hasRealNumber) rawNumber!!.trim() else "Unknown Caller"
                val now = System.currentTimeMillis()

                Log.d("AirLink", "Call ringing detected in receiver: $number (hasRealNumber=$hasRealNumber)")

                // Hold temporary partial wake lock to prevent Doze mode from dropping socket
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AirLink:IncomingCallWakeLock"
                )
                wakeLock?.acquire(15_000L) // 15 seconds

                if (hasRealNumber) {
                    // We have the actual phone number!
                    // 1. Cancel any pending "Unknown Caller" delayed broadcast
                    pendingUnknownCallerRunnable?.let {
                        mainHandler.removeCallbacks(it)
                        Log.d(TAG, "AirLinkCallReceiver: Cancelled pending Unknown Caller broadcast because real number '$number' arrived")
                    }
                    pendingUnknownCallerRunnable = null

                    // 2. Debounce duplicate RINGING broadcasts for this same call event
                    if (lastCallState == TelephonyManager.EXTRA_STATE_RINGING &&
                        hasBroadcastForCurrentRing &&
                        lastRingingNumber == number &&
                        (now - lastCallTime) < RINGING_DEBOUNCE_WINDOW_MS) {
                        Log.d(TAG, "AirLinkCallReceiver: Debouncing duplicate ringing broadcast for '$number' (${now - lastCallTime}ms since last)")
                        return
                    }

                    // 3. Mark state and dispatch single broadcast
                    lastCallState = TelephonyManager.EXTRA_STATE_RINGING
                    lastCallTime = now
                    lastRingingNumber = number
                    hasBroadcastForCurrentRing = true

                    dispatchIncomingCall(context, number)
                } else {
                    // Android fired with "Unknown Caller" (often followed ~20ms later by the real number).
                    // If we already broadcast for this ringing event, ignore it.
                    if (hasBroadcastForCurrentRing && (now - lastCallTime) < RINGING_DEBOUNCE_WINDOW_MS) {
                        Log.d(TAG, "AirLinkCallReceiver: Ignoring duplicate Unknown Caller broadcast for active ringing event")
                        return
                    }

                    // Cancel any previous pending runnable before setting a new one
                    pendingUnknownCallerRunnable?.let { mainHandler.removeCallbacks(it) }

                    val runnable = Runnable {
                        pendingUnknownCallerRunnable = null
                        if (!hasBroadcastForCurrentRing) {
                            Log.d(TAG, "AirLinkCallReceiver: No real number received within ${UNKNOWN_CALLER_DELAY_MS}ms, dispatching 'Unknown Caller'")
                            lastCallState = TelephonyManager.EXTRA_STATE_RINGING
                            lastCallTime = System.currentTimeMillis()
                            lastRingingNumber = "Unknown Caller"
                            hasBroadcastForCurrentRing = true
                            dispatchIncomingCall(context, "Unknown Caller")
                        }
                    }
                    pendingUnknownCallerRunnable = runnable
                    mainHandler.postDelayed(runnable, UNKNOWN_CALLER_DELAY_MS)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "AirLinkCallReceiver: Call is IDLE — resetting debounce state")
                pendingUnknownCallerRunnable?.let { mainHandler.removeCallbacks(it) }
                pendingUnknownCallerRunnable = null
                lastCallState = TelephonyManager.EXTRA_STATE_IDLE
                lastCallTime = System.currentTimeMillis()
                lastRingingNumber = null
                hasBroadcastForCurrentRing = false

                AirLinkCallManager.sharedInstance?.onCallIdle()
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "AirLinkCallReceiver: Call is OFFHOOK (answered)")
                pendingUnknownCallerRunnable?.let { mainHandler.removeCallbacks(it) }
                pendingUnknownCallerRunnable = null
                lastCallState = TelephonyManager.EXTRA_STATE_OFFHOOK
                lastCallTime = System.currentTimeMillis()

                AirLinkCallManager.sharedInstance?.onCallAnswered()
            }
        }
    }

    private fun dispatchIncomingCall(context: Context, number: String) {
        Log.d("AirLink", "Call ringing detected: $number")
        if (AirLinkCallManager.sharedInstance != null) {
            AirLinkCallManager.sharedInstance?.onIncomingCallRinging(number)
        } else {
            Log.d("AirLink", "Sending incoming_call JSON to Mac (via broadcast fallback)")
            val payload = org.json.JSONObject().apply {
                put("type", "incoming_call")
                put("number", number)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            AirLinkWebSocketManager.sendPayload(payload)
        }
    }
}
