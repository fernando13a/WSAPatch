package com.ironmind.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironmind.app.BuildConfig
import com.ironmind.app.core.util.Constants
import com.ironmind.app.data.local.IronMindDatabase
import com.ironmind.app.data.local.Migrations
import com.ironmind.app.data.local.dao.WorkoutDao
import com.ironmind.app.data.local.seed.DefaultExercises
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

/** Wires up the Room database and its DAO. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        // Lazy provider avoids a cycle: the seed callback needs the DAO, which needs the DB.
        daoProvider: Provider<WorkoutDao>,
        @ApplicationScope scope: CoroutineScope,
    ): IronMindDatabase {
        val builder = Room.databaseBuilder(
            context,
            IronMindDatabase::class.java,
            Constants.DATABASE_NAME,
        )
            // Real migrations (empty at v1); production upgrades must go through these.
            .addMigrations(*Migrations.ALL)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    scope.launch { DefaultExercises.seed(daoProvider.get()) }
                }
            })

        // Only during development do we allow a destructive recreate on a missing migration;
        // release builds fail loudly so user data is never silently wiped.
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }

        return builder.build()
    }

    @Provides
    fun provideWorkoutDao(database: IronMindDatabase): WorkoutDao = database.workoutDao()
}
