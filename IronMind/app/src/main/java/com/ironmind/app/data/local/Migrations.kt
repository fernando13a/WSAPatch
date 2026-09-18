package com.ironmind.app.data.local

import androidx.room.migration.Migration

/**
 * Ordered Room migrations applied on database upgrade. Empty at schema v1.
 *
 * When [IronMindDatabase]'s version is bumped to 2, add a `Migration(1, 2)` here describing the
 * schema change in SQL and include it in [ALL], for example:
 *
 * ```
 * private val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE exercises ADD COLUMN thumbnailUri TEXT")
 *     }
 * }
 * val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
 * ```
 *
 * Room validates every version against the schema JSON exported to `app/schemas` (commit those
 * files so migrations can be verified with `MigrationTestHelper`).
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
