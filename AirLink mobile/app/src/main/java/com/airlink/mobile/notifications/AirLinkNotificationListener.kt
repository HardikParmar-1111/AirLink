package com.airlink.mobile.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.airlink.mobile.network.AirLinkWebSocketManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Intercepts incoming notifications and forwards them over WebSocket to the Mac.
 */
class AirLinkNotificationListener : NotificationListenerService() {

    private val replyActionMap = ConcurrentHashMap<String, Notification.Action>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return

        // Ignore notifications posted by AirLink itself (e.g. screen streaming foreground service)
        if (packageName == applicationContext.packageName) {
            return
        }

        // Ignore ongoing notifications like media players, foreground services, downloads
        if (sbn.isOngoing) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras

        // Extract title and text with safe null checks
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
            ?: ""

        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            ?: notification.tickerText?.toString()?.trim()
            ?: ""

        // Skip empty notifications
        if (title.isEmpty() && text.isEmpty()) {
            return
        }

        val sbnKey = sbn.key ?: ""

        // Resolve friendly application name
        val pm = applicationContext.packageManager
        val appName = try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, 0)
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }

        Log.d(TAG, "Notification received: app='$appName', title='$title', text='$text', key='$sbnKey'")

        // Broadcast JSON payload via WebSocket immediately BEFORE scanning for RemoteInput reply actions
        try {
            val payload = org.json.JSONObject().apply {
                put("action", "incoming_notification")
                put("type", "notification")
                put("package", packageName)
                put("title", title)
                put("text", text)
                put("key", sbnKey)
                put("app", appName)
            }.toString()

            val sent = AirLinkWebSocketManager.sendPayload(payload)
            Log.d(TAG, "Sent incoming_notification JSON to Mac (result=$sent)")

            listenerCallback?.invoke(appName, title, text, packageName, sbnKey)
            AirLinkWebSocketManager.broadcastNotification(appName, title, text, packageName, sbnKey)
        } catch (t: Throwable) {
            Log.e(TAG, "Error broadcasting notification to Mac: ${t.message}", t)
        }

        try {
            val actions = sbn.notification?.actions
            if (actions != null) {
                for (action in actions) {
                    if (action == null) continue
                    val remoteInputs = action.remoteInputs ?: continue
                    val hasReply = remoteInputs.any { remoteInput ->
                        remoteInput?.resultKey?.contains("reply", ignoreCase = true) == true
                    }
                    if (hasReply) {
                        replyActionMap[sbn.key] = action
                        Log.d(TAG, "Stored reply action for key=${sbn.key}")
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to store reply action: ${t.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        replyActionMap.remove(key)
    }

    fun executeReply(notificationKey: String, message: String) {
        try {
            val action = replyActionMap[notificationKey] ?: return
            val remoteInputs = action.remoteInputs ?: return
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in remoteInputs) {
                if (remoteInput?.resultKey != null) {
                    bundle.putCharSequence(remoteInput.resultKey, message)
                }
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            action.actionIntent?.send(this, 0, intent)
            Log.d(TAG, "Executed reply for key=$notificationKey")
        } catch (t: Throwable) {
            Log.e(TAG, "executeReply failed: ${t.message}", t)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isConnected = true
        Log.d(TAG, "NotificationListener connected")
    }

    override fun onListenerDisconnected() {
        _isConnected = false
        Log.d(TAG, "NotificationListener disconnected")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        instance = null
        replyActionMap.clear()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AirLinkNotification"
        private var _isConnected = false
        val isConnected: Boolean get() = _isConnected

        @Volatile
        private var instance: AirLinkNotificationListener? = null

        fun executeReply(notificationKey: String, message: String) {
            instance?.executeReply(notificationKey, message)
        }

        var listenerCallback: ((appName: String, title: String, text: String, packageName: String, key: String) -> Unit)? = null

        fun isPermissionGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
