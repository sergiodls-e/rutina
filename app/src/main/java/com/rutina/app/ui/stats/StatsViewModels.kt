package com.rutina.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.GoalPeriod
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.CellKind
import com.rutina.app.domain.ChartPoint
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.HabitStats
import com.rutina.app.domain.HabitSummary
import com.rutina.app.domain.QuitSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale("es", "MX")

// TODO Fase 9: leer de Ajustes.
val StatsWeekStart: DayOfWeek = DayOfWeek.SUNDAY

// ---------------------------------------------------------------- Resumen general

data class HabitOverview(
    val habit: Habit,
    val summary: HabitSummary?,
    val quit: QuitSummary?,
)

data class Overview(
    val loaded: Boolean = false,
    val habitsDoneToday: Int = 0,
    val habitsDueToday: Int = 0,
    val tasksToday: Int = 0,
    val tasksWeek: Int = 0,
    val pendingTasks: Int = 0,
    /** % de hábitos diarios cumplidos cada día (últimos 14 días). */
    val dailyRate: List<ChartPoint> = emptyList(),
    val tasksDaily: List<ChartPoint> = emptyList(),
    val habits: List<HabitOverview> = emptyList(),
)

class StatsViewModel(db: AppDatabase) : ViewModel() {

    val state: StateFlow<Overview> = combine(
        db.habits().observeActive(),
        db.habits().observeAllLogs(),
        db.notes().observeAll(),
        db.tasks().observeAll(),
    ) { habits, logs, notes, tasks ->
        val today = LocalDate.now()
        val byHabit = logs.groupBy { it.habitId }
        val journal = HabitStats.journalLogs(notes)
        val logsOf = habits.associate { it.id to HabitStats.logsOf(it, byHabit, journal) }
        val totals = logsOf.mapValues { HabitStats.dayTotals(it.value) }
        val resets = habits.filter { it.type == TrackingType.QUIT }.associate { it.id to HabitStats.resetDays(logsOf[it.id].orEmpty()) }

        fun daily(h: Habit, d: LocalDate) =
            h.type != TrackingType.QUIT && h.period == GoalPeriod.DAY && d >= HabitStats.created(h) && HabitLogic.isScheduled(h, d)

        fun done(h: Habit, d: LocalDate) =
            HabitStats.cell(h, d, totals[h.id].orEmpty(), resets[h.id].orEmpty(), today).kind == CellKind.DONE

        val dueToday = habits.filter { daily(it, today) }
        val rate = (13 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            val due = habits.filter { daily(it, d) }
            val pct = if (due.isEmpty()) 0.0 else due.count { done(it, d) } * 100.0 / due.size
            ChartPoint(dayLabel(d), pct)
        }
        val doneDays = tasks.mapNotNull { t -> t.doneAt?.takeIf { t.done }?.let(HabitLogic::dateOf) }
        val tasksDaily = (13 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            ChartPoint(dayLabel(d), doneDays.count { it == d }.toDouble())
        }
        val weekStart = HabitLogic.weekStartOf(today, StatsWeekStart)
        Overview(
            loaded = true,
            habitsDoneToday = dueToday.count { done(it, today) },
            habitsDueToday = dueToday.size,
            tasksToday = doneDays.count { it == today },
            tasksWeek = doneDays.count { it >= weekStart },
            pendingTasks = tasks.count { !it.done },
            dailyRate = rate,
            tasksDaily = tasksDaily,
            habits = habits.map { h ->
                val l = logsOf[h.id].orEmpty()
                if (h.type == TrackingType.QUIT) HabitOverview(h, null, HabitStats.quitSummary(h, l))
                else HabitOverview(h, HabitStats.summary(h, l, StatsWeekStart, today), null)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Overview())
}

private fun dayLabel(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.NARROW, es).uppercase()

// ---------------------------------------------------------------- Detalle de un hábito

data class HabitDetail(
    val loaded: Boolean = false,
    val habit: Habit? = null,
    val logs: List<HabitLog> = emptyList(),
    val totals: Map<LocalDate, Double> = emptyMap(),
    val resets: Set<LocalDate> = emptySet(),
    val summary: HabitSummary? = null,
    val quit: QuitSummary? = null,
    val chart: List<ChartPoint> = emptyList(),
)

class HabitStatsViewModel(db: AppDatabase, habitId: Long) : ViewModel() {

    val state: StateFlow<HabitDetail> = combine(
        db.habits().observe(habitId),
        db.habits().observeLogsFor(habitId),
        db.notes().observeAll(),
    ) { h, rawLogs, notes ->
        if (h == null) return@combine HabitDetail(loaded = true)
        val logs = if (h.type == TrackingType.JOURNAL) HabitStats.journalLogs(notes)[h.id].orEmpty() else rawLogs
        val totals = HabitStats.dayTotals(logs)
        val today = LocalDate.now()
        HabitDetail(
            loaded = true,
            habit = h,
            logs = logs.sortedByDescending { it.timestamp },
            totals = totals,
            resets = if (h.type == TrackingType.QUIT) HabitStats.resetDays(logs) else emptySet(),
            summary = if (h.type == TrackingType.QUIT) null else HabitStats.summary(h, logs, StatsWeekStart, today),
            quit = if (h.type == TrackingType.QUIT) HabitStats.quitSummary(h, logs) else null,
            chart = if (h.type == TrackingType.QUIT) emptyList() else HabitStats.chart(h, totals, StatsWeekStart, today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitDetail())
}
