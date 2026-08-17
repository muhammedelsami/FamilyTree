package com.familytree.core.common.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** A stream's loading/success/failure state, so screens can render all three. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Failure(val error: Throwable) : LoadState<Nothing>
}

/** Wraps a cold flow so failures surface as state rather than crashing the collector. */
fun <T> Flow<T>.asLoadState(): Flow<LoadState<T>> = this
    .map<T, LoadState<T>> { LoadState.Success(it) }
    .onStart { emit(LoadState.Loading) }
    .catch { emit(LoadState.Failure(it)) }
