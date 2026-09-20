package com.ironmind.app.di

import com.ironmind.app.data.ai.ExerciseTranslationServiceImpl
import com.ironmind.app.data.ai.LlmInferenceManager
import com.ironmind.app.domain.ai.ExerciseTranslationService
import com.ironmind.app.domain.ai.LlmInferenceService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the on-device AI ports to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindLlmInferenceService(impl: LlmInferenceManager): LlmInferenceService

    @Binds
    @Singleton
    abstract fun bindExerciseTranslationService(impl: ExerciseTranslationServiceImpl): ExerciseTranslationService
}
