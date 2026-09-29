package com.recharge.client.features.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.recharge.client.R

object CallNotificationManager {
    const val ACTION_DECLINE = "com.recharge.client.voice.DECLINE"
    const val ACTION_HANGUP = "com.recharge.client.voice.HANGUP"
    private const val CHANNEL_INCOMING = "incoming_calls_v4"
    private const val CHANNEL_ACTIVE = "active_calls"
    private const val INCOMING_BASE_ID = 48000
    const val ACTIVE_NOTIFICATION_ID = 59021

    private fun ringtoneUri() =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun ringtoneAttributes() =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ringtone = ringtoneUri()
        val audioAttributes = ringtoneAttributes()

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming mPay calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming support and account-service calls from mPay"
                // This channel remains audible as a fallback when Android does not allow
                // the dedicated ringtone service to start from the background.
                setSound(ringtone, audioAttributes)
                enableVibration(true)
                setVibrationPattern(longArrayOf(0L, 500L, 250L, 500L))
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTIVE, "Active mPay calls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for an active mPay voice call"
                setSound(null, null)
            }
        )
    }

    fun buildIncomingNotification(context: Context, callId: String, callerName: String, timeoutMillis: Long = 30_000L): Notification {
        ensureChannels(context)
        val appContext = context.applicationContext
        val ringtone = ringtoneUri()
        val answerIntent = PendingIntent.getActivity(
            appContext,
            callId.hashCode(),
            Intent(appContext, IncomingCallActivity::class.java)
                .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
                .putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
                .putExtra(IncomingCallActivity.EXTRA_ACTION, IncomingCallActivity.ACTION_ANSWER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declineIntent = PendingIntent.getBroadcast(
            appContext,
            callId.hashCode() + 1,
            Intent(appContext, CallActionReceiver::class.java)
                .setAction(ACTION_DECLINE)
                .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            appContext,
            callId.hashCode() + 2,
            Intent(appContext, IncomingCallActivity::class.java)
                .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
                .putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val person = Person.Builder()
            .setName(callerName.ifBlank { "mPay Support" })
            .setImportant(true)
            .build()
        val builder = NotificationCompat.Builder(appContext, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.mpay_logo)
            .setContentTitle("Incoming mPay call")
            .setContentText(callerName.ifBlank { "mPay Support" })
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(timeoutMillis.coerceAtLeast(250L))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declineIntent, answerIntent))
        } else {
            builder.addAction(NotificationCompat.Action.Builder(0, "Decline", declineIntent).build())
                .addAction(NotificationCompat.Action.Builder(0, "Answer", answerIntent).build())
        }
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || notificationManager.canUseFullScreenIntent()) {
            builder.setFullScreenIntent(openIntent, true)
        }
        return builder.build()
    }

    fun showIncoming(
        context: Context,
        callId: String,
        callerName: String,
        expiresAt: String? = null,
        persistentRinging: Boolean = true
    ) {
        val appContext = context.applicationContext
        val remaining = remainingMillisUntil(expiresAt)
        val notification = runCatching {
            buildIncomingNotification(
                appContext,
                callId,
                callerName,
                timeoutMillis = remaining
            )
        }.getOrNull() ?: return

        runCatching {
            androidx.core.app.NotificationManagerCompat.from(appContext)
                .notify(incomingNotificationId(callId), notification)
        }.onFailure {
            android.util.Log.e("CallNotificationManager", "Unable to post incoming call notification. callId=$callId", it)
        }

        if (persistentRinging && remaining > 0L) {
            runCatching {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, IncomingCallRingtoneService::class.java)
                        .setAction(IncomingCallRingtoneService.ACTION_START)
                        .putExtra(IncomingCallRingtoneService.EXTRA_CALL_ID, callId)
                        .putExtra(IncomingCallRingtoneService.EXTRA_CALLER_NAME, callerName)
                        .putExtra(IncomingCallRingtoneService.EXTRA_EXPIRES_AT, expiresAt.orEmpty())
                )
            }.onFailure {
                android.util.Log.e(
                    "CallNotificationManager",
                    "Unable to start incoming ringtone foreground service. callId=$callId",
                    it
                )
                // The CallStyle notification is already posted and its audible
                // channel sound is the fallback in this case.
            }
        }
    }

    private fun remainingMillisUntil(expiresAt: String?): Long {
        return runCatching {
            if (expiresAt.isNullOrBlank()) 30_000L
            else java.time.Instant.parse(expiresAt).toEpochMilli() - System.currentTimeMillis()
        }.getOrDefault(30_000L).coerceAtLeast(250L)
    }

    fun buildActiveNotification(context: Context, callId: String, otherName: String, connected: Boolean): Notification {
        ensureChannels(context)
        val appContext = context.applicationContext
        val hangupIntent = PendingIntent.getBroadcast(
            appContext,
            callId.hashCode() + 3,
            Intent(appContext, CallActionReceiver::class.java)
                .setAction(ACTION_HANGUP)
                .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val person = Person.Builder()
            .setName(otherName.ifBlank { "mPay customer" })
            .setImportant(true)
            .build()
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.mpay_logo)
            .setContentTitle(if (connected) "mPay call in progress" else "Connecting mPay call")
            .setContentText(otherName.ifBlank { "mPay customer" })
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    appContext,
                    callId.hashCode() + 4,
                    Intent(appContext, IncomingCallActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
                        .putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, otherName)
                        .putExtra(IncomingCallActivity.EXTRA_ACTIVE_CALL, true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangupIntent))
        } else {
            builder.addAction(NotificationCompat.Action.Builder(0, "End call", hangupIntent).build())
        }
        return builder.build()
    }

    fun showActive(context: Context, callId: String, otherName: String, connected: Boolean) {
        val notification = buildActiveNotification(context, callId, otherName, connected)
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context.applicationContext)
                .notify(activeNotificationId(callId), notification)
        }
    }

    fun cancelIncoming(context: Context, callId: String) {
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, IncomingCallRingtoneService::class.java))
        androidx.core.app.NotificationManagerCompat.from(appContext)
            .cancel(incomingNotificationId(callId))
    }

    fun cancelActive(context: Context, callId: String) {
        androidx.core.app.NotificationManagerCompat.from(context.applicationContext)
            .cancel(activeNotificationId(callId))
    }

    fun incomingNotificationId(callId: String) = INCOMING_BASE_ID + (callId.hashCode() and 0x0FFF)
    fun activeNotificationId(callId: String) = ACTIVE_NOTIFICATION_ID
}
