package com.rutina.app.domain

import com.rutina.app.data.GoalPeriod
import com.rutina.app.data.GoalType
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.Note
import com.rutina.app.data.ScheduleType
import com.rutina.app.data.TrackingType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Cómo pintar un día en calendarios y mapas. */
enum class CellKind { EMPTY, PARTIAL, DONE, FAILED, SKIP, FUTURE }

data class Cell(val kind: CellKind, val fraction: Float = 1f)

data class HabitSummary(
    val current: Int,
    val best: Int,
    /** Unidad de la racha: "días", "semanas" o "meses". */
    val streakUnit: String,
    /** 0..1, o null si aún no hay periodos evaluados. */
    val recentRate: Float?,
    val recentLabel: String,
    val totalRate: Float?,
    val total: Double,
    val activeDays: Int,
)

data class QuitSummary(val currentMs: Long, val bestMs: Long, val relapses: List<Long>)

data class ChartPoint(val label: String, val value: Double)

/** Estadísticas puras (sin Android) a partir de los registros de un hábito. */
object HabitStats {

    private val es = Locale("es", "MX")

    fun dayTotals(logs: List<HabitLog>): Map<LocalDate, Double> {
        val m = HashMap<LocalDate, Double>()
        for (l in logs) {
            val d = HabitLogic.dateOf(l.timestamp)
            m[d] = (m[d] ?: 0.0) + l.value
        }
        return m
    }

    /** Días en que se reinició un hábito de "Dejar". */
    fun resetDays(logs: List<HabitLog>): Set<LocalDate> = logs.map { HabitLogic.dateOf(it.timestamp) }.toSet()

    /** Hábitos de tipo Diario: cada nota etiquetada cuenta sus palabras como un registro. */
    fun journalLogs(notes: List<Note>): Map<Long, List<HabitLog>> =
        notes.filter { it.habitId != null }.groupBy { it.habitId!! }.mapValues { (hid, ns) ->
            ns.map { HabitLog(habitId = hid, timestamp = it.timestamp, value = NoteLogic.wordCount(it.content).toDouble()) }
        }

    /** Registros efectivos de un hábito (los de Diario salen de las notas). */
    fun logsOf(h: Habit, byHabit: Map<Long, List<HabitLog>>, journal: Map<Long, List<HabitLog>>): List<HabitLog> =
        if (h.type == TrackingType.JOURNAL) journal[h.id].orEmpty() else byHabit[h.id].orEmpty()

    fun created(h: Habit): LocalDate = HabitLogic.dateOf(h.createdAt)

    /** Estado de un día para calendarios y mapas anuales. */
    fun cell(h: Habit, day: LocalDate, totals: Map<LocalDate, Double>, resets: Set<LocalDate>, today: LocalDate): Cell {
        if (day > today) return Cell(CellKind.FUTURE)
        if (day < created(h)) return Cell(CellKind.SKIP)
        if (h.type == TrackingType.QUIT) return Cell(if (day in resets) CellKind.FAILED else CellKind.DONE)
        val v = totals[day] ?: 0.0
        val daily = h.period == GoalPeriod.DAY
        if (daily && !HabitLogic.isScheduled(h, day)) return Cell(if (v > 0) CellKind.DONE else CellKind.SKIP)
        return when (h.goalType) {
            GoalType.POSITIVE -> when {
                v <= 0 -> Cell(CellKind.EMPTY)
                !daily || h.target <= 0 || v >= h.target -> Cell(CellKind.DONE)
                else -> Cell(CellKind.PARTIAL, (v / h.target).toFloat().coerceIn(0.1f, 0.95f))
            }
            GoalType.NEGATIVE -> when {
                daily && v > h.target -> Cell(CellKind.FAILED)
                daily && day == today -> Cell(if (v > 0) CellKind.PARTIAL else CellKind.EMPTY, 0.5f)
                daily -> Cell(CellKind.DONE)
                v > 0 -> Cell(CellKind.PARTIAL, 0.5f)
                else -> Cell(CellKind.EMPTY)
            }
            GoalType.NONE -> Cell(if (v > 0) CellKind.DONE else CellKind.EMPTY)
        }
    }

