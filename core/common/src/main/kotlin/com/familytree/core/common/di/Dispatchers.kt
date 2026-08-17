package com.familytree.core.common.di

import javax.inject.Qualifier

/**
 * Injects a specific [kotlinx.coroutines.CoroutineDispatcher] instead of referencing
 * `Dispatchers.IO` directly, so tests can swap in a deterministic scheduler.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: FtDispatcher)

enum class FtDispatcher { Default, IO }

/** The application-lifetime coroutine scope, for work that must outlive any screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
