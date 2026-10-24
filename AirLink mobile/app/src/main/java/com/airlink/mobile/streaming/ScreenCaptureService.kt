package com.airlink.mobile.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.airlink.mobile.MainActivity
import com.airlink.mobile.network.AirLinkWebSocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var lastFrameTimestamp: Long = 0L
    private val isProcessingFrame = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("AirLinkScreenCaptureThread")
        thread.start()
        handlerThread = thread
        backgroundHandler = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stop requested for ScreenCaptureService")
                stopStreaming()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == 0 || resultData == null) {
                    Log.w(TAG, "ScreenCaptureService started without valid projection intent")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // If already streaming or previous session display/reader is retained, cleanly release first
                if (virtualDisplay != null || mediaProjection != null || imageReader != null) {
                    Log.d(TAG, "Cleaning up previous display and reader before starting fresh projection")
                    cleanupDisplayAndReader()
                }

                startForegroundServiceWithNotification()
                startProjection(resultCode, resultData)
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirLink Screen Mirroring")
            .setContentText("Streaming screen to your Mac via WebSocket")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Mirroring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notification while screen mirroring feed is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Log.e(TAG, "Failed to obtain MediaProjection instance")
            stopSelf()
            return
        }
        mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped by system")
                stopStreaming()
                stopSelf()
            }
        }, backgroundHandler)

        // Calculate scaled resolution (0.5x scale, capped at max 720p width)
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val screenW = metrics.widthPixels.coerceAtLeast(1)
        val screenH = metrics.heightPixels.coerceAtLeast(1)

        // Scale by 0.5 to drastically reduce pixel count (e.g. 1080p -> 540p = 4x fewer pixels)
        val scale = 0.5f
        var targetW = ((screenW * scale).toInt() / 2) * 2
        var targetH = ((screenH * scale).toInt() / 2) * 2
        if (targetW > 720) {
            targetW = 720
            targetH = ((screenH * targetW / screenW) / 2) * 2
        }
        targetW = targetW.coerceAtLeast(360)
        targetH = targetH.coerceAtLeast(640)

        // Scale density proportionally to preserve crisp UI sizing
        val density = ((metrics.densityDpi * (targetW.toFloat() / screenW)).toInt()).coerceAtLeast(120)

        Log.d(TAG, "Configuring low-latency screen capture at ${targetW}x${targetH} (native ${screenW}x${screenH}, density $density)")

        val reader = ImageReader.newInstance(targetW, targetH, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        reader.setOnImageAvailableListener({ ir ->
            val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener

            // Drop stale frames: if WebSocket is not connected, drop immediately to conserve CPU
            if (!AirLinkWebSocketManager.isConnected) {
                image.close()
                return@setOnImageAvailableListener
            }

            // Drop frame if previous frame is still encoding to prevent pipeline backlog
            if (!isProcessingFrame.compareAndSet(false, true)) {
                image.close()
                return@setOnImageAvailableListener
            }

            val now = SystemClock.elapsedRealtime()
            // Throttle to ~35-40 FPS (~25ms interval) to maintain silky responsiveness without congestion
            if (now - lastFrameTimestamp < 25) {
                image.close()
                isProcessingFrame.set(false)
                return@setOnImageAvailableListener
            }
            lastFrameTimestamp = now

            try {
                val planes = image.planes
                if (planes.isNotEmpty()) {
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * targetW

                    val rawBitmap = Bitmap.createBitmap(
                        targetW + rowPadding / pixelStride,
                        targetH,
                        Bitmap.Config.ARGB_8888
                    )
                    rawBitmap.copyPixelsFromBuffer(buffer)

                    val cleanBitmap = if (rowPadding > 0) {
                        Bitmap.createBitmap(rawBitmap, 0, 0, targetW, targetH)
                    } else {
                        rawBitmap
                    }

                    // Aggressive compression: JPEG quality 55 for ultra-fast compression and minimal latency
                    val baos = ByteArrayOutputStream(32 * 1024)
                    cleanBitmap.compress(Bitmap.CompressFormat.JPEG, 55, baos)
                    val jpegBytes = baos.toByteArray()

                    // Send directly as binary byte array over active OkHttp WebSocket connection
                    AirLinkWebSocketManager.sendBinaryFrame(jpegBytes)

                    if (cleanBitmap !== rawBitmap) {
                        cleanBitmap.recycle()
                    }
                    rawBitmap.recycle()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error encoding frame: ${t.message}")
            } finally {
                image.close()
                isProcessingFrame.set(false)
            }
        }, backgroundHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "AirLinkScreenStream",
            targetW,
            targetH,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            backgroundHandler
        )

        _isStreamingFlow.value = true
        Log.d(TAG, "Screen capture started via WebSocket binary streaming")
    }

    private fun cleanupDisplayAndReader() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing virtual display: ${e.message}")
        }
        virtualDisplay = null

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing image reader: ${e.message}")
        }
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media projection: ${e.message}")
        }
        mediaProjection = null

        isProcessingFrame.set(false)
        lastFrameTimestamp = 0L
    }

    private fun stopStreaming() {
        val wasStreaming = _isStreamingFlow.value || virtualDisplay != null || mediaProjection != null
        if (wasStreaming && AirLinkWebSocketManager.isConnected) {
            // 1. Send Stop Signal: notify Mac before tearing down capture session
            AirLinkWebSocketManager.sendStopMirroring()
        }

        _isStreamingFlow.value = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        cleanupDisplayAndReader()
    }

    override fun onDestroy() {
        stopStreaming()
        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null
        super.onDestroy()
        Log.d(TAG, "ScreenCaptureService destroyed")
    }

    companion object {
        const val ACTION_START = "com.airlink.mobile.ACTION_START_STREAM"
        const val ACTION_STOP = "com.airlink.mobile.ACTION_STOP_STREAM"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val CHANNEL_ID = "airlink_screen_mirror_channel"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "AirLinkScreenCapture"

        private val _isStreamingFlow = MutableStateFlow(false)
        val isStreamingFlow: StateFlow<Boolean> = _isStreamingFlow.asStateFlow()

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
