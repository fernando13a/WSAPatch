package com.ironmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup

/** Room representation of an exercise in the catalog. */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val description: String? = null,
    val isCustom: Boolean = true,
)
