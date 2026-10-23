package com.airlink.mobile.notifications

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Intercepts incoming notifications and forwards them over WebSocket to the Mac.
 */
class AirLinkNotificationListener : NotificationListenerService() {

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

        Log.d(TAG, "Notification received: app='$appName', title='$title', text='$text'")
        listenerCallback?.invoke(appName, title, text, packageName)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected = true
        Log.d(TAG, "NotificationListener connected")
    }

    override fun onListenerDisconnected() {
        _isConnected = false
        Log.d(TAG, "NotificationListener disconnected")
        super.onListenerDisconnected()
    }

    companion object {
        private const val TAG = "AirLinkNotification"
        private var _isConnected = false
        val isConnected: Boolean get() = _isConnected

        var listenerCallback: ((appName: String, title: String, text: String, packageName: String) -> Unit)? = null

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
