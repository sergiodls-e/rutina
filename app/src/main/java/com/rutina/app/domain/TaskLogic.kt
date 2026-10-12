package com.rutina.app.domain

import com.rutina.app.data.DateRange
import com.rutina.app.data.RepeatRule
import com.rutina.app.data.Task
import com.rutina.app.data.TaskFilter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reglas puras para tareas: repetición, filtros, agrupación y textos. */
object TaskLogic {

    private val es = Locale("es", "MX")

    fun due(task: Task): LocalDate? = task.dueEpochDay?.let(LocalDate::ofEpochDay)

    /** Siguiente fecha de una tarea que se repite (a partir de su fecha, o de hoy si no tenía). */
    fun nextDue(task: Task, today: LocalDate = LocalDate.now()): LocalDate? {
        val base = due(task) ?: today
        return when (task.repeat) {
            RepeatRule.NEVER -> null
            RepeatRule.DAILY -> base.plusDays(1)
            RepeatRule.WEEKLY -> base.plusWeeks(1)
            RepeatRule.MONTHLY -> base.plusMonths(1)
            RepeatRule.YEARLY -> base.plusYears(1)
            RepeatRule.WEEKDAYS -> {
                var d = base.plusDays(1)
                while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
                d
            }
        }
    }

    fun isOverdue(task: Task, today: LocalDate = LocalDate.now()): Boolean =
        !task.done && task.dueEpochDay != null && task.dueEpochDay < today.toEpochDay()

    fun matches(filter: TaskFilter, task: Task, today: LocalDate = LocalDate.now()): Boolean {
        if (task.done && !filter.includeDone) return false
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

    /** Agrupa para la vista "Todas las tareas". */
    fun group(tasks: List<Task>, today: LocalDate = LocalDate.now()): List<Pair<String, List<Task>>> {
        val t = today.toEpochDay()
        val pending = tasks.filter { !it.done }
        val groups = listOf(
            "Vencidas" to pending.filter { it.dueEpochDay != null && it.dueEpochDay < t },
            "Hoy" to pending.filter { it.dueEpochDay == t },
            "Mañana" to pending.filter { it.dueEpochDay == t + 1 },
            "Próximas" to pending.filter { it.dueEpochDay != null && it.dueEpochDay > t + 1 },
            "Sin fecha" to pending.filter { it.dueEpochDay == null },
            "Completadas" to tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }.take(50),
        )
        return groups.filter { it.second.isNotEmpty() }
    }

    fun repeatLabel(r: RepeatRule): String = when (r) {
        RepeatRule.NEVER -> "Nunca"
        RepeatRule.DAILY -> "Cada día"
        RepeatRule.WEEKDAYS -> "Entre semana (L-V)"
        RepeatRule.WEEKLY -> "Cada semana"
        RepeatRule.MONTHLY -> "Cada mes"
        RepeatRule.YEARLY -> "Cada año"
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
