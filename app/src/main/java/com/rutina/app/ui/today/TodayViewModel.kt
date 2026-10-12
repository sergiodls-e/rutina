package com.rutina.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.GoalType
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.Note
import com.rutina.app.data.Task
import com.rutina.app.data.TaskFilter
import com.rutina.app.data.TaskList
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.NoteLogic
import com.rutina.app.domain.TaskLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class HabitRow(
    val habit: Habit,
    val progress: Double,
    val fraction: Float,
    val met: Boolean,
    val streak: Int,
)

/** Qué se muestra en la pantalla principal (se elige desde el menú lateral). */
sealed interface MainView {
    data object Today : MainView
    data object AllTasks : MainView
    data object AllHabits : MainView
    data class OfList(val id: Long) : MainView
    data class OfFilter(val id: Long) : MainView
    data class Search(val query: String) : MainView
}

data class TodayState(
    val view: MainView = MainView.Today,
    val title: String = "Hoy",
    val date: LocalDate = LocalDate.now(),
    val habits: List<HabitRow> = emptyList(),
    /** Secciones de tareas: título → tareas. */
    val taskGroups: List<Pair<String, List<Task>>> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val filters: List<TaskFilter> = emptyList(),
    val todayCount: Int = 0,
    val pendingCount: Int = 0,
    val habitCount: Int = 0,
    val loaded: Boolean = false,
) {
    val showsWeek get() = view == MainView.Today || view == MainView.AllHabits
    val isEmpty get() = loaded && habits.isEmpty() && taskGroups.isEmpty()
}

private data class Base(
    val date: LocalDate,
    val view: MainView,
    val habits: List<Habit>,
    val logs: List<HabitLog>,
    val tasks: List<Task>,
)

class TodayViewModel(private val db: AppDatabase) : ViewModel() {

    // TODO Fase 9: leer de Ajustes.
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY

    private val selected = MutableStateFlow(LocalDate.now())
    private val view = MutableStateFlow<MainView>(MainView.Today)

    private val base = combine(
        selected, view, db.habits().observeActive(), db.habits().observeAllLogs(), db.tasks().observeAll(),
    ) { d, v, h, l, t -> Base(d, v, h, l, t) }

