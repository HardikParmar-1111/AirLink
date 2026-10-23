package com.airlink.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Apple Human Interface Guidelines System Colors
val AppleBatteryGreen = Color(0xFF34C759)
val AppleBatteryYellow = Color(0xFFFFCC00)
val AppleBatteryRed = Color(0xFFFF3B30)

/**
 * Pixel-perfect Apple battery icon drawn on Canvas:
 * - Rounded capsule body
 * - Positive terminal cap nub on the right
 * - Proportional inner fill bar reflecting battery level
 * - Apple system color coding (Green for charging, Yellow for <= 20%, Red for <= 10%)
 * - Centered Apple-style lightning bolt when charging
 */
@Composable
fun AppleBatteryIcon(
    level: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 26.dp,
    height: Dp = 13.dp,
    tint: Color = Color(0xFF1C1C1E)
) {
    val clampedLevel = level.coerceIn(0, 100)
    val fillColor = when {
        isCharging -> AppleBatteryGreen
        clampedLevel <= 10 -> AppleBatteryRed
        clampedLevel <= 20 -> AppleBatteryYellow
        else -> tint
    }

    Canvas(modifier = modifier.size(width = width, height = height)) {
        val strokeWidth = 1.35.dp.toPx()
        val capWidth = 1.65.dp.toPx()
        val capHeight = size.height * 0.38f
        val gap = 1.2.dp.toPx()

        // Outer body rect
        val bodyWidth = size.width - capWidth - gap
        val bodyHeight = size.height
        val cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())

        // 1. Outer rounded stroke outline
        drawRoundRect(
            color = tint.copy(alpha = 0.38f),
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(bodyWidth - strokeWidth, bodyHeight - strokeWidth),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )

        // 2. Terminal cap on the right
        val capTop = (size.height - capHeight) / 2f
        val capRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx())
        drawRoundRect(
            color = tint.copy(alpha = 0.38f),
            topLeft = Offset(bodyWidth + gap, capTop),
            size = Size(capWidth, capHeight),
            cornerRadius = capRadius,
            style = Fill
        )

        // 3. Inner fill level
        val innerPadding = 2.0.dp.toPx()
        val maxInnerWidth = bodyWidth - (innerPadding * 2f) - strokeWidth
        val innerHeight = bodyHeight - (innerPadding * 2f) - strokeWidth

        if (maxInnerWidth > 0 && innerHeight > 0) {
            val fillWidth = (maxInnerWidth * (clampedLevel / 100f)).coerceAtLeast(1.5.dp.toPx())
            val innerRadius = CornerRadius(2.2.dp.toPx(), 2.2.dp.toPx())
            drawRoundRect(
                color = fillColor,
                topLeft = Offset((strokeWidth / 2f) + innerPadding, (strokeWidth / 2f) + innerPadding),
                size = Size(fillWidth, innerHeight),
                cornerRadius = innerRadius,
                style = Fill
            )
        }

        // 4. Apple lightning bolt glyph when charging
        if (isCharging) {
            val boltPath = Path().apply {
                val cx = bodyWidth / 2f
                val cy = bodyHeight / 2f
                val bw = 3.4.dp.toPx()
                val bh = bodyHeight * 0.72f

                moveTo(cx + bw * 0.15f, cy - bh / 2f)
                lineTo(cx - bw * 0.85f, cy + bh * 0.05f)
                lineTo(cx - bw * 0.05f, cy + bh * 0.05f)
                lineTo(cx - bw * 0.25f, cy + bh / 2f)
                lineTo(cx + bw * 0.85f, cy - bh * 0.05f)
                lineTo(cx + bw * 0.05f, cy - bh * 0.05f)
                close()
            }
            drawPath(
                path = boltPath,
                color = Color.White,
                style = Fill
            )
        }
    }
}

/**
 * Capsule badge displaying the Apple battery icon and numeric percentage.
 */
@Composable
fun AppleBatteryBadge(
    level: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (isCharging) Color(0xFFE8F9EE) else Color(0xFFF2F2F7),
        border = BorderStroke(
            1.dp,
            if (isCharging) AppleBatteryGreen.copy(alpha = 0.35f) else Color(0xFFE5E5EA)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleBatteryIcon(
                level = level,
                isCharging = isCharging,
                tint = if (isCharging) Color(0xFF15803D) else Color(0xFF1C1C1E)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$level%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isCharging) Color(0xFF15803D) else Color(0xFF1C1C1E)
            )
            if (isCharging) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "• Charging",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF15803D)
                )
            }
        }
    }
}
