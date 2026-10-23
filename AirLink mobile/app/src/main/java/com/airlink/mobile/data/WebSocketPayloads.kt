package com.airlink.mobile.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class EncryptedPayload(
    @SerializedName("type") val type: String = "encrypted",
    @SerializedName("iv") val iv: String,
    @SerializedName("data") val data: String
)

@Keep
data class BatteryPayload(
    @SerializedName("type") val type: String = "battery",
    @SerializedName("level") val level: Int,
    @SerializedName("isCharging") val isCharging: Boolean
)

@Keep
data class NotificationPayload(
    @SerializedName("type") val type: String = "notification",
    @SerializedName("app") val app: String,
    @SerializedName("title") val title: String,
    @SerializedName("text") val text: String
)

@Keep
data class TouchPayload(
    @SerializedName("type") val type: String = "touch",
    @SerializedName("action") val action: String = "tap",
    @SerializedName("x") val x: Float,
    @SerializedName("y") val y: Float
)

@Keep
data class ScrollPayload(
    @SerializedName("type") val type: String = "scroll",
    @SerializedName("deltaX") val deltaX: Float,
    @SerializedName("deltaY") val deltaY: Float,
    @SerializedName("sensitivity") val sensitivity: Float = 2.5f
)

@Keep
data class SwipePayload(
    @SerializedName("type") val type: String = "swipe",
    @SerializedName("startX") val startX: Float,
    @SerializedName("startY") val startY: Float,
    @SerializedName("endX") val endX: Float,
    @SerializedName("endY") val endY: Float,
    @SerializedName("duration") val duration: Long = 200L
)

@Keep
data class PairingRequestPayload(
    @SerializedName("type") val type: String = "pairing_request",
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("level") val level: Int? = null,
    @SerializedName("isCharging") val isCharging: Boolean? = null
)

@Keep
data class SimpleActionPayload(
    @SerializedName("type") val type: String
)
