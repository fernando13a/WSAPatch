package com.ironmind.app.di

import com.ironmind.app.notification.AndroidRestTimerNotifier
import com.ironmind.app.notification.RestTimerNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds notification helpers to their Android-backed implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindRestTimerNotifier(impl: AndroidRestTimerNotifier): RestTimerNotifier
}
