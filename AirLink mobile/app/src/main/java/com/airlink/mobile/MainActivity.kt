package com.airlink.mobile

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.airlink.mobile.accessibility.AirLinkAccessibilityService
import com.airlink.mobile.network.ConnectionState
import com.airlink.mobile.network.DiscoveredDevice
import com.airlink.mobile.notifications.AirLinkNotificationListener
import com.airlink.mobile.presentation.AirLinkViewModel
import com.airlink.mobile.ui.AppleBatteryBadge
import com.airlink.mobile.ui.theme.AirLinkTheme
import com.airlink.mobile.ui.theme.Connected
import com.airlink.mobile.ui.theme.Ink
import com.airlink.mobile.ui.theme.InkMuted
import com.airlink.mobile.ui.theme.Paper
import com.airlink.mobile.ui.theme.PaperElevated

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AirLinkViewModel
    private lateinit var clipboard: ClipboardManager

    /**
     * Variable for loop prevention: records the last text received from the Mac
     * so that Android doesn't echo it back over the WebSocket.
     */
    @Volatile
    var lastReceivedText: String? = null

    @Volatile
    private var lastSentText: String? = null

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            viewModel.startScreenCapture(result.resultCode, result.data!!)
        } else {
            Log.w(TAG, "Screen capture permission declined by user")
        }
    }

    private val callAndSmsPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.refreshPermissionsState()
        val phoneStateGranted = permissions[android.Manifest.permission.READ_PHONE_STATE] ?: false
        val smsGranted = permissions[android.Manifest.permission.SEND_SMS] ?: false
        Log.d(TAG, "Call & SMS permissions granted: phoneState=$phoneStateGranted, sms=$smsGranted")
    }

    private fun requestCallPermissions() {
        callAndSmsPermissionsLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.READ_CALL_LOG,
                android.Manifest.permission.ANSWER_PHONE_CALLS,
                android.Manifest.permission.SEND_SMS
            )
        )
    }

    /**
     * Must be an Activity field. ClipboardManager on several OEMs stores
     * OnPrimaryClipChangedListener with a WeakReference; a local/lambda
     * listener is collected and Android → Mac copy silently stops working.
     */
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = readPrimaryClipText()
        if (text.isNullOrEmpty()) {
            return@OnPrimaryClipChangedListener
        }

        if (viewModel.connectionState.value !is ConnectionState.Connected) {
            return@OnPrimaryClipChangedListener
        }

        // Loop Prevention: if text came from the Mac, ignore it
        if (text == lastReceivedText || text == viewModel.lastReceivedText) {
            Log.d(TAG, "Loop prevention: ignoring clipboard item that came from Mac")
            return@OnPrimaryClipChangedListener
        }

        // Prevent duplicate sends for the same local clip change
        if (text == lastSentText) {
            return@OnPrimaryClipChangedListener
        }
        lastSentText = text

        Log.d(TAG, "Captured copied text (${text.length} chars), sending to Mac")
        viewModel.sendClipboardImmediately(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[AirLinkViewModel::class.java]
        viewModel.onRemoteClipboardReceived = { text ->
            lastReceivedText = text
        }
        viewModel.onConnectionLostCallback = {
            if (::clipboard.isInitialized) {
                clipboard.removePrimaryClipChangedListener(clipboardListener)
            }
        }

        clipboard = getSystemService(ClipboardManager::class.java)
        callAndSmsPermissionsLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.READ_CALL_LOG,
                android.Manifest.permission.ANSWER_PHONE_CALLS,
                android.Manifest.permission.SEND_SMS
            )
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.connectionState.collect { state ->
                        if (state is ConnectionState.Connected) {
                            viewModel.retainedClipboardListener = clipboardListener
                            if (::clipboard.isInitialized) {
                                clipboard.removePrimaryClipChangedListener(clipboardListener)
                                clipboard.addPrimaryClipChangedListener(clipboardListener)
                            }
                        } else if (state is ConnectionState.Disconnected) {
                            if (::clipboard.isInitialized) {
                                clipboard.removePrimaryClipChangedListener(clipboardListener)
                            }
                        }
                    }
                }
                launch {
                    viewModel.requestScreenCapturePrompt.collect {
                        promptScreenCapture()
                    }
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            AirLinkTheme {
                AirLinkScreen(
                    viewModel = viewModel,
                    onRequestMirroring = ::promptScreenCapture,
                    onRequestCallPermissions = ::requestCallPermissions
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshPermissionsState()
        }
    }

    private fun promptScreenCapture() {
        val mpManager = getSystemService(MediaProjectionManager::class.java)
        try {
            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } catch (t: Throwable) {
            Log.e(TAG, "Error launching screen capture intent: ${t.message}", t)
        }
    }

    override fun onDestroy() {
        if (::clipboard.isInitialized) {
            clipboard.removePrimaryClipChangedListener(clipboardListener)
        }
        super.onDestroy()
    }

    private fun readPrimaryClipText(): String? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val item = clip.getItemAt(0)
        val text = item.text?.toString() ?: item.coerceToText(this)?.toString()
        return text?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val TAG = "AirLinkClipboard"
    }
}

