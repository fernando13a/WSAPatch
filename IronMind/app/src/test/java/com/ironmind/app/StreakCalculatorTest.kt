package com.ironmind.app

import com.ironmind.app.domain.util.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 1, 10)

    private fun day(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun emptyHistoryIsZero() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), today, zone))
    }

    @Test
    fun consecutiveDaysEndingTodayCount() {
        val millis = listOf(today, today.minusDays(1), today.minusDays(2)).map(::day)
        assertEquals(3, StreakCalculator.currentStreak(millis, today, zone))
    }

    @Test
    fun countsFromYesterdayWhenTodayNotTrainedYet() {
        val millis = listOf(today.minusDays(1), today.minusDays(2)).map(::day)
        assertEquals(2, StreakCalculator.currentStreak(millis, today, zone))
    }

    @Test
    fun gapBeforeYesterdayResetsToZero() {
        val millis = listOf(today.minusDays(3), today.minusDays(4)).map(::day)
        assertEquals(0, StreakCalculator.currentStreak(millis, today, zone))
    }

    @Test
    fun multipleSessionsSameDayCountOnce() {
        val millis = listOf(day(today), day(today) + 3_600_000, day(today.minusDays(1)))
        assertEquals(2, StreakCalculator.currentStreak(millis, today, zone))
    }
}
