package com.familytree.feature.share

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareActionState(
    val busy: Boolean = false,
    val shared: Boolean = false,
    /** A received copy and the tree here it seems to belong to, once one is found. */
    val received: Pair<Long, Long?>? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class ShareActionsViewModel @Inject constructor(
    private val share: ShareRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareActionState())
    val state: StateFlow<ShareActionState> = _state.asStateFlow()

    fun share(treeId: Long, uri: String) = viewModelScope.launch {
        _state.update { it.copy(busy = true, failed = false) }
        share.share(treeId, uri)
            .onSuccess { _state.update { it.copy(busy = false, shared = true) } }
            .onFailure { _state.update { it.copy(busy = false, failed = true) } }
    }

    fun receive(uri: String, fallbackTitle: String) = viewModelScope.launch {
        _state.update { it.copy(busy = true, failed = false) }
        share.receive(uri, fallbackTitle)
            .onSuccess { result ->
                _state.update { it.copy(busy = false, received = result.treeId to result.originTreeId) }
            }
            .onFailure { _state.update { it.copy(busy = false, failed = true) } }
    }

    fun consume() = _state.update { ShareActionState() }
}

/**
 * Wires up the two file pickers the sharing flow needs.
 *
 * Kept as a composable rather than screens of their own: sharing a tree is a single
 * decision followed by the system's own file chooser, and a screen in front of that would
 * only be a page with one button on it.
 */
@Composable
fun ShareLaunchers(
    treeId: Long,
    treeTitle: String,
    onShared: () -> Unit,
    onReceived: (incomingTreeId: Long, originTreeId: Long?) -> Unit,
    onFailed: () -> Unit,
    viewModel: ShareActionsViewModel = hiltViewModel(),
    // Last, so the caller can pass it as a trailing lambda.
    content: @Composable (share: () -> Unit, receive: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val saveShare = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.share(treeId, it.toString()) } }

    val openShare = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.receive(it.toString(), context.getString(R.string.receive_share)) }
    }

    LaunchedEffect(state.shared) {
        if (state.shared) {
            onShared()
            viewModel.consume()
        }
    }
    LaunchedEffect(state.received) {
        state.received?.let { (incoming, origin) ->
            onReceived(incoming, origin)
            viewModel.consume()
        }
    }
    LaunchedEffect(state.failed) {
        if (state.failed) {
            onFailed()
            viewModel.consume()
        }
    }

    content(
        { saveShare.launch("${treeTitle.ifBlank { "family" }}-share.zip") },
        { openShare.launch(arrayOf("application/zip", "application/octet-stream")) },
    )
}
