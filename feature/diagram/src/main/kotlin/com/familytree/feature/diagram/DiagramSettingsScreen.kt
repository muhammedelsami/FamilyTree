package com.familytree.feature.diagram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.theme.FamilyTreeTheme
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.DiagramSettings
import kotlin.math.roundToInt

@Composable
fun DiagramSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagramSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    DiagramSettingsScreen(
        settings = settings,
        onChange = { updated -> viewModel.update { updated } },
        onReset = viewModel::reset,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagramSettingsScreen(
    settings: DiagramSettings,
    onChange: (DiagramSettings) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagram_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(FtDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
        ) {
            Text(
                text = stringResource(R.string.settings_how_much),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GenerationSlider(
                labelRes = R.string.setting_ancestors,
                value = settings.ancestors,
                onValueChange = { onChange(settings.copy(ancestors = it)) },
            )
            GenerationSlider(
                labelRes = R.string.setting_great_uncles,
                value = settings.greatUncles,
                // Great-uncles branch off the ancestor line, so they can never reach
                // further back than the ancestors themselves.
                enabled = settings.ancestors > 0,
                onValueChange = { onChange(settings.copy(greatUncles = it)) },
            )
            GenerationSlider(
                labelRes = R.string.setting_uncles_cousins,
                value = settings.unclesCousins,
                enabled = settings.ancestors > 0,
                onValueChange = { onChange(settings.copy(unclesCousins = it)) },
            )
            GenerationSlider(
                labelRes = R.string.setting_siblings,
                value = settings.siblingsNephews,
                enabled = settings.ancestors > 0,
                onValueChange = { onChange(settings.copy(siblingsNephews = it)) },
            )
            GenerationSlider(
                labelRes = R.string.setting_descendants,
                value = settings.descendants,
                onValueChange = { onChange(settings.copy(descendants = it)) },
            )

            HorizontalDivider(Modifier.padding(vertical = FtDimens.listItemSpacing))

            SettingSwitch(
                labelRes = R.string.setting_spouses,
                descriptionRes = R.string.setting_spouses_description,
                checked = settings.showSpouses,
                onCheckedChange = { onChange(settings.copy(showSpouses = it)) },
            )
            SettingSwitch(
                labelRes = R.string.setting_numbers,
                descriptionRes = R.string.setting_numbers_description,
                checked = settings.showNumbers,
                onCheckedChange = { onChange(settings.copy(showNumbers = it)) },
            )
            SettingSwitch(
                labelRes = R.string.setting_duplicates,
                descriptionRes = R.string.setting_duplicates_description,
                checked = settings.showDuplicateLines,
                onCheckedChange = { onChange(settings.copy(showDuplicateLines = it)) },
            )
        }
    }
}

/**
 * A generation count on a deliberately uneven scale.
 *
 * The steps are `0,1,2,3,4,5,10,20,50,100`: the first few generations are what people
 * adjust in practice, while the large values exist for "show me everything" and do not
 * need fine control. A linear 0–100 slider would make the useful range unusable.
 */
@Composable
private fun GenerationSlider(
    labelRes: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    val position = DiagramSettings.toSliderPosition(value)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = if (value == 0) stringResource(R.string.setting_none) else value.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = position.toFloat(),
            onValueChange = { onValueChange(DiagramSettings.fromSliderPosition(it.roundToInt())) },
            valueRange = 0f..(DiagramSettings.STEPS.size - 1).toFloat(),
            steps = DiagramSettings.STEPS.size - 2,
            enabled = enabled,
        )
    }
}

@Composable
private fun SettingSwitch(
    labelRes: Int,
    descriptionRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FtDimens.listItemSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview
@Composable
private fun DiagramSettingsPreview() {
    FamilyTreeTheme {
        DiagramSettingsScreen(
            settings = DiagramSettings(),
            onChange = {},
            onReset = {},
            onBack = {},
        )
    }
}
