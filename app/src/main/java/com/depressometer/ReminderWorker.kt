package com.depressometer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        ensureChannel(ctx)

        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return Result.success()

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setContentText(ctx.getString(R.string.notif_body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            nm.notify(NOTIF_ID, notif)
        } catch (e: SecurityException) {
            // Permission revoked — nothing to do.
        }
        return Result.success()
    }

    companion object {
        const val NOTIF_ID = 1001

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (mgr.getNotificationChannel(ReminderScheduler.CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        ReminderScheduler.CHANNEL_ID,
                        ctx.getString(R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = ctx.getString(R.string.notif_channel_desc) }
                    mgr.createNotificationChannel(channel)
                }
            }
        }
    }
}
