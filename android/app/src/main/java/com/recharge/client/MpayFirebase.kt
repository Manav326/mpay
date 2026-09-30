package com.recharge.client

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object MpayFirebase {
    fun isConfigured(): Boolean =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_SENDER_ID.isNotBlank()

    fun initialize(context: Context) {
        if (!isConfigured()) return
        synchronized(this) {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .build()
                )
            }
        }
    }

    fun fetchToken(context: Context, onResult: (String?) -> Unit) {
        runCatching {
            initialize(context)
            if (!isConfigured()) {
                onResult(null)
                return
            }
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    onResult(if (task.isSuccessful) task.result?.takeIf { it.isNotBlank() } else null)
                }
        }.onFailure {
            onResult(null)
        }
    }
}
