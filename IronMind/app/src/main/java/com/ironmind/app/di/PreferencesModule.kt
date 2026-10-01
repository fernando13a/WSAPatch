package com.ironmind.app.di

import com.ironmind.app.data.preferences.AppPreferences
import com.ironmind.app.data.preferences.SharedPrefsAppPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds app-preferences to the SharedPreferences-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindAppPreferences(impl: SharedPrefsAppPreferences): AppPreferences
}
