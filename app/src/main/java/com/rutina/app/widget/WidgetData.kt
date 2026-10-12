package com.rutina.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.Habit
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.HabitStats
import com.rutina.app.domain.NoteLogic
import com.rutina.app.domain.TaskLogic
import com.rutina.app.domain.doneOn
import com.rutina.app.domain.todayLabel
import com.rutina.app.ui.common.cellColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    /** Avance 0..1 para la barrita. */
    val fraction: Float = 0f,
)

/** Fila del widget de tareas. */
data class WTask(
    val id: Long,
    val title: String,
    val color: Int,
    val due: String?,
    val overdue: Boolean,
)

/** Contenido de un widget de lista: título + filas. */
data class WList<T>(val title: String, val rows: List<T>)

/** Un hábito para el widget "Informe del hábito". */
data class WReport(val habit: WHabit?, val history: Bitmap?)

/** Contenido del widget "Nota del diario". */
data class WJournal(val tag: String, val tagColor: Int?, val habitId: Long?, val wordsToday: Int)

/**
 * Qué muestra cada widget colocado (se elige al añadirlo o al mantenerlo pulsado).
 * Se guarda en el teléfono por número de widget.
 */
object WidgetConfig {
    private const val FILE = "widgets"
    private val changes = MutableStateFlow(0)

    fun get(ctx: Context, id: Int, key: String, def: String): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("${key}_$id", null) ?: def

    fun set(ctx: Context, id: Int, values: Map<String, String>) {
        val e = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        values.forEach { (k, v) -> e.putString("${k}_$id", v) }
        e.apply()
        changes.value++
    }

    fun flow(ctx: Context, id: Int, key: String, def: String): Flow<String> =
        changes.map { get(ctx, id, key, def) }.distinctUntilChanged()
}

object WidgetData {

    private val weekStart: DayOfWeek get() = com.rutina.app.RutinaApp.instance.prefs.weekStart.value
    private val defaultTaskColor = 0xFF9FA8DA.toInt()

    private fun row(h: Habit, l: List<com.rutina.app.data.HabitLog>, today: LocalDate): WHabit {
        val p = HabitLogic.progress(h, l, today, weekStart)
        return WHabit(
            id = h.id,
            name = h.name,
            color = h.color,
            type = h.type,
            text = todayLabel(h, l, today, weekStart),
            met = doneOn(h, l, today, weekStart),
            fraction = when (h.type) {
                TrackingType.QUIT, TrackingType.WEIGHT, TrackingType.MOOD -> 0f
                else -> HabitLogic.fraction(h, p)
            },
        )
    }

    /** sel: "today" (los que tocan hoy), "all" o "l<id>" (una lista). */
    fun habits(db: AppDatabase, sel: String): Flow<WList<WHabit>> = combine(
        db.habits().observeActive(), db.habits().observeAllLogs(), db.notes().observeAll(), db.tasks().observeLists(),
    ) { habits, logs, notes, lists ->
        val today = LocalDate.now()
        val byHabit = logs.groupBy { it.habitId }
        val journal = HabitStats.journalLogs(notes)
        val listId = sel.removePrefix("l").toLongOrNull()?.takeIf { sel.startsWith("l") }
        val (title, shown) = when {
            sel == "all" -> "Hábitos" to habits
            listId != null -> (lists.firstOrNull { it.id == listId }?.name ?: "Lista") to habits.filter { it.listId == listId }
            else -> "Hábitos de hoy" to habits.filter { HabitLogic.isScheduled(it, today) }
        }
        WList(title, shown.map { h -> row(h, HabitStats.logsOf(h, byHabit, journal), today) })
    }

