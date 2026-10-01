package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Exercise

/**
 * Deterministic (non-AI) exercise substitutes for [target]: same muscle group, different
 * equipment, excluding [target] itself. Pure so it is unit-testable without a real DAO.
 *
 * @param candidates exercises that already share [target]'s muscle group (e.g. from a
 *   `WHERE muscleGroup = :muscleGroup` query) — this function only applies the equipment/self
 *   exclusion on top.
 */
fun filterAlternatives(target: Exercise, candidates: List<Exercise>): List<Exercise> =
    candidates.filter { it.id != target.id && it.equipment != target.equipment }
