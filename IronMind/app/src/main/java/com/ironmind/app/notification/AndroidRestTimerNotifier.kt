package com.ironmind.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.ironmind.app.MainActivity
import com.ironmind.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [RestTimerNotifier]. Uses two channels so the ongoing countdown stays silent while the
 * completion alert is a heads-up buzz. Every post is best-effort and guarded on whether the user
 * has notifications enabled (POST_NOTIFICATIONS on Android 13+), so a denied permission simply
 * means no notification rather than a crash — the in-app timer keeps working regardless.
 */
@Singleton
class AndroidRestTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : RestTimerNotifier {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannels()
    }

    private fun createChannels() {
        val system = context.getSystemService<NotificationManager>() ?: return
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.rest_channel_progress),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.rest_channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { enableVibration(true) },
        )
    }

    override fun showCountdown(remainingSeconds: Int) {
        if (!manager.areNotificationsEnabled()) return
        val notification = baseBuilder(CHANNEL_PROGRESS)
            .setContentTitle(context.getString(R.string.rest_title))
            .setContentText(context.getString(R.string.rest_notif_remaining, formatMmSs(remainingSeconds)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { manager.notify(NOTIF_ID, notification) }
    }

    override fun showComplete() {
        if (!manager.areNotificationsEnabled()) return
        val notification = baseBuilder(CHANNEL_ALERT)
            .setContentTitle(context.getString(R.string.rest_notif_done_title))
            .setContentText(context.getString(R.string.rest_notif_done_body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        runCatching { manager.notify(NOTIF_ID, notification) }
    }

    override fun cancel() {
        runCatching { manager.cancel(NOTIF_ID) }
    }

    private fun baseBuilder(channelId: String): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(ACCENT_GOLD)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
    }

    private fun formatMmSs(total: Int): String = "%02d:%02d".format(total / 60, total % 60)

    private companion object {
        const val CHANNEL_PROGRESS = "rest_progress"
        const val CHANNEL_ALERT = "rest_alert"
        const val NOTIF_ID = 1001
        const val ACCENT_GOLD = 0xFFFFD700.toInt()
    }
}
