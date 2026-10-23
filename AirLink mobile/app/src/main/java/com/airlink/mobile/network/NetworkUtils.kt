package com.airlink.mobile.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    private const val TAG = "AirLinkNetworkUtils"

    /**
     * Resolves the device's local non-loopback IPv4 address on the Wi-Fi or active LAN network.
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Prioritize Wi-Fi and Ethernet interfaces
            val sortedInterfaces = interfaces.sortedByDescending { iface ->
                val name = iface.name.lowercase()
                when {
                    name.startsWith("wlan") -> 3
                    name.startsWith("en") -> 2
                    name.startsWith("eth") -> 2
                    else -> 1
                }
            }

            for (iface in sortedInterfaces) {
                if (!iface.isUp || iface.isLoopback) continue

                val addresses = Collections.list(iface.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host != "127.0.0.1" && !host.startsWith("169.254")) {
                            Log.d(TAG, "Found local IP on ${iface.name}: $host")
                            return host
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve network interfaces: ${t.message}")
        }

        // Fallback to WifiManager IP if available
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ip = String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                if (ip != "0.0.0.0" && ip != "127.0.0.1") {
                    Log.d(TAG, "Resolved IP from WifiManager: $ip")
                    return ip
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed WifiManager fallback: ${t.message}")
        }

        return null
    }

    fun getStreamUrl(context: Context, port: Int = 8081): String {
        val ip = getLocalIpAddress(context) ?: "localhost"
        return "http://$ip:$port/video"
    }
}
