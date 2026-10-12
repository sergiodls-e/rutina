package com.rutina.app.domain

import com.rutina.app.data.GoalPeriod
import com.rutina.app.data.GoalType
import com.rutina.app.data.TrackingType

data class Suggestion(
    val name: String,
    val color: Int,
    val type: TrackingType = TrackingType.COUNT,
    val goalType: GoalType = GoalType.POSITIVE,
    val target: Double = 1.0,
    val unit: String = "vez",
    val period: GoalPeriod = GoalPeriod.DAY,
)

data class SuggestionGroup(val title: String, val items: List<Suggestion>)

/** Paleta de colores pastel para hábitos y listas. */
object Palette {
    val colors: List<Int> = listOf(
        0xFFF28B95, 0xFFF5A88A, 0xFFF6D98E, 0xFFB5DCA5, 0xFF8FD3C1, 0xFF8EC5F0,
        0xFF9FA8DA, 0xFFB39DDB, 0xFFE1A6D8, 0xFFB0BEC5, 0xFFE57373, 0xFF4DB6AC,
        0xFF7986CB, 0xFFFFB74D, 0xFF90A4AE, 0xFFA1887F,
    ).map { it.toInt() }
    fun at(i: Int) = colors[i % colors.size]
}

object Suggestions {
    private fun c(i: Int) = Palette.at(i)

    val groups: List<SuggestionGroup> = listOf(
        SuggestionGroup("Fitness", listOf(
            Suggestion("Hacer ejercicio", c(0), target = 30.0, unit = "min"),
            Suggestion("Correr", c(6), target = 3.0, unit = "km"),
            Suggestion("Hacer cardio", c(1), target = 20.0, unit = "min"),
            Suggestion("Levantar pesas", c(4), target = 3.0, unit = "vez", period = GoalPeriod.WEEK),
            Suggestion("Estirar", c(7), target = 2.0),
            Suggestion("Yoga", c(2), target = 15.0, unit = "min"),
        )),
        SuggestionGroup("Salud", listOf(
            Suggestion("Beber agua", c(5), target = 8.0, unit = "tazas"),
            Suggestion("Madrugar", c(2)),
            Suggestion("Acostarse temprano", c(7)),
            Suggestion("Tomar vitaminas", c(3)),
            Suggestion("Comer frutas y verduras", c(3), target = 5.0, unit = "porciones"),
        )),
        SuggestionGroup("Mente", listOf(
            Suggestion("Leer", c(6), target = 30.0, unit = "páginas"),
            Suggestion("Estudiar", c(1), target = 3.0, unit = "horas"),
            Suggestion("Aprender", c(3), target = 20.0, unit = "min"),
            Suggestion("Meditar", c(0), target = 10.0, unit = "min"),
            Suggestion("Practicar instrumento", c(7), target = 30.0, unit = "min"),
        )),
        SuggestionGroup("Hogar", listOf(
            Suggestion("Limpiar", c(7)),
            Suggestion("Lavar los platos", c(9)),
            Suggestion("Lavar ropa", c(6), period = GoalPeriod.WEEK),
            Suggestion("Cocinar", c(4)),
            Suggestion("Regar plantas", c(3), period = GoalPeriod.WEEK, target = 2.0),
        )),
        SuggestionGroup("Reducir", listOf(
            Suggestion("Fumar menos", c(4), goalType = GoalType.NEGATIVE, target = 3.0, unit = "cigarros"),
            Suggestion("Beber menos", c(7), goalType = GoalType.NEGATIVE, target = 2.0, unit = "bebidas", period = GoalPeriod.WEEK),
            Suggestion("Comer menos dulces", c(6), goalType = GoalType.NEGATIVE, target = 1.0),
            Suggestion("Menos redes sociales", c(5), goalType = GoalType.NEGATIVE, target = 60.0, unit = "min"),
        )),
        SuggestionGroup("Diario", listOf(
            Suggestion("Diario", c(7), type = TrackingType.JOURNAL, target = 100.0, unit = "palabras"),
            Suggestion("Reflexión personal", c(2), type = TrackingType.JOURNAL, target = 50.0, unit = "palabras"),
            Suggestion("Afirmaciones positivas", c(6), type = TrackingType.JOURNAL, target = 30.0, unit = "palabras"),
            Suggestion("Gratitud", c(3), type = TrackingType.JOURNAL, target = 30.0, unit = "palabras"),
            Suggestion("Diario de sueños", c(8), type = TrackingType.JOURNAL, target = 50.0, unit = "palabras"),
        )),
        SuggestionGroup("Estado de ánimo", listOf(
            Suggestion("Estado de ánimo", c(6), type = TrackingType.MOOD, goalType = GoalType.NONE, unit = ""),
            Suggestion("¿Cómo dormí?", c(7), type = TrackingType.MOOD, goalType = GoalType.NONE, unit = ""),
        )),
        SuggestionGroup("Peso corporal", listOf(
            Suggestion("Peso corporal", c(1), type = TrackingType.WEIGHT, goalType = GoalType.NONE, target = 0.0, unit = "kg"),
        )),
        SuggestionGroup("Health Connect", listOf(
            Suggestion("Pasos", c(6), type = TrackingType.HEALTH, target = 10_000.0, unit = "pasos"),
            Suggestion("Distancia recorrida", c(4), type = TrackingType.HEALTH, target = 5.0, unit = "km"),
            Suggestion("Calorías activas", c(1), type = TrackingType.HEALTH, target = 400.0, unit = "kcal"),
            Suggestion("Pisos subidos", c(7), type = TrackingType.HEALTH, target = 10.0, unit = "pisos"),
            Suggestion("Minutos de ejercicio", c(0), type = TrackingType.HEALTH, target = 30.0, unit = "min de ejercicio"),
        )),
        SuggestionGroup("Dejar", listOf(
            Suggestion("Dejar de fumar", c(9), type = TrackingType.QUIT, goalType = GoalType.NONE),
            Suggestion("Dejar de beber", c(0), type = TrackingType.QUIT, goalType = GoalType.NONE),
            Suggestion("Dejar de vapear", c(4), type = TrackingType.QUIT, goalType = GoalType.NONE),
            Suggestion("Dejar las redes sociales", c(5), type = TrackingType.QUIT, goalType = GoalType.NONE),
            Suggestion("Dejar la comida rápida", c(10), type = TrackingType.QUIT, goalType = GoalType.NONE),
        )),
    )

    val units = listOf("vez", "min", "horas", "páginas", "tazas", "vasos", "pasos", "km", "porciones", "cigarros", "bebidas", "pisos", "calorías", "palabras")
}
