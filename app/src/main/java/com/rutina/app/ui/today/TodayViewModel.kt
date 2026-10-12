package com.rutina.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.GoalType
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.Note
import com.rutina.app.data.Task
import com.rutina.app.data.TaskOps
import com.rutina.app.data.TaskFilter
import com.rutina.app.data.TaskList
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.HabitStats
import com.rutina.app.domain.doneOn
import com.rutina.app.domain.todayLabel
import com.rutina.app.data.HabitDisplay
import com.rutina.app.data.Prefs
import com.rutina.app.domain.NoteLogic
import com.rutina.app.domain.TaskLogic
import com.rutina.app.domain.TaskGroup
import com.rutina.app.domain.GroupKind
import com.rutina.app.data.TaskGroupBy
import com.rutina.app.data.TaskSortBy
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
    /** Texto de estado: "3 / 8 vasos", "🙂 Bien", "72.4 kg (-0.6)". */
    val label: String = "",
    /** Total registrado por día (para las vistas semanal/mensual/anual). */
    val totals: Map<LocalDate, Double> = emptyMap(),
    /** Días con recaída (hábitos de Dejar). */
    val resets: Set<LocalDate> = emptySet(),
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
    /** Secciones de tareas. */
    val taskGroups: List<TaskGroup> = emptyList(),
    val groupBy: TaskGroupBy = TaskGroupBy.DATE,
    val sortBy: TaskSortBy = TaskSortBy.DATE,
    val lists: List<TaskList> = emptyList(),
    val filters: List<TaskFilter> = emptyList(),
    val todayCount: Int = 0,
    val pendingCount: Int = 0,
    val habitCount: Int = 0,
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    val loaded: Boolean = false,
) {
    val showsWeek get() = view == MainView.Today || view == MainView.AllHabits
    /** Vistas de tareas: se pueden agrupar y ordenar. */
    val isTaskView get() = view == MainView.AllTasks || view is MainView.OfList || view is MainView.OfFilter
    val isEmpty get() = loaded && habits.isEmpty() && taskGroups.isEmpty()
}

private data class Opts(val weekStart: DayOfWeek, val groupBy: TaskGroupBy, val sortBy: TaskSortBy)

private data class Base(
    val date: LocalDate,
    val view: MainView,
    val habits: List<Habit>,
    val logs: List<HabitLog>,
    val tasks: List<Task>,
)

class TodayViewModel(private val db: AppDatabase, private val prefs: Prefs) : ViewModel() {

    /** Vista de hábitos elegida en el menú: compacta, semanal, mensual o anual. */
    val display: StateFlow<HabitDisplay> = prefs.habitDisplay

    fun setDisplay(d: HabitDisplay) = prefs.setHabitDisplay(d)

    /** Entrada inteligente activada en Ajustes. */
    val smartInput: StateFlow<Boolean> = prefs.smartInput


    /** Inicio de semana elegido en Ajustes. */
    val weekStart: DayOfWeek get() = prefs.weekStart.value

    private val selected = MutableStateFlow(LocalDate.now())
    private val view = MutableStateFlow<MainView>(MainView.Today)

    private val base = combine(
        selected, view, db.habits().observeActive(), db.habits().observeAllLogs(), db.tasks().observeAll(),
    ) { d, v, h, l, t -> Base(d, v, h, l, t) }

    private val opts = combine(prefs.weekStart, prefs.taskGroupBy, prefs.taskSortBy) { w, g, o -> Opts(w, g, o) }

    val state: StateFlow<TodayState> = combine(
        base, db.tasks().observeLists(), db.tasks().observeFilters(), db.notes().observeAll(), opts,
    ) { b, lists, filters, notes, o -> build(b, lists, filters, notes, o) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState())

