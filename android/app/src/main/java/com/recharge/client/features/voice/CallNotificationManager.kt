package com.recharge.client.features.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.recharge.client.R

object CallNotificationManager {
    const val ACTION_DECLINE = "com.recharge.client.voice.DECLINE"
    const val ACTION_HANGUP = "com.recharge.client.voice.HANGUP"
    private const val CHANNEL_INCOMING = "incoming_calls_v2"
    private const val CHANNEL_ACTIVE = "active_calls"
    private const val INCOMING_BASE_ID = 48000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming mPay calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming support and account-service calls from mPay"
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

    fun showIncoming(context: Context, callId: String, callerName: String) {
        ensureChannels(context)
        val appContext = context.applicationContext
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
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(ringtone, audioAttributes)
            .setVibrate(longArrayOf(0L, 500L, 250L, 500L))
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(35_000L)
            .setFullScreenIntent(openIntent, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declineIntent, answerIntent))
        } else {
            builder.addAction(NotificationCompat.Action.Builder(0, "Decline", declineIntent).build())
                .addAction(NotificationCompat.Action.Builder(0, "Answer", answerIntent).build())
        }

        runCatching {
            androidx.core.app.NotificationManagerCompat.from(appContext)
                .notify(notificationId(callId), builder.build())
        }
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
                        .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
                        .putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, otherName),
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
        androidx.core.app.NotificationManagerCompat.from(context.applicationContext)
            .cancel(notificationId(callId))
    }

    fun cancelActive(context: Context, callId: String) {
        androidx.core.app.NotificationManagerCompat.from(context.applicationContext)
            .cancel(activeNotificationId(callId))
    }

    private fun notificationId(callId: String) = INCOMING_BASE_ID + (callId.hashCode() and 0x0FFF)
    private fun activeNotificationId(callId: String) = notificationId(callId) + 10000
}
