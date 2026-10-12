package com.rutina.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.CalView
import com.rutina.app.data.Habit
import com.rutina.app.data.Prefs
import com.rutina.app.data.RepeatRule
import com.rutina.app.data.Task
import com.rutina.app.data.TaskList
import com.rutina.app.data.TimerSession
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.TaskLogic
import com.rutina.app.timer.TimerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

enum class EventKind { TASK, GHOST, SESSION }

/** Algo que se dibuja en el calendario. */
data class CalEvent(
    val key: String,
    val kind: EventKind,
    val day: LocalDate,
    /** Minutos desde medianoche; null = todo el día. */
    val startMin: Int?,
    val durMin: Int,
    val title: String,
    val color: Int,
    val done: Boolean = false,
    val task: Task? = null,
    val habitId: Long? = null,
) {
    /** Solo las tareas reales con hora se pueden arrastrar. */
    val movable get() = kind == EventKind.TASK && startMin != null && !done
}

/** Evento con su carril (para que los que se enciman se muestren lado a lado). */
data class Placed(val event: CalEvent, val lane: Int, val lanes: Int)

data class CalState(
    val view: CalView = CalView.WEEK,
    val anchor: LocalDate = LocalDate.now(),
    /** Columnas visibles (día, 3 días, semana) o 42 días del mes. */
    val days: List<LocalDate> = emptyList(),
    val timed: Map<LocalDate, List<Placed>> = emptyMap(),
    val allDay: Map<LocalDate, List<CalEvent>> = emptyMap(),
    val unscheduled: List<Task> = emptyList(),
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    val loaded: Boolean = false,
)

private data class Source(
    val tasks: List<Task>,
    val lists: List<TaskList>,
    val sessions: List<TimerSession>,
    val habits: List<Habit>,
)

private val DEFAULT_TASK_COLOR = 0xFF9FA8DA.toInt()
private val SESSION_COLOR = 0xFF90A4AE.toInt()

class CalendarViewModel(private val db: AppDatabase, private val prefs: Prefs) : ViewModel() {

    private val anchor = MutableStateFlow(LocalDate.now())

    private val source = combine(
        db.tasks().observeAll(), db.tasks().observeLists(), db.timers().observeAll(), db.habits().observeAll(),
    ) { t, l, s, h -> Source(t, l, s, h) }

    val state: StateFlow<CalState> = combine(anchor, prefs.calendarView, source, prefs.weekStart) { a, v, s, w -> build(a, v, s, w) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalState())

    private fun visibleDays(a: LocalDate, v: CalView, weekStart: DayOfWeek): List<LocalDate> = when (v) {
        CalView.DAY -> listOf(a)
        CalView.THREE -> (0..2).map { a.plusDays(it.toLong()) }
        CalView.WEEK -> HabitLogic.weekStartOf(a, weekStart).let { s -> (0..6).map { s.plusDays(it.toLong()) } }
        CalView.MONTH -> HabitLogic.weekStartOf(a.withDayOfMonth(1), weekStart).let { s -> (0 until 42).map { s.plusDays(it.toLong()) } }
    }

