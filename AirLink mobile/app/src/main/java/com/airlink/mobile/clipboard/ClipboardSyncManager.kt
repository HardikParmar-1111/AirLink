package com.airlink.mobile.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Writes remote clipboard payloads onto the system clipboard.
 * Local copy detection lives on [com.airlink.mobile.MainActivity] so the
 * [ClipboardManager.OnPrimaryClipChangedListener] is a long-lived Activity field
 * (some OEMs keep that listener list weakly).
 */
class ClipboardSyncManager(context: Context) {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var lastReceivedText: String? = null

    @Volatile
    var lastSyncedText: String? = null
        private set

    @Volatile
    private var ignoreLocalChangeUntil: Long = 0L

    fun shouldIgnoreLocalChange(text: String): Boolean {
        if (System.currentTimeMillis() < ignoreLocalChangeUntil) return true
        if (text == lastReceivedText) return true
        return text == lastSyncedText
    }

    fun markLocalTextSent(text: String) {
        lastSyncedText = text
    }

    fun writeRemoteText(text: String) {
        runOnMain {
            lastReceivedText = text
            if (text == lastSyncedText) return@runOnMain
            lastSyncedText = text
            ignoreLocalChangeUntil = System.currentTimeMillis() + ECHO_SUPPRESS_MS
            clipboard.setPrimaryClip(ClipData.newPlainText("AirLink", text))
            Log.d(TAG, "Wrote remote clipboard (${text.length} chars)")
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        private const val TAG = "AirLinkClipboard"
        private const val ECHO_SUPPRESS_MS = 400L
    }
}
