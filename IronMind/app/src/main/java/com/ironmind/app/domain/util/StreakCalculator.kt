package com.ironmind.app.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Computes the current training streak: the number of consecutive days (ending today, or
 * yesterday if today hasn't been trained yet) on which at least one session was started.
 * Pure and deterministic — [today] and [zoneId] are injectable for testing.
 */
object StreakCalculator {

    fun currentStreak(
        sessionStartMillis: List<Long>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Int {
        if (sessionStartMillis.isEmpty()) return 0

        val trainedDays = sessionStartMillis
            .map { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            .toSet()

        var anchor = when {
            trainedDays.contains(today) -> today
            trainedDays.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        while (trainedDays.contains(anchor)) {
            streak++
            anchor = anchor.minusDays(1)
        }
        return streak
    }
}
