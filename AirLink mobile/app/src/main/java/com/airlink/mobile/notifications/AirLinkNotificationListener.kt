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
import java.util.concurrent.ConcurrentHashMap

/**
 * Intercepts incoming notifications, records interactive reply actions, and forwards
 * notifications over WebSocket to macOS with direct RemoteInput reply capabilities.
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

        // 1. Store the Reply Action (RemoteInput Hijacking)
        val sbnKey = sbn.key
        val actions = notification.actions
        if (actions != null && sbnKey != null) {
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    val resultKey = remoteInput.resultKey?.lowercase() ?: ""
                    val actionTitle = action.title?.toString()?.lowercase() ?: ""
                    if (resultKey.contains("reply") || actionTitle.contains("reply")) {
                        replyActionMap[sbnKey] = action
                        Log.d(TAG, "Saved reply action into replyActionMap for key='$sbnKey' (resultKey='${remoteInput.resultKey}')")
                        break
                    }
                }
            }
        }

        val extras = notification.extras ?: return

        // Extract title and text
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
            ?: ""

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            ?: ""

        // Skip empty notifications
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
        } catch (_: Exception) {
            packageName
        }

        val canReply = sbnKey != null && replyActionMap.containsKey(sbnKey)
        Log.d(TAG, "Notification received: app='$appName', title='$title', text='$text', key='$sbnKey', canReply=$canReply")
        listenerCallback?.invoke(appName, title, text, packageName, sbnKey ?: "", canReply)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val key = sbn?.key ?: return
        if (replyActionMap.remove(key) != null) {
            Log.d(TAG, "Removed reply action for key='$key' from replyActionMap to prevent memory leaks")
        }
    }

    /**
     * Executes direct RemoteInput reply using the captured Notification.Action
     */
    fun sendReply(notificationKey: String, message: String): Boolean {
        val action = replyActionMap[notificationKey]
        if (action == null) {
            Log.w(TAG, "sendReply failed: No saved Notification.Action found for key='$notificationKey'")
            return false
        }

        val remoteInputs = action.remoteInputs
        if (remoteInputs == null || remoteInputs.isEmpty()) {
            Log.w(TAG, "sendReply failed: Notification.Action has no RemoteInputs for key='$notificationKey'")
            return false
        }

        try {
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, message)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            action.actionIntent.send(this, 0, intent)
            Log.d(TAG, "sendReply: Successfully dispatched reply for key='$notificationKey' with message='$message'")
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "sendReply: Error executing reply action: ${t.message}", t)
            return false
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
            val action = replyActionMap[notificationKey]
            if (action == null) {
                Log.w(TAG, "sendReply (static): No saved Notification.Action for key='$notificationKey'")
                return false
            }

            val remoteInputs = action.remoteInputs
            if (remoteInputs == null || remoteInputs.isEmpty()) {
                Log.w(TAG, "sendReply (static): No RemoteInputs for key='$notificationKey'")
                return false
            }

            return try {
                val intent = Intent()
                val bundle = Bundle()
                for (remoteInput in remoteInputs) {
                    bundle.putCharSequence(remoteInput.resultKey, message)
                }
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                action.actionIntent.send(context, 0, intent)
                Log.d(TAG, "sendReply (static): Dispatched reply for key='$notificationKey' with message='$message'")
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
