package com.rutina.app.domain

import com.rutina.app.data.DateRange
import com.rutina.app.data.RepeatRule
import com.rutina.app.data.Task
import com.rutina.app.data.TaskFilter
import com.rutina.app.data.TaskGroupBy
import com.rutina.app.data.TaskList
import com.rutina.app.data.TaskSortBy
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class GroupKind { NORMAL, OVERDUE, DONE }

/** Sección de tareas en una vista. */
data class TaskGroup(val key: String, val title: String, val tasks: List<Task>, val kind: GroupKind = GroupKind.NORMAL)

/** Reglas puras para tareas: repetición, filtros, agrupación y textos. */
object TaskLogic {

    private val es = Locale("es", "MX")

    fun due(task: Task): LocalDate? = task.dueEpochDay?.let(LocalDate::ofEpochDay)

    /** Siguiente fecha de una tarea que se repite (a partir de su fecha, o de hoy si no tenía). */
    fun nextDue(task: Task, today: LocalDate = LocalDate.now()): LocalDate? {
        val base = due(task) ?: today
        val every = task.repeatEvery.coerceAtLeast(1).toLong()
        val next = when (task.repeat) {
            RepeatRule.NEVER -> null
            RepeatRule.DAILY -> base.plusDays(every)
            RepeatRule.WEEKLY -> if (task.repeatDays == 0) base.plusWeeks(every) else nextWeekday(base, task.repeatDays, every)
            RepeatRule.MONTHLY -> base.plusMonths(every)
            RepeatRule.YEARLY -> base.plusYears(every)
            RepeatRule.WEEKDAYS -> {
                var d = base.plusDays(1)
                while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
                d
            }
        } ?: return null
        val until = task.repeatUntil
        return if (until != null && next.toEpochDay() > until) null else next
    }

    /** Siguiente día marcado (bits lunes..domingo), saltando semanas según "cada N semanas". */
    private fun nextWeekday(base: LocalDate, days: Int, every: Long): LocalDate {
        val baseWeek = base.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var d = base.plusDays(1)
        repeat(7 * every.toInt() + 7) {
            val week = d.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeks = java.time.temporal.ChronoUnit.WEEKS.between(baseWeek, week)
            if (days and (1 shl (d.dayOfWeek.value - 1)) != 0 && weeks % every == 0L) return d
            d = d.plusDays(1)
        }
        return base.plusWeeks(every)
    }

    fun isOverdue(task: Task, today: LocalDate = LocalDate.now()): Boolean =
        !task.done && task.dueEpochDay != null && task.dueEpochDay < today.toEpochDay()

    fun matches(filter: TaskFilter, task: Task, today: LocalDate = LocalDate.now()): Boolean {
        if (task.done && !filter.includeDone) return false
        if (filter.text.isNotBlank() && !task.title.contains(filter.text.trim(), ignoreCase = true)) return false
        if (filter.minPriority > 0 && task.priority < filter.minPriority) return false
        if (filter.listId != null && task.listId != filter.listId) return false
        val d = task.dueEpochDay
        val t = today.toEpochDay()
        return when (filter.dateRange) {
            DateRange.ANY -> true
            DateRange.OVERDUE -> d != null && d < t
            DateRange.TODAY -> d != null && d <= t
            DateRange.NEXT_7_DAYS -> d != null && d <= t + 7
            DateRange.NO_DATE -> d == null
        }
    }

