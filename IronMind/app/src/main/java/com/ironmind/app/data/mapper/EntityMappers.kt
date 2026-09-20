package com.ironmind.app.data.mapper

import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineEntity
import com.ironmind.app.data.local.entity.SetLogEntity
import com.ironmind.app.data.local.entity.WorkoutSessionEntity
import com.ironmind.app.data.local.relation.RoutineWithExercises
import com.ironmind.app.data.local.relation.SessionWithSets
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutinePlan
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.WorkoutSession

/**
 * Pure mapping functions between Room entities/relations and domain models. Keeping this
 * translation in the data layer lets the domain and presentation layers stay ignorant of Room.
 */

// ---- Exercise -----------------------------------------------------------------------
fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    muscleGroup = muscleGroup,
    equipment = equipment,
    description = description,
    isCustom = isCustom,
    instructions = instructions,
    imagePath = imagePath,
    imageUrl = imageUrl,
    nameEs = nameEs,
)

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    muscleGroup = muscleGroup,
    equipment = equipment,
    description = description,
    isCustom = isCustom,
    instructions = instructions,
    imagePath = imagePath,
    imageUrl = imageUrl,
    nameEs = nameEs,
)

// ---- Routine ------------------------------------------------------------------------
fun RoutineEntity.toDomain(): Routine = Routine(
    id = id,
    name = name,
    split = split,
    description = description,
    position = position,
    createdAt = createdAt,
)

fun Routine.toEntity(): RoutineEntity = RoutineEntity(
    id = id,
    name = name,
    split = split,
    description = description,
    position = position,
    createdAt = createdAt,
)

fun RoutineWithExercises.toDomain(): RoutinePlan = RoutinePlan(
    routine = routine.toDomain(),
    exercises = exercises.map { it.toDomain() },
)

// ---- Workout session ----------------------------------------------------------------
fun WorkoutSessionEntity.toDomain(): WorkoutSession = WorkoutSession(
    id = id,
    routineId = routineId,
    title = title,
    startedAt = startedAt,
    endedAt = endedAt,
    notes = notes,
)

fun WorkoutSession.toEntity(): WorkoutSessionEntity = WorkoutSessionEntity(
    id = id,
    routineId = routineId,
    title = title,
    startedAt = startedAt,
    endedAt = endedAt,
    notes = notes,
)

// ---- Set log ------------------------------------------------------------------------
fun SetLogEntity.toDomain(): SetLog = SetLog(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isWarmup = isWarmup,
    isCompleted = isCompleted,
    restSeconds = restSeconds,
    notes = notes,
    performedAt = performedAt,
)

fun SetLog.toEntity(): SetLogEntity = SetLogEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isWarmup = isWarmup,
    isCompleted = isCompleted,
    restSeconds = restSeconds,
    notes = notes,
    performedAt = performedAt,
)

fun SessionWithSets.toDomain(): SessionDetail = SessionDetail(
    session = session.toDomain(),
    sets = sets.map { it.toDomain() },
)
