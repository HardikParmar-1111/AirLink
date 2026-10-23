package com.airlink.mobile.network

sealed class ConnectionState(val label: String) {
    data object Disconnected : ConnectionState("Disconnected")
    data object Connecting : ConnectionState("Connecting")
    data object Connected : ConnectionState("Connected")
}
