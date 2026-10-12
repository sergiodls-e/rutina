package com.rutina.app.widget

import com.rutina.app.data.AppDatabase
import com.rutina.app.data.GoalType
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.HabitStats
import com.rutina.app.domain.TaskLogic
import com.rutina.app.domain.doneOn
import com.rutina.app.domain.todayLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate

/** Fila del widget de hábitos. */
data class WHabit(
    val id: Long,
    val name: String,
    val color: Int,
    val type: TrackingType,
    val text: String,
    val met: Boolean,
)

/** Fila del widget de tareas. */
data class WTask(
    val id: Long,
    val title: String,
    val color: Int,
    val due: String?,
    val overdue: Boolean,
)

object WidgetData {

    private val weekStart = DayOfWeek.SUNDAY
    private val defaultTaskColor = 0xFF9FA8DA.toInt()

    /** Hábitos que tocan hoy, con su progreso. */
    fun habits(db: AppDatabase): Flow<List<WHabit>> = combine(
        db.habits().observeActive(), db.habits().observeAllLogs(), db.notes().observeAll(),
    ) { habits, logs, notes ->
        val today = LocalDate.now()
        val byHabit = logs.groupBy { it.habitId }
        val journal = HabitStats.journalLogs(notes)
        habits.filter { HabitLogic.isScheduled(it, today) }.map { h ->
            val l = HabitStats.logsOf(h, byHabit, journal)
            val p = HabitLogic.progress(h, l, today, weekStart)
            WHabit(
                id = h.id,
                name = h.name,
                color = h.color,
                type = h.type,
                text = todayLabel(h, l, today, weekStart),
                met = doneOn(h, l, today, weekStart),
            )
        }
    }

    /** Tareas pendientes de hoy y vencidas (y, si no hay, las próximas). */
    fun tasks(db: AppDatabase): Flow<List<WTask>> = combine(
        db.tasks().observeAll(), db.tasks().observeLists(),
    ) { tasks, lists ->
        val today = LocalDate.now()
        val t = today.toEpochDay()
        val colors = lists.associate { it.id to it.color }
        val pending = tasks.filter { !it.done }
        val due = pending.filter { it.dueEpochDay != null && it.dueEpochDay <= t }
        val shown = due.ifEmpty { pending.filter { it.dueEpochDay == null || it.dueEpochDay <= t + 7 } }
        shown.take(30).map {
            WTask(
                id = it.id,
                title = it.title,
                color = it.listId?.let(colors::get) ?: defaultTaskColor,
                due = TaskLogic.dueLabel(it, today),
                overdue = TaskLogic.isOverdue(it, today),
            )
        }
    }
}
