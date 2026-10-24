package com.airlink.mobile.ringer

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class FindMyPhoneActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setupLockScreenFlags()

        setContent {
            FindMyPhoneScreen(
                onStopClicked = {
                    PhoneRingerManager.stopRinging(this)
                    finish()
                }
            )
        }

        // Auto-dismiss activity as soon as ringing stops
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PhoneRingerManager.isRinging.collect { ringing ->
                    if (!ringing) {
                        finish()
                    }
                }
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Pressing hardware volume buttons silences Find My Phone
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            PhoneRingerManager.stopRinging(this)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun FindMyPhoneScreen(
    onStopClicked: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")

    // Ripple wave 1
    val wave1Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1Scale"
    )
    val wave1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1Alpha"
    )

    // Ripple wave 2 (staggered)
    val wave2Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2Scale"
    )
    val wave2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2Alpha"
    )

    // Button gentle heartbeat pulse
    val buttonScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buttonScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF08080C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header information
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0x22FF3B30),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "AIRLINK • FIND MY PHONE",
                        color = Color(0xFFFF453A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Phone is Ringing",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "A connected Mac is requesting to locate this device at maximum volume.",
                    color = Color(0xFF9E9EA7),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            // Massive pulsating button center
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(320.dp)
                    .semantics { contentDescription = "Pulsating alarm stop container" }
            ) {
                // Outer ripple wave 2
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(wave2Scale)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30).copy(alpha = wave2Alpha))
                )

                // Outer ripple wave 1
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(wave1Scale)
                        .clip(CircleShape)
                        .background(Color(0xFFFF453A).copy(alpha = wave1Alpha))
                )

                // Pulsating core button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(210.dp)
                        .scale(buttonScale)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFF453A),
                                    Color(0xFFD32F2F)
                                )
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Color.White),
                            role = Role.Button,
                            onClick = onStopClicked
                        )
                        .semantics { contentDescription = "Stop Ringing button" }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "STOP",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "RINGING",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }

            // Bottom tip
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Press volume buttons or tap above to silence alarm",
                    color = Color(0xFF6E6E77),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
