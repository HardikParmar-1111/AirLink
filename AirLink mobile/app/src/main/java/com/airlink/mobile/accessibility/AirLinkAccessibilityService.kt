package com.airlink.mobile.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService that enables remote touch control from macOS AirLink
 * by injecting gestures via [dispatchGesture].
 */
class AirLinkAccessibilityService : AccessibilityService() {

    private var touchStartPoint: PointF? = null
    private var touchStartTime: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AirLinkAccessibilityService connected and ready for touch injection")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event interception required for remote injection
    }

    override fun onInterrupt() {
        Log.d(TAG, "AirLinkAccessibilityService interrupted")
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
        Log.d(TAG, "AirLinkAccessibilityService destroyed")
    }

    /**
     * Injects a tap gesture at normalized coordinates [normX], [normY] (0.0 to 1.0).
     */
    fun tap(normX: Float, normY: Float): Boolean {
        val (pixelX, pixelY) = toScreenPixels(normX, normY)
        Log.d(TAG, "Injecting tap at normalized ($normX, $normY) -> pixels ($pixelX, $pixelY)")

        val path = Path().apply {
            moveTo(pixelX, pixelY)
        }

        // 50ms duration ensures Android touch system recognizes the click without cancellation
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap gesture successfully dispatched at ($pixelX, $pixelY)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap gesture was cancelled by system at ($pixelX, $pixelY)")
            }
        }, null)
    }

    /**
     * Injects a continuous touch action ("down", "move", "up") or tap.
     */
    fun touch(action: String, normX: Float, normY: Float): Boolean {
        val (pixelX, pixelY) = toScreenPixels(normX, normY)

        when (action.lowercase()) {
            "down" -> {
                touchStartPoint = PointF(pixelX, pixelY)
                touchStartTime = System.currentTimeMillis()
                return true
            }
            "move" -> {
                // Tracking move coordinates for swipe/drag gesture completion on "up"
                return true
            }
            "up" -> {
                val start = touchStartPoint
                val startTime = touchStartTime
                touchStartPoint = null
                touchStartTime = 0L

                if (start == null) {
                    return tap(normX, normY)
                }

                val dx = pixelX - start.x
                val dy = pixelY - start.y
                val distanceSquared = (dx * dx) + (dy * dy)

                // If movement was small (< 20px), treat as a standard tap
                if (distanceSquared < 400) {
                    return tap(normX, normY)
                }

                // Treat as swipe/drag gesture
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    lineTo(pixelX, pixelY)
                }

                val duration = (System.currentTimeMillis() - startTime).coerceIn(40L, 250L)
                val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()

                Log.d(TAG, "Injecting drag gesture from (${start.x}, ${start.y}) to ($pixelX, $pixelY) duration: ${duration}ms")
                return dispatchGesture(gesture, null, null)
            }
            else -> {
                return tap(normX, normY)
            }
        }
    }

    /**
     * Injects a swipe/scroll gesture from normalized ([startX], [startY]) to ([endX], [endY])
     * converting to physical screen pixels and dispatching via [dispatchGesture].
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 200L): Boolean {
        val (startPixelX, startPixelY) = toScreenPixels(startX, startY)
        val (endPixelX, endPixelY) = toScreenPixels(endX, endY)
        Log.d(TAG, "Injecting swipe from ($startX, $startY) -> ($startPixelX, $startPixelY) to ($endX, $endY) -> ($endPixelX, $endPixelY), duration: ${durationMs}ms")

        val path = Path().apply {
            moveTo(startPixelX, startPixelY)
            lineTo(endPixelX, endPixelY)
        }

        val clampedDuration = durationMs.coerceIn(50L, 1000L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, clampedDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe gesture completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe gesture cancelled")
            }
        }, null)
    }

    /**
     * Translates continuous Mac trackpad scroll deltas into fluid Android scrolling.
     * Starts from the center of the screen, scales deltas by sensitivity, enforces minimum
     * travel distance to exceed touch slop, and dispatches an 80ms stroke via [dispatchGesture]
     * so that Android strictly registers a swipe/scroll instead of a tap.
     */
    fun scroll(deltaX: Float, deltaY: Float, sensitivity: Float = 2.5f): Boolean {
        if (deltaX == 0f && deltaY == 0f) return false

        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics) ?: run {
            val resMetrics = resources.displayMetrics
            metrics.widthPixels = resMetrics.widthPixels
            metrics.heightPixels = resMetrics.heightPixels
        }

        val screenW = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = metrics.heightPixels.toFloat().coerceAtLeast(1f)

        // Center-Point Scrolling: start from dead center of the screen
        val startX = screenW / 2f
        val startY = screenH / 2f

        // Scale distance: multiply by sensitivity factor (default 2.5f)
        var scaledDx = deltaX * sensitivity
        var scaledDy = deltaY * sensitivity

        val distance = Math.hypot(scaledDx.toDouble(), scaledDy.toDouble()).toFloat()
        if (distance < 0.5f) return false

        // Ensure the gesture covers enough physical distance on screen to exceed touch slop
        val minScrollDistance = 35f
        if (distance < minScrollDistance) {
            val boost = minScrollDistance / distance
            scaledDx *= boost
            scaledDy *= boost
        }

        // Apply Deltas: subtract from center point (inverting macOS scroll to match Android drag)
        val endX = (startX - scaledDx).coerceIn(10f, screenW - 10f)
        val endY = (startY - scaledDy).coerceIn(10f, screenH - 10f)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        // Increased duration (80ms) ensures the gesture registers strictly as a scroll/swipe rather than a tap
        val stroke = GestureDescription.StrokeDescription(path, 0L, 80L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, null, null)
    }

    /**
     * Triggers standard Android global navigation actions (Back, Home, Recents).
     */
    fun triggerGlobal(action: String): Boolean {
        val globalCode = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "app_switch" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        val result = performGlobalAction(globalCode)
        Log.d(TAG, "Performed global action '$action': $result")
        return result
    }

    private fun toScreenPixels(normX: Float, normY: Float): Pair<Float, Float> {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics) ?: run {
            val resMetrics = resources.displayMetrics
            metrics.widthPixels = resMetrics.widthPixels
            metrics.heightPixels = resMetrics.heightPixels
        }

        val screenW = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = metrics.heightPixels.toFloat().coerceAtLeast(1f)

        val clampedX = normX.coerceIn(0f, 1f) * screenW
        val clampedY = normY.coerceIn(0f, 1f) * screenH

        return clampedX to clampedY
    }

    companion object {
        private const val TAG = "AirLinkAccessibility"

        @Volatile
        private var instance: AirLinkAccessibilityService? = null

        val isServiceRunning: Boolean get() = instance != null

        fun injectTap(normX: Float, normY: Float): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Cannot inject tap: AirLinkAccessibilityService is not running / enabled")
                return false
            }
            return service.tap(normX, normY)
        }

        fun injectTouch(action: String, normX: Float, normY: Float): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Cannot inject touch: AirLinkAccessibilityService is not running / enabled")
                return false
            }
            return service.touch(action, normX, normY)
        }

        fun injectSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 200L): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Cannot inject swipe: AirLinkAccessibilityService is not running / enabled")
                return false
            }
            return service.swipe(startX, startY, endX, endY, durationMs)
        }

        fun injectScroll(deltaX: Float, deltaY: Float, sensitivity: Float = 2.5f): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Cannot inject scroll: AirLinkAccessibilityService is not running / enabled")
                return false
            }
            return service.scroll(deltaX, deltaY, sensitivity)
        }

        fun performGlobalAction(action: String): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Cannot trigger global action: AirLinkAccessibilityService is not enabled")
                return false
            }
            return service.triggerGlobal(action)
        }

        fun isPermissionGranted(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, AirLinkAccessibilityService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(expectedComponentName)
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
