package com.airlink.mobile.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class ClipboardPayload(
    @SerializedName("type") val type: String = TYPE_CLIPBOARD,
    @SerializedName("data") val data: String
) {
    companion object {
        const val TYPE_CLIPBOARD = "clipboard"
    }
}
