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
    /** Step-by-step technique cues shown on the exercise detail screen (offline). */
    val instructions: String? = null,
    /** Absolute path to a user-attached reference image on this device, if any (takes priority). */
    val imagePath: String? = null,
    /** Optional public demo-image URL (loaded on demand and cached); null for user-only exercises. */
    val imageUrl: String? = null,
    /** Spanish name for bilingual display; auto-populated from vocabulary or Gemma translation. */
    val nameEs: String? = null,
)
