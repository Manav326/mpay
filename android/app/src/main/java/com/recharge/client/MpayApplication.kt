package com.recharge.client

import android.app.Application
import com.recharge.client.features.voice.CallNotificationManager

class MpayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MpayFirebase.initialize(this)
        CallNotificationManager.ensureChannels(this)
    }
}
