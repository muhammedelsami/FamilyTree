package com.familytree.feature.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.domain.repository.PurchaseOutcome
import com.familytree.core.model.AppFont
import com.familytree.core.model.AppLanguage
import com.familytree.core.model.ThemePreference
import com.familytree.core.ui.fontFamily

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onExpertMode = viewModel::setExpertMode,
        onAutoSave = viewModel::setAutoSave,
        onLoadTreeAtStartup = viewModel::setLoadTreeAtStartup,
        onTheme = viewModel::setTheme,
        onDynamicColor = viewModel::setDynamicColor,
        onLanguage = viewModel::setLanguage,
        onFont = viewModel::setFont,
        onBirthdayNotifications = viewModel::setBirthdayNotifications,
        onNotifyTime = viewModel::setNotifyTime,
        onBuyPremium = { context.activity()?.let(viewModel::purchasePremium) },
        onRestorePurchases = viewModel::restorePurchases,
        onPurchaseMessageShown = viewModel::onPurchaseMessageShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onExpertMode: (Boolean) -> Unit,
    onAutoSave: (Boolean) -> Unit,
    onLoadTreeAtStartup: (Boolean) -> Unit,
    onTheme: (ThemePreference) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onFont: (AppFont) -> Unit,
    onBirthdayNotifications: (Boolean) -> Unit,
    onNotifyTime: (String) -> Unit,
    onBuyPremium: () -> Unit,
    onRestorePurchases: () -> Unit,
    onPurchaseMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pickingTime by remember { mutableStateOf(false) }

    val purchaseMessage = uiState.purchaseMessage?.let {
        stringResource(
            when (it) {
                PurchaseOutcome.Purchased -> R.string.premium_thanks
                PurchaseOutcome.AlreadyOwned -> R.string.premium_already_owned
                PurchaseOutcome.Cancelled -> R.string.premium_cancelled
                PurchaseOutcome.Unavailable -> R.string.premium_unavailable
                is PurchaseOutcome.Failed -> R.string.premium_failed
            },
        )
    }
    LaunchedEffect(purchaseMessage) {
        purchaseMessage?.let {
            snackbarHostState.showSnackbar(it)
            onPurchaseMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FtDimens.screenPadding)
                .padding(bottom = FtDimens.space8),
            // The rows carry their own vertical padding, so this is the hairline of air
            // between them rather than the gap — the grouping comes from `groupSpacing`.
            verticalArrangement = Arrangement.spacedBy(FtDimens.space1),
        ) {
            SectionTitle(stringResource(R.string.section_appearance))
            SettingDropdown(
                label = stringResource(R.string.label_theme),
                options = ThemePreference.entries,
                selected = uiState.settings.theme,
                onSelect = onTheme,
                optionLabel = { stringResource(it.labelRes()) },
            )
            SettingDropdown(
                label = stringResource(R.string.label_language),
                options = AppLanguage.entries,
                selected = uiState.language,
                onSelect = onLanguage,
                optionLabel = { it.endonym.ifEmpty { stringResource(R.string.language_system) } },
            )
            SettingDropdown(
                label = stringResource(R.string.label_font),
                options = AppFont.entries,
                selected = uiState.settings.font,
                onSelect = onFont,
                optionLabel = { it.endonym.ifEmpty { stringResource(R.string.font_system) } },
                // Each face shown in itself, with a specimen of the three scripts the app
                // ships. A typeface name tells most people nothing; the shapes do.
                optionFontFamily = { it.fontFamily() },
                optionSupporting = { stringResource(R.string.font_specimen) },
            )
            SettingSwitch(
                title = stringResource(R.string.dynamic_colour),
                description = stringResource(R.string.dynamic_colour_description),
                checked = uiState.settings.dynamicColor,
                onCheckedChange = onDynamicColor,
            )

            Spacer(Modifier.height(FtDimens.groupSpacing))
            SectionTitle(stringResource(R.string.section_editing))
            SettingSwitch(
                title = stringResource(R.string.auto_save),
                description = stringResource(R.string.auto_save_description),
                checked = uiState.settings.autoSave,
                onCheckedChange = onAutoSave,
            )
            SettingSwitch(
                title = stringResource(R.string.expert_mode),
                description = stringResource(R.string.expert_mode_description),
                checked = uiState.settings.expertMode,
                onCheckedChange = onExpertMode,
            )
            SettingSwitch(
                title = stringResource(R.string.load_tree_at_startup),
                description = stringResource(R.string.load_tree_at_startup_description),
                checked = uiState.settings.loadTreeAtStartup,
                onCheckedChange = onLoadTreeAtStartup,
            )

            Spacer(Modifier.height(FtDimens.groupSpacing))
            SectionTitle(stringResource(R.string.section_notifications))
            SettingSwitch(
                title = stringResource(R.string.birthday_reminders),
                description = stringResource(R.string.birthday_reminders_description),
                checked = uiState.settings.birthdayNotifications,
                onCheckedChange = onBirthdayNotifications,
            )
            if (uiState.settings.birthdayNotifications) {
                // Shaped like the pickers above it: label left, value right, quieter than
                // the label because the label is what is being scanned for.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { pickingTime = true }
                        .heightIn(min = FtDimens.minTouchTarget)
                        .padding(vertical = FtDimens.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.reminder_time),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = uiState.settings.notifyTime,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(FtDimens.groupSpacing))
            PremiumCard(uiState, onBuyPremium, onRestorePurchases)
        }
    }

    if (pickingTime) {
        val (hour, minute) = uiState.settings.notifyTime.split(':')
            .mapNotNull(String::toIntOrNull)
            .takeIf { it.size == 2 } ?: listOf(9, 0)
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        Dialog(onDismissRequest = { pickingTime = false }) {
            // A dialog sits above everything, so it takes the highest container tone
            // rather than a card's: on the near-black dark surface a card-toned dialog
            // has no edge at all.
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    Modifier.padding(FtDimens.cardPadding),
                    horizontalAlignment = Alignment.End,
                ) {
                    TimePicker(state = state)
                    Row {
                        TextButton(onClick = { pickingTime = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                pickingTime = false
                                onNotifyTime("%02d:%02d".format(state.hour, state.minute))
                            },
                        ) {
                            Text(stringResource(R.string.set))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The one paid feature, described rather than advertised.
 *
 * It says what the money buys and what stays free, because a genealogy app asking for
 * payment should be clear that the family data itself is never behind the wall.
 */
@Composable
private fun PremiumCard(
    uiState: SettingsUiState,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
) {
    // Outlined, not filled: this is the only card on the screen, and a hairline states
    // "these belong together" without putting a slab of colour under a screen whose whole
    // point is that it is quiet.
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(FtDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(FtDimens.space3),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
            ) {
                Icon(
                    Icons.Outlined.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(
                        if (uiState.premium) R.string.premium_active else R.string.premium,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.premium_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                uiState.premium -> Text(
                    text = stringResource(R.string.premium_thanks),
                    style = MaterialTheme.typography.bodyMedium,
                )

                uiState.offer != null -> Button(
                    onClick = onBuy,
                    enabled = !uiState.purchasing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.premium_buy, uiState.offer.formattedPrice))
                }

                else -> Text(
                    // The store is unreachable — an installed-by-hand build, or no Play
                    // Services. Saying so beats a button that does nothing.
                    text = stringResource(R.string.premium_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!uiState.premium) {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.restore_purchases)) }
            }
        }
    }
}

/**
 * A setting whose value is chosen from a short list.
 *
 * A row that states the answer — "Theme … Dark" — and offers the alternatives only when
 * asked, rather than a radio group spending a screenful on options the user is not
 * currently choosing between. It is drawn as a plain row and not as a bordered field
 * because three bordered fields stacked at the top of a settings screen read as a form to
 * be filled in; these are settings that already have values, and the border adds a rectangle
 * per row without adding any information. The label and value carry the structure, and the
 * chevron says it opens.
 *
 * @param optionLabel how an option names itself. Composable because most of these names are
 *   string resources, and one of them ([AppLanguage]) deliberately is not.
 * @param optionFontFamily the face to draw an option in, for the picker that chooses faces.
 *   `null` means "leave the theme's face alone" — which is what the [Text] `fontFamily`
 *   parameter does with null, unlike `TextStyle.copy`, where a null family *clears* the
 *   inherited one and silently drops the row back to the platform font.
 * @param optionSupporting a second line under the option, used for the font specimens.
 */
@Composable
private fun <T> SettingDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    optionFontFamily: (T) -> FontFamily? = { null },
    optionSupporting: (@Composable (T) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.DropdownList) { expanded = true }
                .heightIn(min = FtDimens.minTouchTarget)
                .padding(vertical = FtDimens.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FtDimens.space3),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            // Deliberately unweighted. A weight here would split the row down the middle
            // and let each value end wherever its text happens to run out, leaving the
            // chevrons in a ragged column; with only the label weighted, the value and the
            // chevron are pushed against the right edge and every row lines up.
            Text(
                text = optionLabel(selected),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = optionFontFamily(selected),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = optionLabel(option),
                                fontFamily = optionFontFamily(option),
                            )
                            optionSupporting?.let { supporting ->
                                Text(
                                    text = supporting(option),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = optionFontFamily(option),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    // The open menu still says which one is current; a list of equals would
                    // make the user close it to find out what they already had.
                    trailingIcon = {
                        if (option == selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * A group heading.
 *
 * Small, in the primary green, with the space above it doing the separating. The rules that
 * used to sit between groups drew four horizontal lines down a screen whose content is
 * already a column of rows; the gap says the same thing and leaves the screen quieter.
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row toggles, not just the switch: a 48dp target beats a 32dp one,
            // and the switch alone is smaller than a fingertip.
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .heightIn(min = FtDimens.minTouchTarget)
            .padding(vertical = FtDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
        // Without a gap the description runs right up against the switch and reads as
        // though it is underneath it.
        horizontalArrangement = Arrangement.spacedBy(FtDimens.screenPadding),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun ThemePreference.labelRes(): Int = when (this) {
    ThemePreference.SYSTEM -> R.string.theme_system
    ThemePreference.LIGHT -> R.string.theme_light
    ThemePreference.DARK -> R.string.theme_dark
}

private fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
