package com.familytree.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push messages sent from the Firebase console — announcements, not anything about the
 * user's tree, which never leaves the device.
 *
 * There is no server, so the device's token is sent nowhere. Every install joins the
 * [TOPIC_ALL] topic instead, which is what the console addresses.
 */
@Singleton
class PushNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Called once per process, from the Application. */
    fun initialize() {
        // Before any push can arrive: a background message is drawn straight onto this
        // channel by the system, and without it Firebase falls back to one of its own.
        createChannel(context)

        // A checkout without google-services.json has no Firebase at all, and asking it
        // for anything would throw.
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
    }

    companion object {
        /** Must match default_notification_channel_id in this module's manifest. */
        const val CHANNEL = "announcements"
        const val TOPIC_ALL = "all"
        private const val NOTIFICATION_ID = 2001

        internal fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL,
                context.getString(R.string.push_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.push_channel_description) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        internal fun show(context: Context, title: String?, body: String?) {
            val manager = NotificationManagerCompat.from(context)
            // False on Android 13+ until the user has allowed notifications.
            if (!manager.areNotificationsEnabled()) return

            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_family_tree)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()

            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }
}

/**
 * Receives pushes while the app is in the foreground. In the background the system shows a
 * notification message by itself and this is never called; in the foreground nothing would
 * be shown at all without it.
 */
class PushMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val content = message.notification ?: return
        PushNotifications.createChannel(this)
        PushNotifications.show(this, content.title, content.body)
    }
}
