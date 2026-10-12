package com.rutina.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.Task
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class HabitRow(
    val habit: Habit,
    val progress: Double,
    val fraction: Float,
    val met: Boolean,
    val streak: Int,
)

data class TodayState(
    val date: LocalDate = LocalDate.now(),
    val habits: List<HabitRow> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val loaded: Boolean = false,
)

class TodayViewModel(private val db: AppDatabase) : ViewModel() {

    // TODO Fase 9: leer de Ajustes.
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY

    private val selected = MutableStateFlow(LocalDate.now())

    val state: StateFlow<TodayState> = combine(
        selected,
        db.habits().observeActive(),
        db.habits().observeAllLogs(),
        db.tasks().observeAll(),
    ) { date, habits, logs, tasks ->
        val byHabit = logs.groupBy { it.habitId }
        val today = LocalDate.now()
        val rows = habits
            .filter { HabitLogic.isScheduled(it, date) }
            .map { h ->
                val hl = byHabit[h.id].orEmpty()
                val p = HabitLogic.progress(h, hl, date, weekStart)
                HabitRow(
                    habit = h,
                    progress = p,
                    fraction = HabitLogic.fraction(h, p),
                    met = h.type != TrackingType.QUIT && HabitLogic.isMet(h, p) &&
                        h.goalType != com.rutina.app.data.GoalType.NEGATIVE,
                    streak = if (h.type == TrackingType.QUIT) {
                        h.quitSince?.let { java.time.temporal.ChronoUnit.DAYS.between(HabitLogic.dateOf(it), today).toInt() } ?: 0
                    } else HabitLogic.streak(h, hl, today, weekStart),
                )
            }
        val epoch = date.toEpochDay()
        val dayTasks = tasks.filter { t ->
            val due = t.dueEpochDay
            if (t.done) t.doneAt?.let { HabitLogic.dateOf(it) == date } == true
            else due == null || due <= epoch
        }
        TodayState(date, rows, dayTasks, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState())

    fun select(date: LocalDate) { selected.value = date }

    /** Marca de tiempo para registrar en el día seleccionado (hora actual si es hoy). */
    private fun stampFor(date: LocalDate): Long =
        if (date == LocalDate.now()) System.currentTimeMillis()
        else date.atTime(LocalTime.NOON).atZone(HabitLogic.zone).toInstant().toEpochMilli()

    fun add(habit: Habit, amount: Double = 1.0) = viewModelScope.launch {
        db.habits().insertLog(HabitLog(habitId = habit.id, timestamp = stampFor(selected.value), value = amount))
    }

    fun undo(habit: Habit) = viewModelScope.launch {
        val (a, b) = HabitLogic.periodRange(habit.period, selected.value, weekStart)
        db.habits().deleteLatestLog(habit.id, HabitLogic.startOfDay(a), HabitLogic.startOfDay(b))
    }

    /** Recaída en un hábito de "Dejar": se registra y el contador vuelve a cero. */
    fun resetQuit(habit: Habit) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        db.habits().insertLog(HabitLog(habitId = habit.id, timestamp = now, value = 1.0, note = "reinicio"))
        db.habits().update(habit.copy(quitSince = now))
    }

    fun addTask(title: String, description: String, today: Boolean) = viewModelScope.launch {
        db.tasks().insert(
            Task(
                title = title.trim(),
                description = description.trim(),
                dueEpochDay = if (today) selected.value.toEpochDay() else null,
            )
        )
    }

    fun toggleTask(task: Task) = viewModelScope.launch {
        db.tasks().update(
            task.copy(done = !task.done, doneAt = if (task.done) null else System.currentTimeMillis())
        )
    }
}