@Composable
fun AirLinkScreen(
    viewModel: AirLinkViewModel,
    onRequestMirroring: () -> Unit,
    onRequestCallPermissions: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectedMacName by viewModel.connectedMacName.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val isMirroringActive by viewModel.isMirroringActive.collectAsStateWithLifecycle()
    val isPhoneRinging by viewModel.isPhoneRinging.collectAsStateWithLifecycle()
    val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()
    val isCharging by viewModel.isCharging.collectAsStateWithLifecycle()
    val clipboardSyncActive by viewModel.clipboardSyncActive.collectAsStateWithLifecycle()
    val lastClipboardSyncTimestamp by viewModel.lastClipboardSyncTimestamp.collectAsStateWithLifecycle()
    val callHandlingPermissionsGranted by viewModel.callHandlingPermissionsGranted.collectAsStateWithLifecycle()

    val isTouchInjectionEnabled = AirLinkAccessibilityService.isPermissionGranted(context)

    when (connectionState) {
        is ConnectionState.Connected -> {
            com.airlink.mobile.ui.ActiveConnectionScreen(
                macName = connectedMacName ?: "MacBook",
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                isMirroringActive = isMirroringActive,
                clipboardSyncActive = clipboardSyncActive,
                lastClipboardSyncTimestamp = lastClipboardSyncTimestamp,
                callHandlingPermissionsGranted = callHandlingPermissionsGranted,
                isTouchInjectionEnabled = isTouchInjectionEnabled,
                isPhoneRinging = isPhoneRinging,
                onStartMirroring = onRequestMirroring,
                onStopMirroring = viewModel::stopScreenCapture,
                onStopRinging = viewModel::stopRinging,
                onGrantCallPermissions = onRequestCallPermissions,
                onOpenAccessibilitySettings = { AirLinkAccessibilityService.openSettings(context) },
                onDisconnect = viewModel::onDisconnectClicked
            )
        }
        else -> {
            com.airlink.mobile.ui.ScanningScreen(
                discoveredDevices = discoveredDevices,
                onPairClicked = viewModel::connectToDevice,
                isPhoneRinging = isPhoneRinging,
                onStopRinging = viewModel::stopRinging
            )
        }
    }
}

@Composable
private fun DiscoveredDevicesList(
    devices: List<DiscoveredDevice>,
    onDeviceSelected: (DiscoveredDevice) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Available Macs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Searching",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(devices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    onClick = { onDeviceSelected(device) }
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DiscoveredDevice,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Connect to ${device.name}" },
        shape = RoundedCornerShape(18.dp),
        color = PaperElevated,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "💻", fontSize = 22.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tap to pair",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "Pair",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectingCard(
    macName: String,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Connecting to $macName" },
        shape = RoundedCornerShape(24.dp),
        color = PaperElevated,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.5.dp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Connecting to $macName...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sending pairing request...",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF3F4F6),
                    contentColor = Ink
                )
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ConnectedCard(
    macName: String,
    isStreaming: Boolean,
    isRinging: Boolean,
    batteryState: Pair<Int, Boolean>?,
    onRequestMirroring: () -> Unit,
    onStopMirroring: () -> Unit,
    onStopRinging: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Connected to $macName" },
        shape = RoundedCornerShape(24.dp),
        color = PaperElevated,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRinging) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFEE2E2),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔔 Find My Phone Ringing!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Playing loud alarm so you can locate your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onStopRinging,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                text = "Stop Ringing",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                PulsingGreenDot()
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Live Connection",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Connected
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connected to",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = macName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )

            if (batteryState != null) {
                val (level, isCharging) = batteryState
                Spacer(modifier = Modifier.height(8.dp))
                AppleBatteryBadge(
                    level = level,
                    isCharging = isCharging
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Clipboard, files & battery syncing.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Screen Mirroring Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (isStreaming) Color(0xFFF0FDF4) else Color(0xFFF9FAFB),
                border = BorderStroke(1.dp, if (isStreaming) Color(0xFFBBF7D0) else Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isStreaming) "📡" else "📺",
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isStreaming) "Screen Mirroring Active" else "Screen Mirroring Standby",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isStreaming) Color(0xFF15803D) else Ink
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isStreaming) "Streaming live over secure WebSocket" else "Ready to mirror screen to Mac",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isStreaming) {
                        OutlinedButton(
                            onClick = onStopMirroring,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Stop Screen Feed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Button(
                            onClick = onRequestMirroring,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "Start Screen Feed",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            val context = LocalContext.current
            val hasNotificationAccess = AirLinkNotificationListener.isPermissionGranted(context)
            if (!hasNotificationAccess) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = { AirLinkNotificationListener.openSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔔", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Mirroring",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1D4ED8)
                            )
                            Text(
                                text = "Tap to grant notification access",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
                    }
                }
            }

            val hasAccessibilityAccess = AirLinkAccessibilityService.isPermissionGranted(context)
            if (!hasAccessibilityAccess) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = { AirLinkAccessibilityService.openSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFDF4FF),
                    border = BorderStroke(1.dp, Color(0xFFF5D0FE))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("👆", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remote Touch Control",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF86198F)
                            )
                            Text(
                                text = "Enable Accessibility to control phone from Mac",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF86198F)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics { contentDescription = "Disconnect" },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = PaperElevated
                )
            ) {
                Text(
                    text = "Disconnect",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun RestingCard(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = title },
        shape = RoundedCornerShape(24.dp),
        color = PaperElevated,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.5.dp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PulsingGreenDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(Connected, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Connected, CircleShape)
        )
    }
}