    /** Agrupa para las vistas de tareas según lo elegido (fecha, lista, prioridad o nada). */
    fun group(
        tasks: List<Task>,
        today: LocalDate = LocalDate.now(),
        by: TaskGroupBy = TaskGroupBy.DATE,
        sort: TaskSortBy = TaskSortBy.DATE,
        lists: List<TaskList> = emptyList(),
    ): List<TaskGroup> {
        val t = today.toEpochDay()
        val pending = sorted(tasks.filter { !it.done }, sort)
        val pendingGroups: List<TaskGroup> = when (by) {
            TaskGroupBy.DATE -> listOf(
                TaskGroup("overdue", "Vencidas", pending.filter { it.dueEpochDay != null && it.dueEpochDay < t }, GroupKind.OVERDUE),
                TaskGroup("today", "Hoy", pending.filter { it.dueEpochDay == t }),
                TaskGroup("tomorrow", "Mañana", pending.filter { it.dueEpochDay == t + 1 }),
                TaskGroup("later", "Próximas", pending.filter { it.dueEpochDay != null && it.dueEpochDay > t + 1 }),
                TaskGroup("nodate", "Sin fecha", pending.filter { it.dueEpochDay == null }),
            )
            TaskGroupBy.LIST -> lists.map { l -> TaskGroup("l${l.id}", l.name, pending.filter { it.listId == l.id }) } +
                TaskGroup("l0", "Sin lista", pending.filter { it.listId == null || lists.none { l -> l.id == it.listId } })
            TaskGroupBy.PRIORITY -> (3 downTo 0).map { p ->
                TaskGroup("p$p", priorityLabel(p), pending.filter { it.priority == p })
            }
            TaskGroupBy.NONE -> listOf(TaskGroup("all", "Pendientes", pending))
        }
        val closed = tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }
        val groups = pendingGroups + listOf(
            TaskGroup("done", "Completadas", closed.filter { !it.skipped }.take(50), GroupKind.DONE),
            TaskGroup("skipped", "Omitidas", closed.filter { it.skipped }.take(50), GroupKind.DONE),
        )
        return groups.filter { it.tasks.isNotEmpty() }
    }

    fun sorted(tasks: List<Task>, sort: TaskSortBy): List<Task> = when (sort) {
        TaskSortBy.DATE -> tasks.sortedWith(
            compareBy<Task>({ it.dueEpochDay == null }, { it.dueEpochDay }, { it.dueMinutes == null }, { it.dueMinutes }, { -it.priority }, { it.id })
        )
        TaskSortBy.PRIORITY -> tasks.sortedWith(compareBy<Task>({ -it.priority }, { it.dueEpochDay == null }, { it.dueEpochDay }, { it.id }))
        TaskSortBy.TITLE -> tasks.sortedBy { it.title.lowercase() }
        TaskSortBy.CREATED -> tasks.sortedByDescending { it.createdAt }
        TaskSortBy.MANUAL -> tasks.sortedWith(compareBy<Task>({ it.sortOrder }, { it.id }))
    }

    fun repeatLabel(r: RepeatRule): String = when (r) {
        RepeatRule.NEVER -> "Nunca"
        RepeatRule.DAILY -> "Cada día"
        RepeatRule.WEEKDAYS -> "Entre semana (L-V)"
        RepeatRule.WEEKLY -> "Cada semana"
        RepeatRule.MONTHLY -> "Cada mes"
        RepeatRule.YEARLY -> "Cada año"
    }

    private val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D")

    /** "Cada 2 semanas: L, X, V · hasta 30 dic". */
    fun repeatLabel(task: Task): String {
        if (task.repeat == RepeatRule.NEVER) return "Nunca"
        val n = task.repeatEvery.coerceAtLeast(1)
        val base = when (task.repeat) {
            RepeatRule.DAILY -> if (n == 1) "Cada día" else "Cada $n días"
            RepeatRule.WEEKLY -> if (n == 1) "Cada semana" else "Cada $n semanas"
            RepeatRule.MONTHLY -> if (n == 1) "Cada mes" else "Cada $n meses"
            RepeatRule.YEARLY -> if (n == 1) "Cada año" else "Cada $n años"
            else -> repeatLabel(task.repeat)
        }
        val days = if (task.repeat == RepeatRule.WEEKLY && task.repeatDays != 0) {
            ": " + (0..6).filter { task.repeatDays and (1 shl it) != 0 }.joinToString(", ") { dayLetters[it] }
        } else ""
        val until = task.repeatUntil?.let {
            " · hasta " + LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", es)).replace(".", "")
        } ?: ""
        return base + days + until
    }

    fun priorityLabel(p: Int): String = when (p) {
        3 -> "Alta"; 2 -> "Media"; 1 -> "Baja"; else -> "Sin prioridad"
    }

    fun rangeLabel(r: DateRange): String = when (r) {
        DateRange.ANY -> "Cualquier fecha"
        DateRange.OVERDUE -> "Vencidas"
        DateRange.TODAY -> "Hoy y vencidas"
        DateRange.NEXT_7_DAYS -> "Próximos 7 días"
        DateRange.NO_DATE -> "Sin fecha"
    }

    fun timeLabel(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val ampm = if (h < 12) "a. m." else "p. m."
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return "%d:%02d %s".format(h12, m, ampm)
    }

    /** "Hoy", "Mañana", "Ayer", "lun 14 oct" o "14 oct 2027" (+ hora si tiene). */
    fun dueLabel(task: Task, today: LocalDate = LocalDate.now()): String? {
        val d = due(task) ?: return null
        val day = when (d) {
            today -> "Hoy"
            today.plusDays(1) -> "Mañana"
            today.minusDays(1) -> "Ayer"
            else -> d.format(
                DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "d MMM yyyy", es)
            ).replace(".", "")
        }
        return task.dueMinutes?.let { "$day ${timeLabel(it)}" } ?: day
    }
}
