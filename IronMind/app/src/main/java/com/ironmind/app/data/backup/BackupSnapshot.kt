package com.ironmind.app.data.backup

import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineEntity
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.data.local.entity.SetLogEntity
import com.ironmind.app.data.local.entity.WorkoutSessionEntity
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of the whole database. It mirrors the Room entities field-for-field
 * (preserving primary keys, so relations survive a round trip) but keeps enums as their String
 * names, so the domain enums need not depend on kotlinx.serialization. [version] lets a future
 * schema change migrate an older backup.
 */
@Serializable
data class BackupSnapshot(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val exercises: List<ExerciseDto> = emptyList(),
    val routines: List<RoutineDto> = emptyList(),
    val routineExercises: List<CrossRefDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val setLogs: List<SetLogDto> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class ExerciseDto(
    val id: Long,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val description: String? = null,
    val isCustom: Boolean = true,
)

@Serializable
data class RoutineDto(
    val id: Long,
    val name: String,
    val split: String,
    val description: String? = null,
    val position: Int = 0,
    val createdAt: Long,
)

@Serializable
data class CrossRefDto(
    val routineId: Long,
    val exerciseId: Long,
    val position: Int = 0,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetRestSeconds: Int = 90,
)

@Serializable
data class SessionDto(
    val id: Long,
    val routineId: Long? = null,
    val title: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val notes: String? = null,
)

@Serializable
data class SetLogDto(
    val id: Long,
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
    val performedAt: Long,
)

// ---- Entity <-> DTO mapping ---------------------------------------------------------------

fun ExerciseEntity.toDto() = ExerciseDto(id, name, muscleGroup.name, equipment.name, description, isCustom)
fun ExerciseDto.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    muscleGroup = enumValueOf<MuscleGroup>(muscleGroup),
    equipment = enumValueOf<Equipment>(equipment),
    description = description,
    isCustom = isCustom,
)

fun RoutineEntity.toDto() = RoutineDto(id, name, split.name, description, position, createdAt)
fun RoutineDto.toEntity() = RoutineEntity(
    id = id,
    name = name,
    split = enumValueOf<RoutineSplit>(split),
    description = description,
    position = position,
    createdAt = createdAt,
)

fun RoutineExerciseCrossRef.toDto() =
    CrossRefDto(routineId, exerciseId, position, targetSets, targetReps, targetRestSeconds)
fun CrossRefDto.toEntity() =
    RoutineExerciseCrossRef(routineId, exerciseId, position, targetSets, targetReps, targetRestSeconds)

fun WorkoutSessionEntity.toDto() = SessionDto(id, routineId, title, startedAt, endedAt, notes)
fun SessionDto.toEntity() = WorkoutSessionEntity(id, routineId, title, startedAt, endedAt, notes)

fun SetLogEntity.toDto() =
    SetLogDto(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, isWarmup, isCompleted, restSeconds, notes, performedAt)
fun SetLogDto.toEntity() =
    SetLogEntity(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, isWarmup, isCompleted, restSeconds, notes, performedAt)
