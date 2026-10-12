package com.rutina.app.domain

import com.rutina.app.data.GoalPeriod
import com.rutina.app.data.GoalType
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Reglas puras (sin Android) para calcular progreso, cumplimiento y rachas. */
object HabitLogic {

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun weekStartOf(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(weekStart))

    /** Rango [inicio, fin) del periodo de la meta que contiene [date]. */
    fun periodRange(period: GoalPeriod, date: LocalDate, weekStart: DayOfWeek): Pair<LocalDate, LocalDate> =
        when (period) {
            GoalPeriod.DAY -> date to date.plusDays(1)
            GoalPeriod.WEEK -> weekStartOf(date, weekStart).let { it to it.plusWeeks(1) }
            GoalPeriod.MONTH -> date.withDayOfMonth(1).let { it to it.plusMonths(1) }
        }

    fun isScheduled(habit: Habit, date: LocalDate): Boolean = when (habit.scheduleType) {
        ScheduleType.ALL_DAYS, ScheduleType.DAYS_PER_WEEK -> true
        ScheduleType.SPECIFIC_DAYS -> habit.scheduleDays and (1 shl (date.dayOfWeek.value - 1)) != 0
    }

    fun sumBetween(logs: List<HabitLog>, from: LocalDate, toExclusive: LocalDate): Double {
        val a = startOfDay(from)
        val b = startOfDay(toExclusive)
        return logs.filter { it.timestamp in a until b }.sumOf { it.value }
    }

    fun progress(habit: Habit, logs: List<HabitLog>, date: LocalDate, weekStart: DayOfWeek): Double {
        val (a, b) = periodRange(habit.period, date, weekStart)
        return sumBetween(logs, a, b)
    }

    fun isMet(habit: Habit, value: Double): Boolean = when (habit.goalType) {
        GoalType.POSITIVE -> value >= habit.target
        GoalType.NEGATIVE -> value <= habit.target
        GoalType.NONE -> value > 0
    }

    /** Fracción 0..1 para la barra de progreso. */
    fun fraction(habit: Habit, value: Double): Float = when {
        habit.target <= 0 -> if (value > 0) 1f else 0f
        else -> (value / habit.target).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Racha actual en unidades del periodo (días, semanas o meses).
     * El periodo en curso solo suma si ya se cumplió; si no, no rompe la racha todavía.
     */
    fun streak(habit: Habit, logs: List<HabitLog>, today: LocalDate, weekStart: DayOfWeek): Int {
        val created = dateOf(habit.createdAt)
        if (habit.scheduleType == ScheduleType.DAYS_PER_WEEK && habit.period == GoalPeriod.DAY) {
            return weeklyQuotaStreak(habit, logs, today, weekStart, created)
        }
        var count = 0
        var cursor = today
        var first = true
        repeat(3700) {
            val (a, b) = periodRange(habit.period, cursor, weekStart)
            if (b <= created) return count
            val scheduled = habit.period != GoalPeriod.DAY || isScheduled(habit, cursor)
            if (scheduled) {
                val met = isMet(habit, sumBetween(logs, a, b))
                // Un hábito negativo del periodo actual aún no está "cerrado".
                val open = first && habit.goalType == GoalType.NEGATIVE
                when {
                    met && !open -> count++
                    first -> Unit
                    else -> return count
                }
            }
            first = false
            cursor = a.minusDays(1)
        }
        return count
    }

    private fun weeklyQuotaStreak(
        habit: Habit, logs: List<HabitLog>, today: LocalDate, weekStart: DayOfWeek, created: LocalDate,
    ): Int {
        var count = 0
        var week = weekStartOf(today, weekStart)
        var first = true
        repeat(600) {
            if (week.plusWeeks(1) <= created) return count
            val doneDays = (0 until 7).count { d ->
                val day = week.plusDays(d.toLong())
                isMet(habit, sumBetween(logs, day, day.plusDays(1)))
            }
            when {
                doneDays >= habit.daysPerWeek -> count++
                first -> Unit
                else -> return count
            }
            first = false
            week = week.minusWeeks(1)
        }
        return count
    }

    /** "35d, 2h 15min, 32s" para hábitos de dejar. */
    fun formatElapsed(millis: Long): String {
        val s = (millis / 1000).coerceAtLeast(0)
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return buildString {
            if (d > 0) append("${d}d, ")
            if (d > 0 || h > 0) append("${h}h ")
            append("${m}min, ${sec}s")
        }
    }

    /** "1 vez", "3 veces", "35 min". */
    fun amountLabel(value: Double, unit: String): String =
        "${formatNumber(value)} ${if (unit == "vez" && value != 1.0) "veces" else unit}"

    fun formatNumber(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else String.format("%.1f", v)
}
