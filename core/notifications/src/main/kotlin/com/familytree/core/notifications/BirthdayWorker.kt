package com.familytree.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.model.PersonSummary
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user whose birthday it is today.
 *
 * The original application kept a precomputed list of birthdays in its settings file and
 * had to remember to rebuild it after every edit. Here it is simply a query: the tree is
 * already the source of truth, and a list that can go stale is a bug waiting to happen.
 */
@HiltWorker
class BirthdayWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val trees: TreeRepository,
    private val people: PersonRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        if (!settings.settings.first().birthdayNotifications) return Result.success()
        if (!hasPermission()) return Result.success()

        val celebrating = trees.observeTrees().first().flatMap { tree ->
            people.observePersonSummaries(tree.id).first().filter { it.isBirthdayToday() }
        }
        if (celebrating.isEmpty()) return Result.success()

        notify(celebrating)
        return Result.success()
    }

    /**
     * Whose birthday falls today.
     *
     * The living only. A reminder that a great-grandfather who died in 1954 "turns 134
     * today" is not a nice surprise, and the summary already works out who is deceased.
     */
    private fun PersonSummary.isBirthdayToday(): Boolean =
        !isDeceased && daysToNextBirthday == 0

    private fun hasPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notify(people: List<PersonSummary>) {
        createChannel()

        val title = if (people.size == 1) {
            context.getString(R.string.birthday_today_one, people.first().displayName)
        } else {
            context.resources.getQuantityString(
                R.plurals.birthday_today_many,
                people.size,
                people.size,
            )
        }
        val body = people.joinToString(", ") { person ->
            person.ageInYears
                ?.let { context.getString(R.string.birthday_person_age, person.displayName, it) }
                ?: person.displayName
        }

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.birthday_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.birthday_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val NAME = "familytree-birthdays"
        private const val CHANNEL = "birthdays"
        private const val NOTIFICATION_ID = 1001
    }
}

/** Keeps the daily birthday check scheduled at the hour the user chose. */
@Singleton
class BirthdayScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * @param time `HH:mm`. The job is periodic with a one-day period and an initial delay
     *   that lands on the next occurrence of that time — WorkManager has no "run daily at
     *   nine" of its own, and an exact alarm would be the wrong tool for something this
     *   forgiving about a few minutes either way.
     */
    fun schedule(time: String, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(BirthdayWorker.NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<BirthdayWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntil(time), TimeUnit.MILLISECONDS)
            .build()

        manager.enqueueUniquePeriodicWork(
            BirthdayWorker.NAME,
            // Replace rather than keep: changing the time in settings has to move the job,
            // and keeping the old one would silently ignore the change.
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    private fun millisUntil(time: String): Long {
        val (hour, minute) = time.split(':').mapNotNull(String::toIntOrNull).takeIf { it.size == 2 }
            ?: listOf(9, 0)
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