    private fun build(b: Base, lists: List<TaskList>, filters: List<TaskFilter>, notes: List<Note>, o: Opts): TodayState {
        val weekStart = o.weekStart
        fun grouped(tasks: List<Task>, today: LocalDate) = TaskLogic.group(tasks, today, o.groupBy, o.sortBy, lists)
        val today = LocalDate.now()
        val byHabit = b.logs.groupBy { it.habitId }
        // Hábitos de tipo Diario: cada nota etiquetada cuenta sus palabras como un registro.
        val journalLogs = notes.filter { it.habitId != null }.groupBy { it.habitId!! }.mapValues { (hid, ns) ->
            ns.map { HabitLog(habitId = hid, timestamp = it.timestamp, value = NoteLogic.wordCount(it.content).toDouble()) }
        }
        fun row(h: Habit): HabitRow {
            val hl = if (h.type == TrackingType.JOURNAL) journalLogs[h.id].orEmpty() else byHabit[h.id].orEmpty()
            val p = HabitLogic.progress(h, hl, b.date, weekStart)
            val todayMood = if (h.type == TrackingType.MOOD) {
                hl.filter { HabitLogic.dateOf(it.timestamp) == b.date }.map { it.value }.average().takeIf { !it.isNaN() }
            } else null
            return HabitRow(
                habit = h,
                progress = p,
                fraction = when (h.type) {
                    TrackingType.MOOD -> ((todayMood ?: 0.0) / 5.0).toFloat()
                    TrackingType.WEIGHT -> 0f
                    else -> HabitLogic.fraction(h, p)
                },
                met = doneOn(h, hl, b.date, weekStart),
                label = todayLabel(h, hl, b.date, weekStart),
                streak = if (h.type == TrackingType.QUIT) {
                    h.quitSince?.let { ChronoUnit.DAYS.between(HabitLogic.dateOf(it), today).toInt() } ?: 0
                } else HabitStats.summary(h, hl, weekStart, today).current,
                totals = HabitStats.dayValues(h, hl),
                resets = if (h.type == TrackingType.QUIT) HabitStats.resetDays(hl) else emptySet(),
            )
        }

        val epoch = b.date.toEpochDay()
        val overdue = if (b.date == today) TaskLogic.sorted(b.tasks.filter { TaskLogic.isOverdue(it, today) }, TaskSortBy.DATE) else emptyList()
        val dayPending = TaskLogic.sorted(b.tasks.filter { !it.done && it.dueEpochDay == epoch }, o.sortBy)
        val dayClosed = b.tasks.filter { it.done && it.doneAt?.let { d -> HabitLogic.dateOf(d) == b.date } == true }
            .sortedByDescending { it.doneAt ?: 0 }
        val todayTasks = overdue + dayPending + dayClosed

        var title = "Hoy"
        var habits = emptyList<HabitRow>()
        var groups = emptyList<TaskGroup>()
        when (val v = b.view) {
            MainView.Today -> {
                title = "Hoy"
                habits = b.habits.filter { HabitLogic.isScheduled(it, b.date) }.map(::row)
                groups = listOf(
                    TaskGroup("overdue", "Atrasadas", overdue, GroupKind.OVERDUE),
                    TaskGroup("pending", "Lista de pendientes", dayPending),
                    TaskGroup("closed", "Completadas", dayClosed, GroupKind.DONE),
                ).filter { it.tasks.isNotEmpty() }
            }
            MainView.AllHabits -> {
                title = "Todos los hábitos"
                habits = b.habits.map(::row)
            }
            MainView.AllTasks -> {
                title = "Todas las tareas"
                groups = grouped(b.tasks, today)
            }
            is MainView.OfList -> {
                title = lists.firstOrNull { it.id == v.id }?.name ?: "Lista"
                groups = grouped(b.tasks.filter { it.listId == v.id }, today)
            }
            is MainView.OfFilter -> {
                val f = filters.firstOrNull { it.id == v.id }
                title = f?.name ?: "Filtro"
                groups = if (f == null) emptyList()
                else grouped(b.tasks.filter { TaskLogic.matches(f, it, today) }, today)
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
            weekStart = weekStart,
            groupBy = o.groupBy,
            sortBy = o.sortBy,
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

    /** Registra en un día concreto (desde las vistas semanal/mensual). */
    fun addOn(habit: Habit, date: LocalDate) = viewModelScope.launch {
        db.habits().insertLog(HabitLog(habitId = habit.id, timestamp = stampFor(date), value = 1.0))
    }

    /** Quita el último registro de ese día. */
    fun undoOn(habit: Habit, date: LocalDate) = viewModelScope.launch {
        db.habits().deleteLatestLog(habit.id, HabitLogic.startOfDay(date), HabitLogic.startOfDay(date.plusDays(1)))
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
    fun toggleTask(task: Task) = viewModelScope.launch { TaskOps.toggle(db, task) }

    fun skipTask(task: Task) = viewModelScope.launch { TaskOps.skip(db, task) }

    fun setGroupBy(g: TaskGroupBy) = prefs.setTaskGroupBy(g)
    fun setSortBy(o: TaskSortBy) = prefs.setTaskSortBy(o)

    // ------------------------------------------------------------ varias tareas a la vez

    private fun selected(ids: Set<Long>, all: List<Task>) = all.filter { it.id in ids }

    private fun allTasks(): List<Task> = state.value.taskGroups.flatMap { it.tasks }

    fun completeMany(ids: Set<Long>) = viewModelScope.launch {
        selected(ids, allTasks()).filter { !it.done }.forEach { TaskOps.complete(db, it) }
    }

    fun skipMany(ids: Set<Long>) = viewModelScope.launch {
        selected(ids, allTasks()).filter { !it.done }.forEach { TaskOps.skip(db, it) }
    }

    fun moveMany(ids: Set<Long>, day: LocalDate?) = viewModelScope.launch {
        TaskOps.moveTo(db, selected(ids, allTasks()), day)
    }

    fun setListMany(ids: Set<Long>, listId: Long?) = viewModelScope.launch {
        db.tasks().updateAll(selected(ids, allTasks()).map { it.copy(listId = listId) })
    }

    fun setPriorityMany(ids: Set<Long>, p: Int) = viewModelScope.launch {
        db.tasks().updateAll(selected(ids, allTasks()).map { it.copy(priority = p) })
    }

    fun deleteMany(ids: Set<Long>) = viewModelScope.launch {
        db.tasks().deleteAll(selected(ids, allTasks()))
    }

    /** "Posponer": las atrasadas pasan a hoy. */
    fun postponeOverdue() = viewModelScope.launch {
        val today = LocalDate.now()
        TaskOps.moveTo(db, allTasks().filter { TaskLogic.isOverdue(it, today) }, today)
    }

    /** Orden manual: guarda el nuevo orden de una sección. */
    fun saveOrder(ordered: List<Task>) = viewModelScope.launch {
        db.tasks().updateAll(ordered.mapIndexed { i, t -> t.copy(sortOrder = i) })
    }
}
