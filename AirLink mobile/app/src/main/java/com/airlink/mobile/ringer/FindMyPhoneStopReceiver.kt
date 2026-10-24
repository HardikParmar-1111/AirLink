package com.airlink.mobile.ringer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class FindMyPhoneStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AirLinkRinger", "FindMyPhoneStopReceiver: Received stop action from notification")
        PhoneRingerManager.stopRinging(context)
    }
}
