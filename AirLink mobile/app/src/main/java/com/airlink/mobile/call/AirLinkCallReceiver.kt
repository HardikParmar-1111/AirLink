package com.airlink.mobile.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * BroadcastReceiver for TelephonyManager.ACTION_PHONE_STATE.
 * Registered in AndroidManifest.xml to ensure incoming calls wake the app
 * and notify the macOS companion even when the app is in the background or device is locked.
 */
class AirLinkCallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AirLink"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != TelephonyManager.ACTION_PHONE_STATE_CHANGED && action != "android.intent.action.PHONE_STATE") return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d(TAG, "AirLinkCallReceiver: ACTION_PHONE_STATE received state=$stateStr")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    ?.takeIf { it.isNotBlank() } ?: "Unknown Caller"

                // Strict logging as required
                Log.d("AirLink", "Call ringing detected: $number")

                // Hold temporary partial wake lock to prevent Doze mode from dropping socket
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AirLink:IncomingCallWakeLock"
                )
                wakeLock?.acquire(15_000L) // 15 seconds

                AirLinkCallManager.sharedInstance?.onIncomingCallRinging(number)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "AirLinkCallReceiver: Call is IDLE")
                AirLinkCallManager.sharedInstance?.onCallIdle()
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "AirLinkCallReceiver: Call is OFFHOOK (answered)")
                AirLinkCallManager.sharedInstance?.onCallAnswered()
            }
        }
    }
}
