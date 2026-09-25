package com.familytrees.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.familytree.core.notifications.PushNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * WorkManager is configured here rather than by its default initialiser, because the
 * birthday-reminder worker takes its dependencies through Hilt and the stock factory
 * cannot construct it. The matching `androidx.startup` entry is removed in the manifest.
 */
@HiltAndroidApp
class FamilyTreeApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var pushNotifications: PushNotifications

    override fun onCreate() {
        // Hilt injects the fields above inside super.onCreate().
        super.onCreate()
        pushNotifications.initialize()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
