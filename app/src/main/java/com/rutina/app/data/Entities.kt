package com.rutina.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cómo se registra un hábito. */
enum class TrackingType { COUNT, QUIT, MOOD, WEIGHT, HEALTH, JOURNAL }

/** Positivo = llegar a la meta; Negativo = no pasarse; Ninguno = solo registrar. */
enum class GoalType { POSITIVE, NEGATIVE, NONE }

enum class GoalPeriod { DAY, WEEK, MONTH, YEAR }

/** Qué días toca el hábito. */
enum class ScheduleType { ALL_DAYS, SPECIFIC_DAYS, DAYS_PER_WEEK }

enum class RepeatRule { NEVER, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY }

/** Rango de fechas que acepta un filtro de tareas. */
enum class DateRange { ANY, OVERDUE, TODAY, NEXT_7_DAYS, NO_DATE }

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val type: TrackingType = TrackingType.COUNT,
    val goalType: GoalType = GoalType.POSITIVE,
    val target: Double = 1.0,
    val unit: String = "vez",
    val period: GoalPeriod = GoalPeriod.DAY,
    val scheduleType: ScheduleType = ScheduleType.ALL_DAYS,
    /** Bits 0..6 = lunes..domingo (ISO). */
    val scheduleDays: Int = 0b1111111,
    val daysPerWeek: Int = 7,
    /** Para hábitos de tipo QUIT: momento desde el que cuenta. */
    val quitSince: Long? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Lista a la que pertenece (las mismas listas que las tareas). */
    val listId: Long? = null,
    /** 0 = ninguna, 1 baja, 2 media, 3 alta. */
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    /** Plural de la unidad ("páginas"); vacío = automático. */
    @ColumnInfo(defaultValue = "''") val unitPlural: String = "",
    /** Cuánto suma el botón +. */
    @ColumnInfo(defaultValue = "1.0") val step: Double = 1.0,
    /** Hábito de Dejar en pausa desde este momento (el contador se congela). */
    val pausedAt: Long? = null,
    /** Temporizador preferido: "STOPWATCH" o "COUNTDOWN" (null = el último usado). */
    val timerMode: String? = null,
    /** Minutos de la cuenta atrás preferida. */
    val timerMinutes: Int? = null,
)

@Entity(
    tableName = "habit_logs",
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("habitId"), Index("timestamp")],
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val timestamp: Long,
    val value: Double = 1.0,
    val note: String? = null,
)

@Entity(tableName = "task_lists")
data class TaskList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val sortOrder: Int = 0,
    /** Clave del ícono (ver ListIcons). */
    @ColumnInfo(defaultValue = "'list'") val icon: String = "list",
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = TaskList::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("listId"), Index("dueEpochDay")],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    /** LocalDate.toEpochDay(); null = sin fecha. */
    val dueEpochDay: Long? = null,
    /** Minutos desde medianoche; null = sin hora. */
    val dueMinutes: Int? = null,
    val repeat: RepeatRule = RepeatRule.NEVER,
    val notify: Boolean = false,
    /** Duración en minutos (para el calendario). */
    @ColumnInfo(defaultValue = "30") val durationMin: Int = 30,
    /** 0 = ninguna, 1 baja, 2 media, 3 alta. */
    val priority: Int = 0,
    val listId: Long? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** "No lo haré": cerrada sin completarse (también lleva done = true). */
    @ColumnInfo(defaultValue = "0") val skipped: Boolean = false,
    /** Cada cuántos días/semanas/meses/años se repite. */
    @ColumnInfo(defaultValue = "1") val repeatEvery: Int = 1,
    /** Para repetición semanal: bits 0..6 = lunes..domingo (0 = el mismo día de la fecha). */
    @ColumnInfo(defaultValue = "0") val repeatDays: Int = 0,
    /** Última fecha (epochDay) en que se repite; null = sin fin. */
    val repeatUntil: Long? = null,
    /** Id de la primera tarea de la serie (para el historial de repeticiones). */
    val seriesId: Long? = null,
    /** Orden manual. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)

/** Id de la serie de repeticiones a la que pertenece la tarea. */
val Task.series: Long get() = seriesId ?: id

@Entity(tableName = "notes", indices = [Index("timestamp"), Index("habitId"), Index("taskId")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val content: String,
    val habitId: Long? = null,
    val taskId: Long? = null,
    val starred: Boolean = false,
)

@Entity(tableName = "timer_sessions", indices = [Index("habitId")])
data class TimerSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long? = null,
    val start: Long,
    val end: Long,
    val activeMillis: Long,
    val countdown: Boolean,
)

/** Filtro guardado: muestra las tareas que cumplen todos sus criterios. */
@Entity(tableName = "task_filters")
data class TaskFilter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val dateRange: DateRange = DateRange.ANY,
    /** 0 = cualquiera; si no, prioridad mínima. */
    val minPriority: Int = 0,
    /** null = todas las listas. */
    val listId: Long? = null,
    val includeDone: Boolean = false,
    val sortOrder: Int = 0,
    /** Texto que debe aparecer en el título (vacío = cualquiera). */
    @ColumnInfo(defaultValue = "''") val text: String = "",
)

/** Recordatorio de un hábito: una hora y los días en que suena. */
@Entity(
    tableName = "habit_reminders",
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("habitId")],
)
data class HabitReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    /** Minutos desde medianoche. */
    val minutes: Int,
    /** Bits 0..6 = lunes..domingo (ISO). */
    val days: Int = 0b1111111,
)
