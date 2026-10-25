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
 * Intercepts incoming notifications, records interactive reply actions with safe null-checking,
 * and broadcasts notifications to macOS via WebSocket with zero risk of crashing.
 */
class AirLinkNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
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
            val sbnKey = sbn.key ?: ""

            // 1. Safe Null-Checking for Quick Reply (RemoteInput Hijacking)
            try {
                val actions = notification.actions
                if (actions != null && sbnKey.isNotEmpty()) {
                    for (action in actions) {
                        if (action == null) continue
                        val remoteInputs = action.remoteInputs ?: continue
                        for (remoteInput in remoteInputs) {
                            if (remoteInput == null) continue
                            val resultKey = remoteInput.resultKey?.lowercase() ?: ""
                            val actionTitle = try {
                                action.title?.toString()?.lowercase() ?: ""
                            } catch (_: Throwable) {
                                ""
                            }
                            if (resultKey.contains("reply") || actionTitle.contains("reply")) {
                                replyActionMap[sbnKey] = action
                                Log.d(TAG, "Saved reply action for key='$sbnKey' (resultKey='${remoteInput.resultKey}')")
                                break
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                // If action inspection fails on any weird notification layout, log and continue gracefully
                Log.w(TAG, "Safe check: error inspecting actions for key='$sbnKey': ${t.message}")
            }

            // 2. Safely extract notification title and text
            val extras = try {
                notification.extras
            } catch (t: Throwable) {
                Log.w(TAG, "Error accessing notification extras: ${t.message}")
                null
            }

            var title = ""
            var text = ""

            if (extras != null) {
                try {
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                        ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
                        ?: ""

                    text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                        ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
                        ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
                        ?: ""
                } catch (t: Throwable) {
                    Log.w(TAG, "Error parsing CharSequences from extras: ${t.message}")
                }
            }

            // Fallback to tickerText if text is empty
            if (text.isEmpty()) {
                text = notification.tickerText?.toString()?.trim() ?: ""
            }

            // Skip completely empty notifications
            if (title.isEmpty() && text.isEmpty()) {
                return
            }

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
            } catch (_: Throwable) {
                packageName
            }

            val canReply = sbnKey.isNotEmpty() && replyActionMap.containsKey(sbnKey)
            Log.d(TAG, "Notification parsed: app='$appName', title='$title', text='$text', key='$sbnKey', canReply=$canReply")

            // 3. Forward to registered listener callback
            try {
                listenerCallback?.invoke(appName, title, text, packageName, sbnKey, canReply)
            } catch (t: Throwable) {
                Log.w(TAG, "Error invoking listenerCallback: ${t.message}")
            }

            // 4. Also broadcast directly via AirLinkWebSocketManager companion as resilient primary/backup route
            AirLinkWebSocketManager.broadcastNotification(
                appName = appName,
                title = title,
                text = text,
                packageName = packageName,
                key = sbnKey,
                canReply = canReply
            )
        } catch (t: Throwable) {
            // Absolute safety catch to guarantee NotificationListenerService NEVER crashes
            Log.e(TAG, "Safe check: caught unhandled error in onNotificationPosted (crash prevented): ${t.message}", t)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            super.onNotificationRemoved(sbn)
            val key = sbn?.key ?: return
            if (replyActionMap.remove(key) != null) {
                Log.d(TAG, "Removed reply action for key='$key' from replyActionMap")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error in onNotificationRemoved: ${t.message}")
        }
    }

    /**
     * Executes direct RemoteInput reply using the captured Notification.Action
     */
    fun sendReply(notificationKey: String, message: String): Boolean {
        return try {
            val action = replyActionMap[notificationKey]
            if (action == null) {
                Log.w(TAG, "sendReply: No saved Notification.Action found for key='$notificationKey'")
                return false
            }

            val remoteInputs = action.remoteInputs
            if (remoteInputs.isNullOrEmpty()) {
                Log.w(TAG, "sendReply: Notification.Action has no RemoteInputs for key='$notificationKey'")
                return false
            }

            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in remoteInputs) {
                if (remoteInput == null) continue
                val key = remoteInput.resultKey ?: continue
                bundle.putCharSequence(key, message)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

            val actionIntent = action.actionIntent
            if (actionIntent == null) {
                Log.w(TAG, "sendReply: actionIntent is null for key='$notificationKey'")
                return false
            }

            actionIntent.send(this, 0, intent)
            Log.d(TAG, "sendReply: Successfully dispatched reply for key='$notificationKey': '$message'")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "sendReply: Error executing reply action: ${t.message}", t)
            false
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
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "NotificationListener disconnected")
        super.onListenerDisconnected()
    }

    companion object {
        private const val TAG = "AirLinkNotification"
        private var _isConnected = false
        val isConnected: Boolean get() = _isConnected

        @Volatile
        private var instance: AirLinkNotificationListener? = null

        /**
         * Global dictionary mapping sbn.key to active Notification.Action for RemoteInput hijacking
         */
        val replyActionMap: MutableMap<String, Notification.Action> = ConcurrentHashMap()

        var listenerCallback: ((appName: String, title: String, text: String, packageName: String, key: String, canReply: Boolean) -> Unit)? = null

        /**
         * Static router to dispatch quick reply payload from WebSocket
         */
        fun sendReply(notificationKey: String, message: String, context: Context? = null): Boolean {
            val service = instance
            if (service != null) {
                return service.sendReply(notificationKey, message)
            }

            // Static fallback execution using context
            return try {
                val action = replyActionMap[notificationKey]
                if (action == null) {
                    Log.w(TAG, "sendReply (static): No saved Notification.Action for key='$notificationKey'")
                    return false
                }

                val remoteInputs = action.remoteInputs
                if (remoteInputs.isNullOrEmpty()) {
                    Log.w(TAG, "sendReply (static): No RemoteInputs for key='$notificationKey'")
                    return false
                }

                val intent = Intent()
                val bundle = Bundle()
                for (remoteInput in remoteInputs) {
                    if (remoteInput == null) continue
                    val key = remoteInput.resultKey ?: continue
                    bundle.putCharSequence(key, message)
                }
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

                val actionIntent = action.actionIntent
                if (actionIntent == null) {
                    Log.w(TAG, "sendReply (static): actionIntent is null for key='$notificationKey'")
                    return false
                }

                actionIntent.send(context, 0, intent)
                Log.d(TAG, "sendReply (static): Dispatched reply for key='$notificationKey': '$message'")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "sendReply (static): Error sending reply: ${t.message}", t)
                false
            }
        }

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
