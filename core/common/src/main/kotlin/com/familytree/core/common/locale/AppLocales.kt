package com.familytree.core.common.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.familytree.core.model.AppLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The application's language.
 *
 * Deliberately *not* stored in DataStore with the other settings. The manifest declares
 * `android:localeConfig`, so on Android 13+ this app already appears in the system's
 * per-app language picker, and the system is then free to change the language without
 * asking us. A copy in DataStore would be a second answer to the same question, wrong
 * whenever the user used the system screen — so the platform's own store is the only one.
 *
 * [AppCompatDelegate] is the seam: on Android 13+ it forwards to the framework's
 * `LocaleManager`, and below it keeps the choice itself. That backport is the sole reason
 * `androidx.appcompat` is a dependency; no AppCompat activity or view is involved.
 */
@Singleton
class AppLocales @Inject constructor() {

    /** The chosen language, or [AppLanguage.SYSTEM] when the app follows the device. */
    fun current(): AppLanguage =
        AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag())

    fun set(language: AppLanguage) {
        val locales = language.tag
            ?.let { LocaleListCompat.forLanguageTags(it) }
            // An empty list is how "follow the device" is expressed; clearing the
            // preference is not the same as setting it to the device's current language,
            // which would then survive the user changing the device.
            ?: LocaleListCompat.getEmptyLocaleList()
        // Recreating the activity is AppCompat's job, not the caller's: from Android 13
        // the framework restarts it, and below that the backport recreates the delegates
        // it owns — which includes MainActivity, precisely because it is an
        // AppCompatActivity.
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
