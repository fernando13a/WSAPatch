package com.ironmind.app.di

import javax.inject.Qualifier

/** Marks the application-wide [kotlinx.coroutines.CoroutineScope] (lives for the process). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
