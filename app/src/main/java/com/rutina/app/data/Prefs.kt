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

/** Preferencias de la app guardadas en el teléfono. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _habitDisplay = MutableStateFlow(
        runCatching { HabitDisplay.valueOf(sp.getString("habitDisplay", null) ?: "COMPACT") }.getOrDefault(HabitDisplay.COMPACT)
    )
    val habitDisplay: StateFlow<HabitDisplay> = _habitDisplay

    fun setHabitDisplay(d: HabitDisplay) {
        sp.edit().putString("habitDisplay", d.name).apply()
        _habitDisplay.value = d
    }
}
