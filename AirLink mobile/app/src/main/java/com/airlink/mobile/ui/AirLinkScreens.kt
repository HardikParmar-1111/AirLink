package com.airlink.mobile.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.airlink.mobile.network.DiscoveredDevice

// MARK: - Monochromatic Apple Color Palette
private val CanvasBg = Color(0xFFFBFBFD)
private val CardBg = Color(0xFFFFFFFF)
private val CardBorder = Color(0x0F000000)
private val TextPrimary = Color(0xFF1D1D1F)
private val TextSecondary = Color(0xFF86868B)
private val AccentBlue = Color(0xFF0071E3)
private val ActiveGreen = Color(0xFF34C759)
private val DangerRed = Color(0xFFFF3B30)
private val WarningAmber = Color(0xFFFF9500)

// =============================================================================
// SCREEN 1: Scanning Screen (Unpaired)
// =============================================================================

@Composable
fun ScanningScreen(
    discoveredDevices: List<DiscoveredDevice>,
    onPairClicked: (DiscoveredDevice) -> Unit,
    isPhoneRinging: Boolean = false,
    onStopRinging: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(CanvasBg)) {
        // High-Priority Find My Phone Overlay
        FindMyPhoneOverlay(
            isPhoneRinging = isPhoneRinging,
            onStopRinging = onStopRinging,
            modifier = Modifier.zIndex(100f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Title and Subtitle
            Text(
                text = "AirLink",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Seamless Android & macOS Continuity",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Pulsing Radar Animation
            RadarPulseAnimation(
                statusText = if (discoveredDevices.isEmpty()) "Searching for Macs..." else "Nearby Macs Detected"
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Available Devices Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AVAILABLE DEVICES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${discoveredDevices.size} found",
                    fontSize = 12.sp,
                    color = AccentBlue,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Devices List or Empty State (Strictly HIDE IP Address)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (discoveredDevices.isEmpty()) {
                    item {
                        EmptyScanningCard()
                    }
                } else {
                    items(discoveredDevices, key = { it.id }) { device ->
                        AvailableDeviceCard(
                            device = device,
                            onPair = { onPairClicked(device) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pulsing Radar Canvas with smooth expanding concentric waves.
 */
@Composable
private fun RadarPulseAnimation(
    statusText: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "RadarWave")

    val wave1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave1"
    )

    val wave2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave2"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2f

                listOf(wave1, wave2).forEach { progress ->
                    val radius = maxRadius * progress
                    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.45f
                    drawCircle(
                        color = AccentBlue.copy(alpha = alpha),
                        radius = radius,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            // Center Mac Glowing Orb
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        spotColor = AccentBlue.copy(alpha = 0.35f)
                    )
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                MacLaptopCanvasIcon(
                    modifier = Modifier.size(32.dp),
                    tint = AccentBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = statusText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

/**
 * Visually appealing Available Device Card with laptop icon, name, and pill-shaped Pair button.
 * Technical IP address is strictly hidden for Apple-like minimalism.
 */
@Composable
private fun AvailableDeviceCard(
    device: DiscoveredDevice,
    onPair: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPair)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                MacLaptopCanvasIcon(
                    modifier = Modifier.size(24.dp),
                    tint = AccentBlue
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Nearby Mac",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Pill-shaped Pair button
            Button(
                onClick = onPair,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "Pair",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmptyScanningCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Looking for your Mac...",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ensure AirLink is running in your Mac menu bar and both devices are connected to the same Wi-Fi network.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// =============================================================================
// SCREEN 2: Active Connection Screen (Paired Dashboard)
// =============================================================================

@Composable
fun ActiveConnectionScreen(
    macName: String,
    batteryLevel: Int,
    isCharging: Boolean,
    isMirroringActive: Boolean,
    clipboardSyncActive: Boolean = true,
    lastClipboardSyncTimestamp: Long = 0L,
    callHandlingPermissionsGranted: Boolean = false,
    isTouchInjectionEnabled: Boolean = true,
    isPhoneRinging: Boolean = false,
    onStartMirroring: () -> Unit,
    onStopMirroring: () -> Unit,
    onStopRinging: () -> Unit = {},
    onGrantCallPermissions: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(CanvasBg)) {
        // High-Priority Find My Phone Overlay (AnimatedVisibility)
        FindMyPhoneOverlay(
            isPhoneRinging = isPhoneRinging,
            onStopRinging = onStopRinging,
            modifier = Modifier.zIndex(100f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Top Section: Connected Mac Name & macOS Battery Indicator
            ConnectedHeader(
                macName = macName,
                batteryLevel = batteryLevel,
                isCharging = isCharging
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Middle Section: 2x2 Grid of Frosted-Glass Style Widgets
            Text(
                text = "ACTIVE CONTINUITY PIPELINES",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            val isRecentlySynced = (System.currentTimeMillis() - lastClipboardSyncTimestamp) < 2500L

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Widget 1: Universal Clipboard with Sync Pulse Animation
                    ClipboardWidgetCard(
                        isActive = clipboardSyncActive,
                        isRecentlySynced = isRecentlySynced,
                        modifier = Modifier.weight(1f)
                    )

                    // Widget 2: Notch Drop File Beaming
                    FeatureWidgetCard(
                        title = "Beaming & Files",
                        status = "Notch Drop Ready",
                        icon = Icons.Default.Refresh,
                        isActive = true,
                        accentColor = Color(0xFF5856D6),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Widget 3: Call Quick Reply (with Grant Permissions trigger if missing)
                    CallQuickReplyWidgetCard(
                        isPermissionGranted = callHandlingPermissionsGranted,
                        onGrantClicked = onGrantCallPermissions,
                        modifier = Modifier.weight(1f)
                    )

                    // Widget 4: Screen Mirroring & Touch Injection Status
                    ScreenMirrorWidgetCard(
                        isMirroringActive = isMirroringActive,
                        isTouchInjectionEnabled = isTouchInjectionEnabled,
                        onOpenSettings = onOpenAccessibilitySettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 3. Bottom Section: Dynamic Mirroring Action Button
            if (isMirroringActive) {
                Button(
                    onClick = onStopMirroring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stop Screen Mirroring",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Button(
                    onClick = onStartMirroring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Screen Mirroring",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Persistent Disconnect Button with explicit WindowInsets navigation bars padding
            TextButton(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Disconnect from $macName",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }

            // CRITICAL: WindowInsets navigationBars padding + 32dp clearance
            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

// =============================================================================
// CRITICAL FIX: Find My Phone High-Priority Animated Overlay
// =============================================================================

@Composable
fun FindMyPhoneOverlay(
    isPhoneRinging: Boolean,
    onStopRinging: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isPhoneRinging,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            initialOffsetY = { -it }
        ),
        exit = fadeOut(animationSpec = tween(250)) + slideOutVertically(
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            targetOffsetY = { -it }
        ),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "RingingPulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "AlarmScale"
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFFFF0F0),
            border = BorderStroke(1.5.dp, DangerRed.copy(alpha = 0.5f)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .scale(pulseScale)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DangerRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔔", fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Find My Phone Alarm Active",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DangerRed
                        )
                        Text(
                            text = "Ringing at maximum volume from Mac",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onStopRinging,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Stop Ringing",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =============================================================================
// SPECIALIZED WIDGET CARDS
// =============================================================================

@Composable
private fun ClipboardWidgetCard(
    isActive: Boolean,
    isRecentlySynced: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isRecentlySynced) ActiveGreen else if (isActive) AccentBlue.copy(alpha = 0.20f) else CardBorder,
        animationSpec = tween(400),
        label = "ClipboardBorder"
    )

    Surface(
        modifier = modifier.aspectRatio(1.22f),
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = if (isRecentlySynced) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background((if (isRecentlySynced) ActiveGreen else AccentBlue).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = if (isRecentlySynced) ActiveGreen else AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ActiveGreen, CircleShape)
                    )
                }
            }

            Column {
                Text(
                    text = "Universal Clipboard",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isRecentlySynced) "Synced Just Now!" else if (isActive) "Auto-Sync Ready" else "Disabled",
                    fontSize = 11.sp,
                    color = if (isRecentlySynced) ActiveGreen else if (isActive) AccentBlue else TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CallQuickReplyWidgetCard(
    isPermissionGranted: Boolean,
    onGrantClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.aspectRatio(1.22f),
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = BorderStroke(
            1.dp,
            if (isPermissionGranted) ActiveGreen.copy(alpha = 0.20f) else WarningAmber.copy(alpha = 0.35f)
        ),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background((if (isPermissionGranted) ActiveGreen else WarningAmber).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = if (isPermissionGranted) ActiveGreen else WarningAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isPermissionGranted) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ActiveGreen, CircleShape)
                    )
                }
            }

            Column {
                Text(
                    text = "Call Quick Reply",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isPermissionGranted) {
                    Text(
                        text = "Active: Decline & SMS",
                        fontSize = 11.sp,
                        color = ActiveGreen,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Grant Permissions",
                        fontSize = 11.sp,
                        color = WarningAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onGrantClicked)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenMirrorWidgetCard(
    isMirroringActive: Boolean,
    isTouchInjectionEnabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.aspectRatio(1.22f),
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = BorderStroke(
            1.dp,
            if (isMirroringActive) ActiveGreen.copy(alpha = 0.25f) else CardBorder
        ),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background((if (isMirroringActive) ActiveGreen else AccentBlue).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (isMirroringActive) ActiveGreen else AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isMirroringActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ActiveGreen, CircleShape)
                    )
                }
            }

            Column {
                Text(
                    text = "Screen Mirror",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isMirroringActive) {
                    Text(
                        text = "60 FPS Streaming",
                        fontSize = 11.sp,
                        color = ActiveGreen,
                        fontWeight = FontWeight.Medium
                    )
                } else if (!isTouchInjectionEnabled) {
                    Text(
                        text = "Touch: Enable in Settings",
                        fontSize = 10.sp,
                        color = WarningAmber,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(onClick = onOpenSettings)
                    )
                } else {
                    Text(
                        text = "Standby • Touch Ready",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Frosted-glass style 2x2 widget card with soft shadows.
 */
@Composable
private fun FeatureWidgetCard(
    title: String,
    status: String,
    icon: ImageVector,
    isActive: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.aspectRatio(1.22f),
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = BorderStroke(
            1.dp,
            if (isActive) accentColor.copy(alpha = 0.20f) else CardBorder
        ),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ActiveGreen, CircleShape)
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = status,
                    fontSize = 11.sp,
                    color = if (isActive) accentColor else TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Top dashboard header showing connected Mac name & macOS battery badge.
 */
@Composable
private fun ConnectedHeader(
    macName: String,
    batteryLevel: Int,
    isCharging: Boolean
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF2F2F7)),
                contentAlignment = Alignment.Center
            ) {
                MacLaptopCanvasIcon(
                    modifier = Modifier.size(28.dp),
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = macName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(ActiveGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Secure AES-256 Connected",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // macOS Battery Percentage Pill
            AppleBatteryBadge(
                level = batteryLevel,
                isCharging = isCharging
            )
        }
    }
}

/**
 * Pixel-perfect Apple MacBook laptop vector drawn on Canvas.
 */
@Composable
fun MacLaptopCanvasIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Top lid (Screen)
        val lidWidth = w * 0.78f
        val lidHeight = h * 0.58f
        val lidLeft = (w - lidWidth) / 2f
        val lidTop = h * 0.12f

        drawRoundRect(
            color = tint,
            topLeft = Offset(lidLeft, lidTop),
            size = Size(lidWidth, lidHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = 1.6.dp.toPx())
        )

        // Inner screen glass
        drawRoundRect(
            color = tint.copy(alpha = 0.12f),
            topLeft = Offset(lidLeft + 2.dp.toPx(), lidTop + 2.dp.toPx()),
            size = Size(lidWidth - 4.dp.toPx(), lidHeight - 4.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
            style = Fill
        )

        // Bottom base (Keyboard deck)
        val baseWidth = w * 0.94f
        val baseHeight = h * 0.12f
        val baseLeft = (w - baseWidth) / 2f
        val baseTop = lidTop + lidHeight

        drawRoundRect(
            color = tint,
            topLeft = Offset(baseLeft, baseTop),
            size = Size(baseWidth, baseHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Fill
        )

        // Thumb notch in bottom base
        val notchWidth = w * 0.16f
        val notchHeight = baseHeight * 0.45f
        val notchLeft = (w - notchWidth) / 2f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(notchLeft, baseTop),
            size = Size(notchWidth, notchHeight),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            style = Fill
        )
    }
}
