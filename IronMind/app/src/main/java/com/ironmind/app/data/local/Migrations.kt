package com.ironmind.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Ordered Room migrations applied on database upgrade.
 *
 * Room validates every version against the schema JSON exported to `app/schemas` (commit those
 * files so migrations can be verified with `MigrationTestHelper`).
 */
object Migrations {

    /**
     * v2: exercise reference material — a written how-to (`instructions`) and an optional
     * user-attached reference image (`imagePath`). Both are nullable, so existing rows are valid
     * with no backfill.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN instructions TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN imagePath TEXT")
        }
    }

    /** v3: optional public demo-image URL (loaded on demand + cached). Nullable, no backfill. */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN imageUrl TEXT")
        }
    }

    /** v4: Spanish exercise name for bilingual UI. Nullable; populated via vocabulary or Gemma. */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN nameEs TEXT")
        }
    }

    /**
     * v5: index on `set_logs.performedAt`. Backs the "activity since a given timestamp" query
     * used by the on-device AI insights/recovery features (cross-exercise aggregation), which
     * would otherwise force a full table scan of `set_logs` as history grows. Index name matches
     * Room's default convention (`index_<table>_<column>`) so it lines up with the `@Entity`
     * schema Room now expects from `SetLogEntity`.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_set_logs_performedAt ON set_logs(performedAt)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
