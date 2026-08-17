package com.familytree.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.familytree.core.designsystem.theme.FtDimens

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Determinate progress for the long operations this app really does have —
 * importing a large GEDCOM, unzipping a backup, exporting a diagram.
 */
@Composable
fun LabelledProgress(
    label: String,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FtDimens.screenPadding),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        if (progress == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FtDimens.listItemSpacing),
            )
        } else {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FtDimens.listItemSpacing),
            )
        }
    }
}