    private fun build(a: LocalDate, v: CalView, s: Source, weekStart: DayOfWeek): CalState {
        val days = visibleDays(a, v, weekStart)
        val from = days.first()
        val to = days.last()
        val listColor = s.lists.associate { it.id to it.color }
        val habits = s.habits.associateBy { it.id }
        val events = mutableListOf<CalEvent>()

        fun colorOf(t: Task) = t.listId?.let(listColor::get) ?: DEFAULT_TASK_COLOR

        for (t in s.tasks) {
            val due = t.dueEpochDay?.let(LocalDate::ofEpochDay) ?: continue
            if (due in from..to) {
                events += CalEvent("t${t.id}", EventKind.TASK, due, t.dueMinutes, t.durationMin, t.title, colorOf(t), t.done, t)
            }
            // Repeticiones futuras (aún no existen como tarea): se muestran atenuadas.
            if (!t.done && t.repeat != RepeatRule.NEVER) {
                var cur = t
                var i = 0
                while (i++ < 400) {
                    val next = TaskLogic.nextDue(cur) ?: break
                    if (next > to) break
                    cur = cur.copy(dueEpochDay = next.toEpochDay())
                    if (next >= from) {
                        events += CalEvent("g${t.id}-$next", EventKind.GHOST, next, t.dueMinutes, t.durationMin, t.title, colorOf(t), task = t)
                    }
                }
            }
        }
        for (ss in s.sessions) {
            val start = Instant.ofEpochMilli(ss.start).atZone(HabitLogic.zone)
            val day = start.toLocalDate()
            if (day !in from..to) continue
            val startMin = start.hour * 60 + start.minute
            val dur = ((ss.end - ss.start) / 60_000).toInt().coerceAtLeast(10).coerceAtMost(24 * 60 - startMin)
            val h = ss.habitId?.let(habits::get)
            events += CalEvent(
                "s${ss.id}", EventKind.SESSION, day, startMin, dur,
                "⏱ ${h?.name ?: "Concentración"} · ${TimerController.formatDuration(ss.activeMillis)}",
                h?.color ?: SESSION_COLOR, habitId = h?.id,
            )
        }

        val timed = events.filter { it.startMin != null }.groupBy { it.day }.mapValues { layout(it.value) }
        val allDay = events.filter { it.startMin == null }.groupBy { it.day }
        val unscheduled = s.tasks.filter { !it.done && it.dueMinutes == null }
            .sortedWith(compareBy({ it.dueEpochDay == null }, { it.dueEpochDay }, { -it.priority }))
        return CalState(v, a, days, timed, allDay, unscheduled, weekStart, loaded = true)
    }

    /** Reparte en carriles los eventos que se enciman. */
    private fun layout(list: List<CalEvent>): List<Placed> {
        val sorted = list.sortedWith(compareBy({ it.startMin }, { -it.durMin }))
        val out = mutableListOf<Placed>()
        var group = mutableListOf<Pair<CalEvent, Int>>()
        val laneEnds = mutableListOf<Int>()
        var groupEnd = -1
        fun flush() {
            val n = laneEnds.size.coerceAtLeast(1)
            group.forEach { (e, lane) -> out += Placed(e, lane, n) }
            group = mutableListOf()
            laneEnds.clear()
        }
        for (e in sorted) {
            val start = e.startMin ?: 0
            val end = start + e.durMin.coerceAtLeast(15)
            if (start >= groupEnd && group.isNotEmpty()) flush()
            var lane = laneEnds.indexOfFirst { it <= start }
            if (lane < 0) { laneEnds += end; lane = laneEnds.lastIndex } else laneEnds[lane] = end
            group += e to lane
            groupEnd = maxOf(groupEnd, end)
        }
        flush()
        return out
    }

    // ------------------------------------------------------------ navegación

    fun setView(v: CalView) = prefs.setCalendarView(v)

    fun goTo(d: LocalDate) { anchor.value = d }

    fun today() { anchor.value = LocalDate.now() }

    fun step(forward: Boolean) {
        val s = state.value
        val sign = if (forward) 1L else -1L
        anchor.value = when (s.view) {
            CalView.MONTH -> s.anchor.plusMonths(sign)
            CalView.WEEK -> s.anchor.plusWeeks(sign)
            else -> s.anchor.plusDays(sign * s.view.days)
        }
    }

    // ------------------------------------------------------------ cambios

    fun move(task: Task, day: LocalDate, minute: Int) = viewModelScope.launch {
        db.tasks().update(task.copy(dueEpochDay = day.toEpochDay(), dueMinutes = minute.coerceIn(0, 24 * 60 - 15)))
    }

    fun schedule(task: Task, day: LocalDate, minute: Int) = move(task, day, minute)
}
