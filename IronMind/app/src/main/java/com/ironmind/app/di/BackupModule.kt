package com.ironmind.app.di

import com.ironmind.app.data.backup.RoomBackupManager
import com.ironmind.app.domain.backup.BackupManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the data-layer backup implementation to the domain contract. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindBackupManager(impl: RoomBackupManager): BackupManager
}
