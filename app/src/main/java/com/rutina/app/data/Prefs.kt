package com.rutina.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek

/** Cómo se muestran los hábitos en Hoy / Todos los hábitos. */
enum class HabitDisplay(val label: String) {
    COMPACT("Compacto"),
    WEEK("Semanal"),
    MONTH("Mensual"),
    YEAR("Anual"),
}

/** Vista del calendario. [days] = columnas de la cuadrícula (0 = mes). */
enum class CalView(val label: String, val days: Int) {
    DAY("Día", 1),
    THREE("3 días", 3),
    WEEK("Semana", 7),
    MONTH("Mes", 0),
}

/** Tamaño del texto de la app (multiplica el tamaño elegido en Android). */
enum class TextSize(val label: String, val scale: Float) {
    SMALL("Pequeño", 0.9f),
    NORMAL("Normal", 1f),
    LARGE("Grande", 1.1f),
    XL("Muy grande", 1.2f),
}

/** Pestaña con la que abre la app. */
enum class StartTab(val label: String) {
    TODAY("Hoy"),
    JOURNAL("Diario"),
    CALENDAR("Calendario"),
}

/** Días con los que puede empezar la semana. */
val WeekStarts = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY)

fun weekStartLabel(d: DayOfWeek) = when (d) {
    DayOfWeek.MONDAY -> "Lunes"
    DayOfWeek.SATURDAY -> "Sábado"
    else -> "Domingo"
}

/** Preferencias de la app guardadas en el teléfono. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        runCatching { enumValueOf<E>(sp.getString(key, null) ?: default.name) }.getOrDefault(default)

    private val _habitDisplay = MutableStateFlow(enumPref("habitDisplay", HabitDisplay.COMPACT))
    val habitDisplay: StateFlow<HabitDisplay> = _habitDisplay

    private val _calendarView = MutableStateFlow(enumPref("calendarView", CalView.WEEK))
    val calendarView: StateFlow<CalView> = _calendarView

    private val _weekStart = MutableStateFlow(
        runCatching { DayOfWeek.valueOf(sp.getString("weekStart", null) ?: "SUNDAY") }.getOrDefault(DayOfWeek.SUNDAY)
    )
    /** Día con el que empieza la semana (hábitos semanales, estadísticas, calendario, widgets). */
    val weekStart: StateFlow<DayOfWeek> = _weekStart

    private val _textSize = MutableStateFlow(enumPref("textSize", TextSize.NORMAL))
    val textSize: StateFlow<TextSize> = _textSize

    private val _startTab = MutableStateFlow(enumPref("startTab", StartTab.TODAY))
    val startTab: StateFlow<StartTab> = _startTab

    private val _lastBackup = MutableStateFlow(sp.getLong("lastBackup", 0L))
    /** Momento del último respaldo exportado (0 = nunca). */
    val lastBackup: StateFlow<Long> = _lastBackup

    fun setCalendarView(v: CalView) {
        sp.edit().putString("calendarView", v.name).apply()
        _calendarView.value = v
    }

    fun setHabitDisplay(d: HabitDisplay) {
        sp.edit().putString("habitDisplay", d.name).apply()
        _habitDisplay.value = d
    }

    fun setWeekStart(d: DayOfWeek) {
        sp.edit().putString("weekStart", d.name).apply()
        _weekStart.value = d
    }

    fun setTextSize(t: TextSize) {
        sp.edit().putString("textSize", t.name).apply()
        _textSize.value = t
    }

    fun setStartTab(t: StartTab) {
        sp.edit().putString("startTab", t.name).apply()
        _startTab.value = t
    }

    fun markBackup(at: Long = System.currentTimeMillis()) {
        sp.edit().putLong("lastBackup", at).apply()
        _lastBackup.value = at
    }

    /** Ajustes que viajan dentro del respaldo. */
    fun exportMap(): Map<String, String> = mapOf(
        "habitDisplay" to habitDisplay.value.name,
        "calendarView" to calendarView.value.name,
        "weekStart" to weekStart.value.name,
        "textSize" to textSize.value.name,
        "startTab" to startTab.value.name,
    )

    fun importMap(m: Map<String, String>) {
        m["habitDisplay"]?.let { v -> runCatching { setHabitDisplay(HabitDisplay.valueOf(v)) } }
        m["calendarView"]?.let { v -> runCatching { setCalendarView(CalView.valueOf(v)) } }
        m["weekStart"]?.let { v -> runCatching { setWeekStart(DayOfWeek.valueOf(v)) } }
        m["textSize"]?.let { v -> runCatching { setTextSize(TextSize.valueOf(v)) } }
        m["startTab"]?.let { v -> runCatching { setStartTab(StartTab.valueOf(v)) } }
    }
}
