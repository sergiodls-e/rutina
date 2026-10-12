package com.rutina.app.domain

import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.Note
import com.rutina.app.data.Task
import com.rutina.app.data.TimerSession
import com.rutina.app.data.TrackingType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Un total de toda la vida con sus hitos (1, 10, 50, 100…). */
data class Milestone(
    val key: String,
    val title: String,
    val emoji: String,
    val value: Long,
    /** Cómo se muestra el valor ("1 234", "12 h"). */
    val display: String,
    val thresholds: List<Long>,
    /** Qué contribuye a este total. */
    val detail: String,
    val unit: String,
) {
    val reached: List<Long> get() = thresholds.filter { value >= it }
    val next: Long? get() = thresholds.firstOrNull { value < it }
    val previous: Long get() = reached.lastOrNull() ?: 0L
    /** Avance hacia el siguiente hito (0..1). */
    val progress: Float
        get() = next?.let { n -> ((value - previous).toFloat() / (n - previous)).coerceIn(0f, 1f) } ?: 1f
}

data class Record(val title: String, val value: String, val detail: String)

data class StreakRecord(val habit: Habit, val best: Int, val current: Int, val unit: String)

data class AchievementsData(
    val since: LocalDate?,
    val milestones: List<Milestone>,
    val records: List<Record>,
    val streaks: List<StreakRecord>,
) {
    val badges: Int get() = milestones.sumOf { it.reached.size }
}

object Achievements {
    private val es = Locale("es", "MX")
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", es)

    private val counts = listOf(1L, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000)
    private val words = listOf(100L, 500, 1_000, 5_000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000)
    private val hours = listOf(1L, 5, 10, 25, 50, 100, 250, 500, 1_000)
    private val days = listOf(7L, 30, 60, 100, 180, 365, 730, 1_000, 1_825)

    fun compute(
        habits: List<Habit>,
        logs: List<HabitLog>,
        notes: List<Note>,
        tasks: List<Task>,
        sessions: List<TimerSession>,
        weekStart: DayOfWeek,
        today: LocalDate = LocalDate.now(),
    ): AchievementsData {
        val byHabit = logs.groupBy { it.habitId }
        val journal = HabitStats.journalLogs(notes)
        val types = habits.associate { it.id to it.type }

        // Registros reales hechos por ti (sin recaídas ni lecturas automáticas de Health Connect).
        val manualLogs = logs.filter { types[it.habitId] != TrackingType.QUIT && it.note != "health" }
        val done = tasks.filter { it.done && !it.skipped }
        val wordCounts = notes.map { it to NoteLogic.wordCount(it.content) }
        val totalWords = wordCounts.sumOf { it.second.toLong() }
        val focusMs = sessions.sumOf { it.activeMillis }

        val firstUse = listOfNotNull(
            habits.minOfOrNull { it.createdAt },
            tasks.minOfOrNull { it.createdAt },
            notes.minOfOrNull { it.timestamp },
            logs.minOfOrNull { it.timestamp },
        ).minOrNull()?.let(HabitLogic::dateOf)
        val daysUsing = firstUse?.let { ChronoUnit.DAYS.between(it, today) + 1 } ?: 0L
        val activeDays = (manualLogs.map { HabitLogic.dateOf(it.timestamp) } +
            done.mapNotNull { it.doneAt?.let(HabitLogic::dateOf) } +
            notes.map { HabitLogic.dateOf(it.timestamp) }).toSet().size.toLong()

        val milestones = listOf(
            Milestone("days", "Días con Rutina", "📅", daysUsing, fmt(daysUsing), days, "Días desde tu primer registro.", "días"),
            Milestone("active", "Días activos", "🔥", activeDays, fmt(activeDays), days, "Días en que registraste un hábito, completaste una tarea o escribiste una nota.", "días"),
            Milestone("logs", "Registros de hábitos", "✅", manualLogs.size.toLong(), fmt(manualLogs.size.toLong()), counts, "Cada vez que sumas progreso a un hábito (incluye ánimo y peso).", "registros"),
            Milestone("tasks", "Tareas completadas", "☑️", done.size.toLong(), fmt(done.size.toLong()), counts, "Tareas marcadas como hechas, incluidas las repeticiones.", "tareas"),
            Milestone("notes", "Notas escritas", "📝", notes.size.toLong(), fmt(notes.size.toLong()), counts, "Notas de tu diario, con o sin etiqueta.", "notas"),
            Milestone("words", "Palabras escritas", "✍️", totalWords, fmt(totalWords), words, "Suma de palabras de todas tus notas.", "palabras"),
            Milestone("focus", "Horas de concentración", "⏱", focusMs / 3_600_000, formatHours(focusMs), hours, "Tiempo activo de tus sesiones de temporizador.", "horas"),
        )

        val records = buildList {
            wordCounts.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.let { (n, w) ->
                add(Record("Nota más larga", "${fmt(w.toLong())} palabras", HabitLogic.dateOf(n.timestamp).format(dayFmt)))
            }
            wordCounts.groupBy { HabitLogic.dateOf(it.first.timestamp) }
                .mapValues { (_, l) -> l.sumOf { it.second } }
                .maxByOrNull { it.value }?.takeIf { it.value > 0 }
                ?.let { add(Record("Día con más palabras", "${fmt(it.value.toLong())} palabras", it.key.format(dayFmt))) }
            done.mapNotNull { it.doneAt?.let(HabitLogic::dateOf) }.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }
                ?.let { add(Record("Día más productivo", "${it.value} ${if (it.value == 1) "tarea" else "tareas"}", it.key.format(dayFmt))) }
            sessions.maxByOrNull { it.activeMillis }?.takeIf { it.activeMillis > 0 }?.let {
                add(Record("Sesión más larga", com.rutina.app.timer.TimerController.formatDuration(it.activeMillis), HabitLogic.dateOf(it.start).format(dayFmt)))
            }
            habits.filter { it.type == TrackingType.QUIT }
                .map { it to HabitStats.quitSummary(it, byHabit[it.id].orEmpty()) }
                .maxByOrNull { it.second.bestMs }?.takeIf { it.second.bestMs > 0 }
                ?.let { (h, q) -> add(Record("Más tiempo sin recaer", HabitLogic.formatElapsed(q.bestMs), h.name)) }
        }

        val streaks = habits
            .filter { it.type == TrackingType.COUNT || it.type == TrackingType.JOURNAL || it.type == TrackingType.HEALTH }
            .map { h ->
                val s = HabitStats.summary(h, HabitStats.logsOf(h, byHabit, journal), weekStart, today)
                StreakRecord(h, s.best, s.current, s.streakUnit)
            }
            .filter { it.best > 0 }
            .sortedByDescending { it.best }
            .take(8)

        return AchievementsData(firstUse, milestones, records, streaks)
    }

    private fun fmt(v: Long): String = "%,d".format(es, v)

    private fun formatHours(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        return if (h > 0) "$h h $m min" else "$m min"
    }

    fun thresholdLabel(m: Milestone, t: Long): String = "${fmt(t)} ${m.unit}"
}
