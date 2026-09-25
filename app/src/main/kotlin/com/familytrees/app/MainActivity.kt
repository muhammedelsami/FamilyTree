package com.familytrees.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytrees.app.navigation.FamilyTreeNavHost
import com.familytree.core.designsystem.theme.FamilyTreeTheme
import com.familytree.core.model.AppFont
import com.familytree.core.model.ThemePreference
import com.familytree.core.ui.fontFamily
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity.
 *
 * [AppCompatActivity] rather than `ComponentActivity` for one reason: the per-app language
 * setting. `AppCompatDelegate.setApplicationLocales` reaches the platform through a context
 * it takes from AppCompat's *active delegates*, so with no AppCompat activity in the app
 * the call finds none and silently does nothing — the language appears to be accepted and
 * never changes. Being an AppCompat activity also applies the stored locale to this
 * activity's resources below Android 13, and sets the layout direction that mirrors the
 * interface for Arabic. Everything drawn here is still Compose; no AppCompat view is used.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Nothing to do with the answer: a refusal only means no notifications are shown, and
    // everything that posts one checks for itself.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // Swaps the launch theme for Theme.FamilyTree, so it has to run before AppCompat
        // reads the window in `super.onCreate`.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // The splash stays up until DataStore has answered. Everything below is drawn
        // from `settings`, and without this the first frames use the fallbacks — the
        // system's night mode under a user who chose the opposite, Alexandria under a
        // user who chose the device font — and then correct themselves in front of the
        // user. The deadline is there because a splash that waits on a store that never
        // answers is a launch that never finishes; a wrong first frame beats none.
        val giveUpAt = SystemClock.uptimeMillis() + SETTINGS_WAIT_MS
        splashScreen.setKeepOnScreenCondition {
            viewModel.settings.value == null && SystemClock.uptimeMillis() < giveUpAt
        }

        // Only on a fresh start, not on every recreation — a language change recreates the
        // activity, and the question should not come back with it.
        if (savedInstanceState == null) askForNotifications()

        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings?.theme) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                // Null while settings are still loading — follow the system rather than
                // flashing the light theme at a user who has chosen dark.
                else -> isSystemInDarkTheme()
            }

            // The bars are transparent and the app draws under them, so the system decides
            // whether to paint the clock, battery and gesture pill dark or light. It takes
            // that from the *platform's* night mode, which is only right while the app's
            // theme agrees with it: choose Light on a dark-mode phone and the icons stay
            // white on a white background, leaving the status bar unreadable. Written
            // straight to the insets controller rather than through `enableEdgeToEdge`,
            // because the AppCompat DayNight theme this activity needs for the language
            // API re-applies its own value from the platform's night mode and wins.
            val view = LocalView.current
            DisposableEffect(darkTheme) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
                onDispose {}
            }

            FamilyTreeTheme(
                darkTheme = darkTheme,
                dynamicColor = settings?.dynamicColor ?: false,
                // Null while settings load: use the default the store would return, so the
                // first frame is not drawn in a face the user never chose.
                fontFamily = (settings?.font ?: AppFont.ALEXANDRIA).fontFamily(),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FamilyTreeNavHost()
                }
            }
        }
    }

    /**
     * From Android 13 a notification — a birthday reminder or a push — is shown only once the
     * user has allowed it here. Declaring the permission in the manifest is not enough, and
     * without this request it was never granted, so reminders silently never appeared.
     * Asked at launch rather than from a settings switch because push has no switch of its
     * own. Android stops showing the prompt by itself after the user declines it twice.
     */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        /** How long the splash may wait for the first settings emission. */
        const val SETTINGS_WAIT_MS = 1_000L
    }
}
