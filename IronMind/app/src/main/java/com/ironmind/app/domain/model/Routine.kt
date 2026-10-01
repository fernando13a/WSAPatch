package com.ironmind.app.domain.model

/**
 * A planned routine (a training day) grouping a set of exercises under a [split].
 */
data class Routine(
    val id: Long = 0,
    val name: String,
    val split: RoutineSplit,
    val description: String? = null,
    /** Ordering position within the user's list of routines. */
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
