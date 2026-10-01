package com.ironmind.app.di

import com.ironmind.app.data.AppInitializerImpl
import com.ironmind.app.domain.usecase.AppInitializer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds app-level initialization tasks. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAppInitializer(impl: AppInitializerImpl): AppInitializer
}