    /**
     * Resultado de cada periodo evaluado (de más antiguo a más reciente).
     * El periodo en curso solo cuenta si ya se cumplió.
     */
    fun periodResults(h: Habit, totals: Map<LocalDate, Double>, weekStart: DayOfWeek, today: LocalDate): List<Pair<LocalDate, Boolean>> {
        val created = created(h)
        val out = mutableListOf<Pair<LocalDate, Boolean>>()
        fun sum(a: LocalDate, b: LocalDate): Double {
            var s = 0.0
            var d = a
            while (d < b) { s += totals[d] ?: 0.0; d = d.plusDays(1) }
            return s
        }
        if (h.scheduleType == ScheduleType.DAYS_PER_WEEK && h.period == GoalPeriod.DAY) {
            var w = HabitLogic.weekStartOf(created, weekStart)
            val cur = HabitLogic.weekStartOf(today, weekStart)
            while (w <= cur) {
                val done = (0..6).count { HabitLogic.isMet(h, totals[w.plusDays(it.toLong())] ?: 0.0) && (totals[w.plusDays(it.toLong())] ?: 0.0) > 0 }
                val met = done >= h.daysPerWeek
                if (w < cur || met) out += w to met
                w = w.plusWeeks(1)
            }
            return out
        }
        if (h.period == GoalPeriod.DAY) {
            var d = created
            while (d <= today) {
                if (HabitLogic.isScheduled(h, d)) {
                    val met = HabitLogic.isMet(h, totals[d] ?: 0.0)
                    val open = d == today && (h.goalType == GoalType.NEGATIVE || !met)
                    if (!open) out += d to met
                }
                d = d.plusDays(1)
            }
            return out
        }
        var start = HabitLogic.periodRange(h.period, created, weekStart).first
        while (start <= today) {
            val (a, b) = HabitLogic.periodRange(h.period, start, weekStart)
            val met = HabitLogic.isMet(h, sum(a, b))
            val open = today < b && (h.goalType == GoalType.NEGATIVE || !met)
            if (!open) out += a to met
            start = b
        }
        return out
    }

    fun summary(h: Habit, logs: List<HabitLog>, weekStart: DayOfWeek, today: LocalDate = LocalDate.now()): HabitSummary {
        val totals = dayTotals(logs)
        val results = periodResults(h, totals, weekStart, today)
        var current = 0
        for (r in results.asReversed()) { if (r.second) current++ else break }
        var best = 0
        var run = 0
        for (r in results) { if (r.second) { run++; if (run > best) best = run } else run = 0 }

        val weekly = h.period == GoalPeriod.WEEK || (h.scheduleType == ScheduleType.DAYS_PER_WEEK && h.period == GoalPeriod.DAY)
        val (recent, label) = when {
            h.period == GoalPeriod.MONTH -> results.takeLast(3) to "Últimos 3 meses"
            weekly -> results.takeLast(4) to "Últimas 4 semanas"
            else -> results.filter { it.first >= today.minusDays(29) } to "Últimos 30 días"
        }
        fun rate(l: List<Pair<LocalDate, Boolean>>) = if (l.isEmpty()) null else l.count { it.second }.toFloat() / l.size
        return HabitSummary(
            current = current,
            best = best,
            streakUnit = when { h.period == GoalPeriod.MONTH -> "meses"; weekly -> "semanas"; else -> "días" },
            recentRate = rate(recent),
            recentLabel = label,
            totalRate = rate(results),
            total = logs.sumOf { it.value },
            activeDays = totals.count { it.value > 0 },
        )
    }

    fun quitSummary(h: Habit, logs: List<HabitLog>, now: Long = System.currentTimeMillis()): QuitSummary {
        val resets = logs.map { it.timestamp }.sorted()
        var prev = h.createdAt
        var best = 0L
        for (r in resets) { best = maxOf(best, r - prev); prev = r }
        val current = now - (h.quitSince ?: prev)
        return QuitSummary(current, maxOf(best, current), resets.asReversed())
    }

    /** Serie para la gráfica de barras: por día, semana o mes según el hábito. */
    fun chart(h: Habit, totals: Map<LocalDate, Double>, weekStart: DayOfWeek, today: LocalDate = LocalDate.now()): List<ChartPoint> =
        when (h.period) {
            GoalPeriod.DAY -> (13 downTo 0).map { i ->
                val d = today.minusDays(i.toLong())
                ChartPoint(d.dayOfWeek.getDisplayName(TextStyle.NARROW, es).uppercase(), totals[d] ?: 0.0)
            }
            GoalPeriod.WEEK -> (7 downTo 0).map { i ->
                val a = HabitLogic.weekStartOf(today, weekStart).minusWeeks(i.toLong())
                ChartPoint("${a.dayOfMonth}/${a.monthValue}", (0..6).sumOf { totals[a.plusDays(it.toLong())] ?: 0.0 })
            }
            GoalPeriod.MONTH -> (5 downTo 0).map { i ->
                val a = today.withDayOfMonth(1).minusMonths(i.toLong())
                val days = a.lengthOfMonth()
                ChartPoint(
                    a.month.getDisplayName(TextStyle.SHORT, es).replace(".", ""),
                    (0 until days).sumOf { totals[a.plusDays(it.toLong())] ?: 0.0 },
                )
            }
        }

    fun chartTitle(h: Habit): String = when (h.period) {
        GoalPeriod.DAY -> "Últimos 14 días"
        GoalPeriod.WEEK -> "Últimas 8 semanas"
        GoalPeriod.MONTH -> "Últimos 6 meses"
    }
}
