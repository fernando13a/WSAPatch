package com.ironmind.app.domain.backup

/**
 * Full-database export / import for the offline app, so a user can save their history off-device
 * and restore it (e.g. on a new phone). The payload is a self-contained JSON string; reading and
 * writing the actual file is a UI concern (Storage Access Framework), kept out of the domain.
 */
interface BackupManager {

    /** Serializes the entire database to a JSON string. */
    suspend fun export(): String

    /**
     * Replaces all current data with the contents of [json]. Throws if the payload cannot be
     * parsed, leaving the existing data untouched.
     */
    suspend fun import(json: String): BackupResult
}

/** Row counts of what a backup contains / restored, for user-facing confirmation. */
data class BackupResult(
    val exercises: Int,
    val routines: Int,
    val sessions: Int,
    val setLogs: Int,
)
