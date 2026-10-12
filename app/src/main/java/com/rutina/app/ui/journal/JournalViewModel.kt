package com.rutina.app.ui.journal

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.Note
import com.rutina.app.data.Task
import com.rutina.app.data.TaskOps
import com.rutina.app.data.TaskList
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.NoteLogic
import com.rutina.app.domain.TaskLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class EntryKind(val label: String) {
    ALL("Todas las entradas"),
    NOTES("Notas"),
    TASKS("Actividad de tareas"),
    HABITS("Actividad de hábitos"),
}

/** Un elemento del historial del diario. */
sealed interface Entry {
    val time: Long
    val key: String
}

data class NoteEntry(val note: Note, val habit: Habit?) : Entry {
    override val time get() = note.timestamp
    override val key get() = "n${note.id}"
}

data class HabitEntry(val log: HabitLog, val habit: Habit) : Entry {
    override val time get() = log.timestamp
    override val key get() = "h${log.id}"
}

data class TaskEntry(val task: Task, val list: TaskList?) : Entry {
    override val time get() = task.doneAt ?: task.createdAt
    override val key get() = "t${task.id}"
}

data class JournalFilter(
    val kind: EntryKind = EntryKind.ALL,
    val tagId: Long? = null,
    val query: String = "",
    val searching: Boolean = false,
    val starredOnly: Boolean = false,
)

data class JournalState(
    val filter: JournalFilter = JournalFilter(),
    val entries: List<Entry> = emptyList(),
    val tags: List<Habit> = emptyList(),
    val loaded: Boolean = false,
)

private data class Source(
    val habits: List<Habit>,
    val logs: List<HabitLog>,
    val tasks: List<Task>,
    val lists: List<TaskList>,
    val notes: List<Note>,
)

private val es = Locale("es", "MX")

/** "11 oct, 11:21 p. m." (con año si no es el actual). */
fun stampLabel(millis: Long): String {
    val z = Instant.ofEpochMilli(millis).atZone(HabitLogic.zone)
    val month = z.month.getDisplayName(TextStyle.SHORT, es).replace(".", "")
    val year = if (z.year != LocalDate.now().year) " ${z.year}" else ""
    return "${z.dayOfMonth} $month$year, ${TaskLogic.timeLabel(z.hour * 60 + z.minute)}"
}

/** Texto del registro de un hábito: "1 vez", "35 min", "Reinició el contador". */
fun logLabel(habit: Habit, log: HabitLog): String = when (habit.type) {
    TrackingType.QUIT -> "Reinició el contador"
    else -> HabitLogic.amountLabel(log.value, habit.unit)
}

/** Nota escrita por el usuario en un registro (la marca interna de reinicio no se muestra). */
fun HabitLog.userNote(): String? = note?.takeIf { it.isNotBlank() && it != "reinicio" }

class JournalViewModel(private val db: AppDatabase) : ViewModel() {

    private val filter = MutableStateFlow(JournalFilter())

    private val source = combine(
        db.habits().observeAll(),
        db.habits().observeAllLogs(),
        db.tasks().observeAll(),
        db.tasks().observeLists(),
        db.notes().observeAll(),
    ) { h, l, t, ls, n -> Source(h, l, t, ls, n) }

