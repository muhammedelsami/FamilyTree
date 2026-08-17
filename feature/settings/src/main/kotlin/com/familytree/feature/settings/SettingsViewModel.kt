package com.familytree.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.common.locale.AppLocales
import com.familytree.core.domain.repository.PremiumOffer
import com.familytree.core.domain.repository.PremiumRepository
import com.familytree.core.domain.repository.PurchaseOutcome
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.model.AppFont
import com.familytree.core.model.AppLanguage
import com.familytree.core.model.AppSettings
import com.familytree.core.model.ThemePreference
import com.familytree.core.notifications.BirthdayScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val premium: Boolean = false,
    val offer: PremiumOffer? = null,
    val purchasing: Boolean = false,
    val purchaseMessage: PurchaseOutcome? = null,
    /**
     * Read from the platform rather than from [settings] — the language is the one
     * preference the system also owns. See `AppLocales`.
     */
    val language: AppLanguage = AppLanguage.SYSTEM,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val premiumRepository: PremiumRepository,
    private val birthdays: BirthdayScheduler,
    private val locales: AppLocales,
) : ViewModel() {

    private val transient = MutableStateFlow(SettingsUiState(language = locales.current()))

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        premiumRepository.isPremium,
        transient,
    ) { settings, premium, local ->
        local.copy(settings = settings, premium = premium)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), transient.value)

    init {
        // Asked once and cached: the store call is slow and the price does not change
        // while the screen is open.
        viewModelScope.launch {
            transient.update { it.copy(offer = premiumRepository.offer()) }
        }
        viewModelScope.launch { premiumRepository.restorePurchases() }
    }

    fun setExpertMode(enabled: Boolean) = update { it.copy(expertMode = enabled) }

    fun setAutoSave(enabled: Boolean) = update { it.copy(autoSave = enabled) }

    fun setLoadTreeAtStartup(enabled: Boolean) = update { it.copy(loadTreeAtStartup = enabled) }

    fun setTheme(theme: ThemePreference) = update { it.copy(theme = theme) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setFont(font: AppFont) = update { it.copy(font = font) }

    fun setLanguage(language: AppLanguage) {
        if (language == transient.value.language) return
        transient.update { it.copy(language = language) }
        // Recreates the activity, so it goes last: anything after it may not run.
        locales.set(language)
    }

    fun setBirthdayNotifications(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(birthdayNotifications = enabled) }
        // The scheduler is told immediately: a preference that only takes effect after a
        // restart is a preference the user will believe is broken.
        val current = settingsRepository.settings.first()
        birthdays.schedule(current.notifyTime, enabled)
    }

    fun setNotifyTime(time: String) = viewModelScope.launch {
        settingsRepository.update { it.copy(notifyTime = time) }
        val current = settingsRepository.settings.first()
        birthdays.schedule(time, current.birthdayNotifications)
    }

    fun purchasePremium(activity: Any) = viewModelScope.launch {
        transient.update { it.copy(purchasing = true) }
        val outcome = premiumRepository.purchase(activity)
        transient.update { it.copy(purchasing = false, purchaseMessage = outcome) }
    }

    fun restorePurchases() = viewModelScope.launch {
        premiumRepository.restorePurchases()
    }

    fun onPurchaseMessageShown() = transient.update { it.copy(purchaseMessage = null) }

    private fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }
}
