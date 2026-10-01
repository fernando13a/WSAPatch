package com.ironmind.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.ironmind.app.data.local.entity.SetLogEntity
import com.ironmind.app.data.local.entity.WorkoutSessionEntity

/**
 * A workout session together with every set logged during it.
 */
data class SessionWithSets(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val sets: List<SetLogEntity>,
)
