package com.airlink.mobile.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.airlink.mobile.network.AirLinkWebSocketManager
import org.json.JSONObject

/**
 * Manages telephony call detection, background wake lock retention,
 * Mac WebSocket synchronization, and remote call decline with instant automated SMS reply.
 */
class AirLinkCallManager(
    private val context: Context,
    private val webSocketManager: AirLinkWebSocketManager
) {
    companion object {
        private const val TAG = "AirLink"

        @Volatile
        var sharedInstance: AirLinkCallManager? = null
            private set
    }

    init {
        sharedInstance = this
    }

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    // Holds caller ID of the currently ringing phone call
    @Volatile
    var activeIncomingNumber: String? = null
        private set

    private var isListening = false

    /**
     * Start listening for incoming call ringing states via modern TelephonyCallback/Listener.
     */
    fun startListening() {
        if (isListening) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot listen for calls: READ_PHONE_STATE permission not granted")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state, null)
                    }
                }
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
                isListening = true
                Log.d(TAG, "Registered TelephonyCallback for call state monitoring")
            } else {
                @Suppress("DEPRECATION")
                val listener = object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state, phoneNumber)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
                isListening = true
                Log.d(TAG, "Registered PhoneStateListener for call state monitoring")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register call state listener: ${t.message}", t)
        }
    }

    private fun handleCallState(state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                val number = phoneNumber?.takeIf { it.isNotBlank() } ?: activeIncomingNumber ?: "Unknown Caller"
                onIncomingCallRinging(number)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                onCallIdle()
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                onCallAnswered()
            }
        }
    }

    /**
     * Triggered immediately when ringing is detected by either TelephonyCallback or AirLinkCallReceiver.
     */
    fun onIncomingCallRinging(number: String) {
        activeIncomingNumber = number

        // 1. Strict logging requirement
        Log.d("AirLink", "Call ringing detected: $number")

        // 2. Prevent Doze mode from putting network/CPU to sleep during call alert
        var wakeLock: PowerManager.WakeLock? = null
        try {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AirLink:SendIncomingCallWakeLock"
            )
            wakeLock?.acquire(10_000L) // 10 seconds timeout
        } catch (t: Throwable) {
            Log.w(TAG, "Could not acquire WakeLock: ${t.message}")
        }

        // 3. Strict logging requirement right before sending JSON
        Log.d("AirLink", "Sending incoming_call JSON to Mac")

        val payload = JSONObject().apply {
            put("type", "incoming_call")
            put("number", number)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        val sent = webSocketManager.send(payload)
        Log.d(TAG, "WebSocket payload delivery result for incoming_call: $sent")

        if (!sent) {
            Log.w(TAG, "WebSocket send failed! Connection may be disconnected or buffering.")
        }
    }

    fun onCallIdle() {
        Log.d(TAG, "Call ended or dismissed (CALL_STATE_IDLE)")
        activeIncomingNumber = null
    }

    fun onCallAnswered() {
        Log.d(TAG, "Call answered (CALL_STATE_OFFHOOK)")
    }

    /**
     * Step 3: Silently decline the active call and send the automated SMS reply.
     */
    fun declineAndSendSMS(customMessage: String) {
        val targetNumber = activeIncomingNumber
        Log.d(TAG, "Executing Decline & Text command for target: $targetNumber")

        // 1. Silently reject/end the incoming call via TelecomManager
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val ended = telecomManager.endCall()
                    Log.d(TAG, "TelecomManager.endCall() returned: $ended")
                } else {
                    Log.w(TAG, "TelecomManager.endCall requires API 28+")
                }
            } else {
                Log.w(TAG, "ANSWER_PHONE_CALLS permission missing; cannot reject call")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error ending call via TelecomManager: ${t.message}", t)
        }

        // 2. Dispatch automated SMS in the background
        if (!targetNumber.isNullOrBlank() && targetNumber != "Unknown Caller") {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }

                    smsManager.sendTextMessage(targetNumber, null, customMessage, null, null)
                    Log.d(TAG, "Background SMS sent to $targetNumber: '$customMessage'")
                } else {
                    Log.w(TAG, "SEND_SMS permission missing; cannot send automated SMS")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to send SMS to $targetNumber: ${t.message}", t)
            }
        }

        activeIncomingNumber = null
    }
}
