package com.rutina.app.domain

import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.TrackingType
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** Estado de ánimo: se guarda como valor 1..5. */
object Mood {
    data class Level(val value: Int, val emoji: String, val label: String)

    val levels = listOf(
        Level(1, "😣", "Terrible"),
        Level(2, "🙁", "Mal"),
        Level(3, "😐", "Normal"),
        Level(4, "🙂", "Bien"),
        Level(5, "😄", "Genial"),
    )

    val emotions = listOf(
        "Feliz", "Tranquilo", "Agradecido", "Motivado", "Enamorado", "Orgulloso",
        "Cansado", "Estresado", "Ansioso", "Triste", "Enojado", "Aburrido",
    )

    fun level(v: Double): Level = levels[(v.roundToInt() - 1).coerceIn(0, 4)]

    /** "🙂 Bien" (con decimales si es un promedio). */
    fun label(v: Double): String = level(v).let { "${it.emoji} ${it.label}" }

    fun percent(v: Double): Int = (v / 5.0 * 100).roundToInt()
}

/** Métricas de Health Connect. La unidad del hábito identifica la métrica. */
enum class HealthMetric(val title: String, val unit: String, val defaultTarget: Double, val step: Double) {
    STEPS("Pasos", "pasos", 10_000.0, 1_000.0),
    DISTANCE("Distancia recorrida", "km", 5.0, 1.0),
    CALORIES("Calorías activas", "kcal", 400.0, 50.0),
    FLOORS("Pisos subidos", "pisos", 10.0, 1.0),
    EXERCISE("Minutos de ejercicio", "min de ejercicio", 30.0, 5.0);

    companion object {
        fun of(h: Habit): HealthMetric = entries.firstOrNull { it.unit == h.unit } ?: STEPS
    }
}

/** Unidades de peso. */
val WeightUnits = listOf("kg", "lb")

/** Resumen de peso corporal. */
data class WeightSummary(
    val current: Double?,
    val first: Double?,
    val change30: Double?,
    val min: Double?,
    val max: Double?,
    val entries: Int,
)

object Weight {
    fun summary(logs: List<HabitLog>, now: Long = System.currentTimeMillis()): WeightSummary {
        val sorted = logs.sortedBy { it.timestamp }
        val current = sorted.lastOrNull()?.value
        val monthAgo = now - 30L * 86_400_000
        val base30 = sorted.lastOrNull { it.timestamp <= monthAgo }?.value ?: sorted.firstOrNull()?.value
        return WeightSummary(
            current = current,
            first = sorted.firstOrNull()?.value,
            change30 = if (current != null && base30 != null && sorted.size > 1) current - base30 else null,
            min = sorted.minOfOrNull { it.value },
            max = sorted.maxOfOrNull { it.value },
            entries = sorted.size,
        )
    }

    fun format(v: Double): String = String.format("%.1f", v)

    fun delta(v: Double): String = (if (v > 0) "+" else "") + format(v)
}

/** Texto del valor de un registro según el tipo de hábito. */
fun valueLabel(h: Habit, value: Double): String = when (h.type) {
    TrackingType.MOOD -> Mood.label(value)
    TrackingType.WEIGHT -> "${Weight.format(value)} ${h.unit}"
    else -> HabitLogic.amountLabel(value, h)
}

/** ¿Hay algún registro ese día? */
fun loggedOn(logs: List<HabitLog>, date: LocalDate): Boolean = logs.any { HabitLogic.dateOf(it.timestamp) == date }

/** Texto corto del estado de un hábito en un día (tarjetas, widget, avisos). */
fun todayLabel(h: Habit, logs: List<HabitLog>, date: LocalDate, weekStart: DayOfWeek): String = when (h.type) {
    TrackingType.QUIT -> "${HabitLogic.formatShort(HabitLogic.quitNow(h) - (h.quitSince ?: h.createdAt))} " +
        if (h.pausedAt != null) "· en pausa" else "sin recaer"
    TrackingType.MOOD -> logs.filter { HabitLogic.dateOf(it.timestamp) == date }.maxByOrNull { it.timestamp }
        ?.let { Mood.label(it.value) } ?: "¿Cómo te sientes?"
    TrackingType.WEIGHT -> {
        val end = HabitLogic.startOfDay(date.plusDays(1))
        val l = logs.filter { it.timestamp < end }.sortedBy { it.timestamp }
        val last = l.lastOrNull()
        if (last == null) "Registra tu peso"
        else "${Weight.format(last.value)} ${h.unit}" + (l.getOrNull(l.size - 2)?.let { " (${Weight.delta(last.value - it.value)})" } ?: "")
    }
    else -> HabitLogic.progressLabel(h, HabitLogic.progress(h, logs, date, weekStart))
}

/** ¿Cuenta como hecho hoy (para tachar / marcar)? */
fun doneOn(h: Habit, logs: List<HabitLog>, date: LocalDate, weekStart: DayOfWeek): Boolean = when (h.type) {
    TrackingType.QUIT, TrackingType.WEIGHT -> false
    TrackingType.MOOD -> loggedOn(logs, date)
    else -> h.goalType != com.rutina.app.data.GoalType.NEGATIVE &&
        HabitLogic.isMet(h, HabitLogic.progress(h, logs, date, weekStart))
}