    /** sel: "today" (hoy y vencidas), "all", "l<id>" (lista) o "f<id>" (filtro). */
    fun tasks(db: AppDatabase, sel: String): Flow<WList<WTask>> = combine(
        db.tasks().observeAll(), db.tasks().observeLists(), db.tasks().observeFilters(),
    ) { tasks, lists, filters ->
        val today = LocalDate.now()
        val t = today.toEpochDay()
        val colors = lists.associate { it.id to it.color }
        val pending = TaskLogic.sorted(tasks.filter { !it.done }, com.rutina.app.data.TaskSortBy.DATE)
        val id = sel.drop(1).toLongOrNull()
        val (title, shown) = when {
            sel == "all" -> "Tareas" to pending
            sel.startsWith("l") && id != null -> (lists.firstOrNull { it.id == id }?.name ?: "Lista") to pending.filter { it.listId == id }
            sel.startsWith("f") && id != null -> {
                val f = filters.firstOrNull { it.id == id }
                (f?.name ?: "Filtro") to if (f == null) emptyList() else tasks.filter { TaskLogic.matches(f, it, today) && !it.done }
            }
            else -> {
                val due = pending.filter { it.dueEpochDay != null && it.dueEpochDay <= t }
                "Tareas" to due.ifEmpty { pending.filter { it.dueEpochDay == null || it.dueEpochDay <= t + 7 } }
            }
        }
        WList(
            title,
            shown.take(30).map {
                WTask(
                    id = it.id,
                    title = it.title,
                    color = it.listId?.let(colors::get) ?: defaultTaskColor,
                    due = TaskLogic.dueLabel(it, today),
                    overdue = TaskLogic.isOverdue(it, today),
                )
            },
        )
    }

    /** Un hábito con su mapa de las últimas semanas. */
    fun report(db: AppDatabase, habitId: Long?, history: Boolean): Flow<WReport> = combine(
        db.habits().observeActive(), db.habits().observeAllLogs(), db.notes().observeAll(),
    ) { habits, logs, notes ->
        val h = habits.firstOrNull { it.id == habitId } ?: return@combine WReport(null, null)
        val today = LocalDate.now()
        val l = HabitStats.logsOf(h, logs.groupBy { it.habitId }, HabitStats.journalLogs(notes))
        WReport(row(h, l, today), if (history) heatmap(h, l, today) else null)
    }

    /** Palabras escritas hoy (de las notas con esa etiqueta, o de todas). */
    fun journal(db: AppDatabase, habitId: Long?): Flow<WJournal> = combine(
        db.habits().observeAll(), db.notes().observeAll(),
    ) { habits, notes ->
        val h = habits.firstOrNull { it.id == habitId }
        val today = LocalDate.now()
        val words = notes.filter { HabitLogic.dateOf(it.timestamp) == today && (h == null || it.habitId == h.id) }
            .sumOf { NoteLogic.wordCount(it.content) }
        WJournal(h?.name ?: "Diario", h?.color, h?.id, words)
    }

    /** Dibuja 16 semanas × 7 días como imagen (los widgets no tienen lienzo propio). */
    private fun heatmap(h: Habit, logs: List<com.rutina.app.data.HabitLog>, today: LocalDate): Bitmap {
        val weeks = 16
        val cell = 22
        val gap = 5
        val bmp = Bitmap.createBitmap(weeks * (cell + gap), 7 * (cell + gap), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val totals = HabitStats.dayValues(h, logs)
        val resets = if (h.type == TrackingType.QUIT) HabitStats.resetDays(logs) else emptySet()
        val start = HabitLogic.weekStartOf(today, weekStart).minusWeeks((weeks - 1).toLong())
        for (w in 0 until weeks) for (d in 0 until 7) {
            val day = start.plusDays((w * 7 + d).toLong())
            p.color = cellColor(HabitStats.cell(h, day, totals, resets, today), Color(h.color)).toArgb()
            val x = (w * (cell + gap)).toFloat()
            val y = (d * (cell + gap)).toFloat()
            c.drawRoundRect(RectF(x, y, x + cell, y + cell), 5f, 5f, p)
        }
        return bmp
    }
}