    val state: StateFlow<JournalState> = combine(source, filter) { s, f -> build(s, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalState())

    private fun build(s: Source, f: JournalFilter): JournalState {
        val habitById = s.habits.associateBy { it.id }
        val listById = s.lists.associateBy { it.id }
        val q = f.query.trim().lowercase()
        fun hit(vararg texts: String?) = q.isEmpty() || texts.any { it?.lowercase()?.contains(q) == true }
        val out = mutableListOf<Entry>()

        if (f.kind == EntryKind.ALL || f.kind == EntryKind.NOTES) {
            for (n in s.notes) {
                if (f.starredOnly && !n.starred) continue
                if (f.tagId != null && n.habitId != f.tagId) continue
                val h = n.habitId?.let(habitById::get)
                if (!hit(n.content, h?.name)) continue
                out += NoteEntry(n, h)
            }
        }
        if (!f.starredOnly && (f.kind == EntryKind.ALL || f.kind == EntryKind.HABITS)) {
            for (l in s.logs) {
                val h = habitById[l.habitId] ?: continue
                if (f.tagId != null && l.habitId != f.tagId) continue
                if (!hit(h.name, l.userNote())) continue
                out += HabitEntry(l, h)
            }
        }
        if (!f.starredOnly && f.tagId == null && (f.kind == EntryKind.ALL || f.kind == EntryKind.TASKS)) {
            for (t in s.tasks) {
                if (!t.done || t.doneAt == null) continue
                if (!hit(t.title, t.description)) continue
                out += TaskEntry(t, t.listId?.let(listById::get))
            }
        }
        out.sortByDescending { it.time }
        return JournalState(f, out, s.habits.filter { !it.archived }, loaded = true)
    }

    // ------------------------------------------------------------ filtros

    fun setKind(k: EntryKind) = filter.update { it.copy(kind = k) }
    fun setTag(id: Long?) = filter.update { it.copy(tagId = id) }
    fun setQuery(q: String) = filter.update { it.copy(query = q) }
    fun setSearching(on: Boolean) = filter.update { it.copy(searching = on, query = if (on) it.query else "") }
    fun toggleStarredOnly() = filter.update { it.copy(starredOnly = !it.starredOnly) }

    // ------------------------------------------------------------ acciones

    fun toggleStar(n: Note) = viewModelScope.launch { db.notes().update(n.copy(starred = !n.starred)) }
    fun deleteNote(n: Note) = viewModelScope.launch { db.notes().delete(n) }
    fun toggleCheck(n: Note, line: Int) = viewModelScope.launch {
        db.notes().update(n.copy(content = NoteLogic.toggleCheck(n.content, line)))
    }

    fun saveLogNote(l: HabitLog, text: String) = viewModelScope.launch {
        // Las recaídas conservan su marca aunque se borre la nota.
        val fallback = if (l.note == "reinicio") "reinicio" else null
        db.habits().updateLog(l.copy(note = text.trim().ifBlank { fallback }))
    }

    /** Borra un registro. Si era la última recaída de un hábito de Dejar, el contador vuelve a la anterior. */
    fun deleteLog(l: HabitLog, habit: Habit) = viewModelScope.launch {
        db.habits().deleteLog(l)
        if (habit.type == TrackingType.QUIT && habit.quitSince == l.timestamp) {
            val prev = db.habits().lastLogTime(habit.id)
            db.habits().update(habit.copy(quitSince = prev ?: habit.createdAt))
        }
    }

    fun reopenTask(t: Task) = viewModelScope.launch { TaskOps.reopen(db, t) }

    // ------------------------------------------------------------ exportar

    /** Escribe lo que estás viendo (con los filtros actuales) como un archivo Markdown. */
    fun export(resolver: ContentResolver, uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val text = buildExport(state.value.entries)
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("sin acceso")
            }.isSuccess
        }
        onDone(ok)
    }

    private fun buildExport(entries: List<Entry>): String = buildString {
        appendLine("# Diario")
        appendLine("_Exportado el ${stampLabel(System.currentTimeMillis())} · ${entries.size} entradas_")
        var lastDay: LocalDate? = null
        for (e in entries.sortedBy { it.time }) {
            val z = Instant.ofEpochMilli(e.time).atZone(HabitLogic.zone)
            val day = z.toLocalDate()
            if (day != lastDay) {
                appendLine()
                appendLine("## ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, es)} ${day.year}")
                lastDay = day
            }
            val time = TaskLogic.timeLabel(z.hour * 60 + z.minute)
            appendLine()
            when (e) {
                is NoteEntry -> {
                    appendLine("**$time · Nota${e.habit?.let { " · ${it.name}" } ?: ""}${if (e.note.starred) " ★" else ""}**")
                    appendLine()
                    appendLine(e.note.content.trimEnd())
                }
                is HabitEntry -> {
                    appendLine("**$time · ${e.habit.name}:** ${logLabel(e.habit, e.log)}")
                    e.log.userNote()?.let { appendLine(); appendLine(it) }
                }
                is TaskEntry -> {
                    appendLine("**$time · Tarea completada:** ${e.task.title}${e.list?.let { " (${it.name})" } ?: ""}")
                    if (e.task.description.isNotBlank()) { appendLine(); appendLine(e.task.description) }
                }
            }
        }
    }
}
