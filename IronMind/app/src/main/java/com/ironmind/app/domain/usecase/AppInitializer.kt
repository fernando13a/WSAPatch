package com.ironmind.app.domain.usecase

/**
 * One-time initialization tasks that run on app startup.
 * Includes populating Spanish exercise names, checking model availability, etc.
 */
interface AppInitializer {
    suspend fun initialize()
}