    val state: StateFlow<TodayState> = combine(
        base, db.tasks().observeLists(), db.tasks().observeFilters(), db.notes().observeAll(),
    ) { b, lists, filters, notes -> build(b, lists, filters, notes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState())

    private fun build(b: Base, lists: List<TaskList>, filters: List<TaskFilter>, notes: List<Note>): TodayState {
        val today = LocalDate.now()
        val byHabit = b.logs.groupBy { it.habitId }
        // Hábitos de tipo Diario: cada nota etiquetada cuenta sus palabras como un registro.
        val journalLogs = notes.filter { it.habitId != null }.groupBy { it.habitId!! }.mapValues { (hid, ns) ->
            ns.map { HabitLog(habitId = hid, timestamp = it.timestamp, value = NoteLogic.wordCount(it.content).toDouble()) }
        }
        fun row(h: Habit): HabitRow {
            val hl = if (h.type == TrackingType.JOURNAL) journalLogs[h.id].orEmpty() else byHabit[h.id].orEmpty()
            val p = HabitLogic.progress(h, hl, b.date, weekStart)
            return HabitRow(
                habit = h,
                progress = p,
                fraction = HabitLogic.fraction(h, p),
                met = h.type != TrackingType.QUIT && h.goalType != GoalType.NEGATIVE && HabitLogic.isMet(h, p),
                streak = if (h.type == TrackingType.QUIT) {
                    h.quitSince?.let { ChronoUnit.DAYS.between(HabitLogic.dateOf(it), today).toInt() } ?: 0
                } else HabitLogic.streak(h, hl, today, weekStart),
            )
        }

        val epoch = b.date.toEpochDay()
        val todayTasks = b.tasks.filter { t ->
            if (t.done) t.doneAt?.let { HabitLogic.dateOf(it) == b.date } == true
            else t.dueEpochDay == epoch || (b.date == today && TaskLogic.isOverdue(t, today))
        }

        var title = "Hoy"
        var habits = emptyList<HabitRow>()
        var groups = emptyList<Pair<String, List<Task>>>()
        when (val v = b.view) {
            MainView.Today -> {
                title = "Hoy"
                habits = b.habits.filter { HabitLogic.isScheduled(it, b.date) }.map(::row)
                if (todayTasks.isNotEmpty()) groups = listOf("Lista de pendientes" to todayTasks)
            }
            MainView.AllHabits -> {
                title = "Todos los hábitos"
                habits = b.habits.map(::row)
            }
            MainView.AllTasks -> {
                title = "Todas las tareas"
                groups = TaskLogic.group(b.tasks, today)
            }
            is MainView.OfList -> {
                title = lists.firstOrNull { it.id == v.id }?.name ?: "Lista"
                groups = TaskLogic.group(b.tasks.filter { it.listId == v.id }, today)
            }
            is MainView.OfFilter -> {
                val f = filters.firstOrNull { it.id == v.id }
                title = f?.name ?: "Filtro"
                groups = if (f == null) emptyList()
                else TaskLogic.group(b.tasks.filter { TaskLogic.matches(f, it, today) }, today)
            }
            is MainView.Search -> {
                title = "Buscar"
                val q = v.query.trim().lowercase()
                if (q.isNotEmpty()) {
                    habits = b.habits.filter { it.name.lowercase().contains(q) }.map(::row)
                    groups = TaskLogic.group(
                        b.tasks.filter { it.title.lowercase().contains(q) || it.description.lowercase().contains(q) },
                        today,
                    )
                }
            }
        }

        return TodayState(
            view = b.view,
            title = title,
            date = b.date,
            habits = habits,
            taskGroups = groups,
            lists = lists,
            filters = filters,
            todayCount = todayTasks.count { !it.done },
            pendingCount = b.tasks.count { !it.done },
            habitCount = b.habits.size,
            loaded = true,
        )
    }

    fun select(date: LocalDate) { selected.value = date }

    fun show(v: MainView) { view.value = v }

    // ------------------------------------------------------------ hábitos

    /** Marca de tiempo para registrar en el día seleccionado (hora actual si es hoy). */
    private fun stampFor(date: LocalDate): Long =
        if (date == LocalDate.now()) System.currentTimeMillis()
        else date.atTime(LocalTime.NOON).atZone(HabitLogic.zone).toInstant().toEpochMilli()

    fun add(habit: Habit, amount: Double = 1.0, note: String? = null) = viewModelScope.launch {
        db.habits().insertLog(HabitLog(habitId = habit.id, timestamp = stampFor(selected.value), value = amount, note = note))
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

    // ------------------------------------------------------------ tareas

    fun addTask(task: Task) = viewModelScope.launch { db.tasks().insert(task) }

    /** Lista activa (si se está viendo una), para asignarla a tareas nuevas. */
    fun currentListId(): Long? = (view.value as? MainView.OfList)?.id

    fun selectedDate(): LocalDate = selected.value

    /**
     * Completar una tarea que se repite la deja en el historial como hecha
     * y crea la siguiente ocurrencia.
     */
    fun toggleTask(task: Task) = viewModelScope.launch {
        if (task.done) {
            db.tasks().update(task.copy(done = false, doneAt = null))
            return@launch
        }
        db.tasks().update(task.copy(done = true, doneAt = System.currentTimeMillis()))
        TaskLogic.nextDue(task)?.let { next ->
            db.tasks().insert(
                task.copy(id = 0, done = false, doneAt = null, dueEpochDay = next.toEpochDay(), createdAt = System.currentTimeMillis())
            )
        }
    }
}
