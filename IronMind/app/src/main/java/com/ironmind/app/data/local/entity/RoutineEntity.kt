package com.ironmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ironmind.app.domain.model.RoutineSplit

/** Room representation of a routine / training day (e.g. a "Push" day). */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val split: RoutineSplit,
    val description: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
