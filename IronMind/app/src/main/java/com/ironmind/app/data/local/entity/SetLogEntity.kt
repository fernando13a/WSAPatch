package com.ironmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room representation of a single logged set. Cascades on deletion of its parent session
 * or exercise. [notes] holds free-form context (supplements, energy, form cues) that the
 * on-device AI can later summarize.
 */
@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId"), Index("performedAt")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Float? = null,
    val isWarmup: Boolean = false,
    val isCompleted: Boolean = true,
    val restSeconds: Int? = null,
    val notes: String? = null,
    val performedAt: Long = System.currentTimeMillis(),
)
