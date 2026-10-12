package com.rutina.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

/** Preferencias de la app guardadas en el teléfono. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _habitDisplay = MutableStateFlow(
        runCatching { HabitDisplay.valueOf(sp.getString("habitDisplay", null) ?: "COMPACT") }.getOrDefault(HabitDisplay.COMPACT)
    )
    val habitDisplay: StateFlow<HabitDisplay> = _habitDisplay

    private val _calendarView = MutableStateFlow(
        runCatching { CalView.valueOf(sp.getString("calendarView", null) ?: "WEEK") }.getOrDefault(CalView.WEEK)
    )
    val calendarView: StateFlow<CalView> = _calendarView

    fun setCalendarView(v: CalView) {
        sp.edit().putString("calendarView", v.name).apply()
        _calendarView.value = v
    }

    fun setHabitDisplay(d: HabitDisplay) {
        sp.edit().putString("habitDisplay", d.name).apply()
        _habitDisplay.value = d
    }
}
