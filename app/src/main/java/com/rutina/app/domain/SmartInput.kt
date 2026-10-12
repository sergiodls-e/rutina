package com.rutina.app.domain

import com.rutina.app.data.RepeatRule
import com.rutina.app.data.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Lo que la entrada inteligente encontró en el título de una tarea. */
data class Parsed(
    val title: String,
    val day: LocalDate? = null,
    val minutes: Int? = null,
    val repeat: RepeatRule? = null,
    val every: Int = 1,
    /** Bits lunes..domingo para repetición semanal. */
    val days: Int = 0,
    val priority: Int? = null,
) {
    val found: Boolean get() = day != null || minutes != null || repeat != null || priority != null

    /** Etiquetas para mostrar lo detectado: "Mañana", "7:00 p. m.", "Cada lunes"… */
    fun chips(today: LocalDate = LocalDate.now()): List<String> = buildList {
        day?.let { d -> TaskLogic.dueLabel(Task(title = "", dueEpochDay = d.toEpochDay()), today)?.let(::add) }
        minutes?.let { add(TaskLogic.timeLabel(it)) }
        repeat?.let { r -> add("↻ " + TaskLogic.repeatLabel(Task(title = "", repeat = r, repeatEvery = every, repeatDays = days))) }
        priority?.let { add("⚑ " + TaskLogic.priorityLabel(it)) }
    }

    /** Aplica lo detectado a una tarea (lo no detectado se queda como estaba). */
    fun applyTo(t: Task): Task {
        var r = t.copy(title = title)
        if (repeat != null) r = r.copy(repeat = repeat, repeatEvery = every, repeatDays = days)
        if (day != null) r = r.copy(dueEpochDay = day.toEpochDay())
        if (minutes != null) r = r.copy(dueMinutes = minutes, dueEpochDay = r.dueEpochDay ?: LocalDate.now().toEpochDay())
        if (priority != null) r = r.copy(priority = priority)
        if (r.repeat != RepeatRule.NEVER && r.dueEpochDay == null) r = r.copy(dueEpochDay = LocalDate.now().toEpochDay())
        return r
    }
}

/**
 * Entrada inteligente en español: detecta fecha, hora, repetición y prioridad
 * dentro del título ("Gym mañana 7am", "Pagar renta cada mes", "Junta el viernes a las 10").
 */
object SmartInput {
    // Límites de palabra que entienden acentos y ñ.
    private const val B0 = "(?<![\\p{L}\\d])"
    private const val B1 = "(?![\\p{L}\\d])"
    private const val WD = "lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?"
    private const val MONTHS = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|" +
        "ene|feb|mar|abr|may|jun|jul|ago|sept|sep|oct|nov|dic"

    private fun rx(p: String) = Regex(B0 + p + B1, RegexOption.IGNORE_CASE)

    private fun weekday(w: String): DayOfWeek = when (w.lowercase().take(2)) {
        "lu" -> DayOfWeek.MONDAY
        "ma" -> DayOfWeek.TUESDAY
        "mi" -> DayOfWeek.WEDNESDAY
        "ju" -> DayOfWeek.THURSDAY
        "vi" -> DayOfWeek.FRIDAY
        "sa", "sá" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun month(m: String): Int {
        val k = m.lowercase()
        return when {
            k.startsWith("ene") -> 1; k.startsWith("feb") -> 2; k.startsWith("mar") -> 3; k.startsWith("abr") -> 4
            k.startsWith("may") -> 5; k.startsWith("jun") -> 6; k.startsWith("jul") -> 7; k.startsWith("ago") -> 8
            k.startsWith("sep") || k.startsWith("set") -> 9; k.startsWith("oct") -> 10; k.startsWith("nov") -> 11
            else -> 12
        }
    }

    private val reEveryN = rx("(?:cada|todos los|todas las)\\s+(\\d{1,3})\\s+(d[ií]as|semanas|meses|a[ñn]os)")
    private val reEveryWeekday = rx("(?:cada|todos los|todas las)\\s+(?:$WD)(?:\\s*(?:,|y|e)\\s*(?:$WD))*")
    private val reWeekdayWord = Regex("$B0($WD)$B1", RegexOption.IGNORE_CASE)
    private val reDaily = rx("todos los d[ií]as|cada d[ií]a|diariamente")
    private val reWeekdays = rx("entre semana|de lunes a viernes|d[ií]as laborables")
    private val reWeekly = rx("cada semana|todas las semanas|semanalmente")
    private val reMonthly = rx("cada mes|todos los meses|mensualmente")
    private val reYearly = rx("cada a[ñn]o|todos los a[ñn]os|anualmente")

    private val reNoon = rx("(?:al |a )?medio ?d[ií]a")
    private val reTime = rx(
        "(?:(a las|a la|@)\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?|hrs?|h)?" +
            "(?:\\s+(de la ma[ñn]ana|de la tarde|de la noche|de la madrugada))?"
    )

    private val reAfterTomorrow = rx("pasado ma[ñn]ana")
    private val reToday = rx("hoy")
    // "en la mañana" es momento del día, no fecha.
    private val reTomorrow = rx("(?<!la )ma[ñn]ana")
    private val reIn = rx("en\\s+(\\d{1,3})\\s+(d[ií]as|semanas|meses)")
    private val reNextWeek = rx("(?:la )?(?:pr[oó]xima semana|semana que viene)")
    private val reWeekday = rx("(?:(?:el|este|esta|para el)\\s+)?(?:pr[oó]ximo\\s+)?($WD)(?:\\s+que viene)?")
    private val reDateWords = rx("(?:(?:el|para el)\\s+)?(\\d{1,2})\\s+(?:de\\s+)?($MONTHS)\\.?(?:\\s+(?:de\\s+)?(\\d{4}))?")
    // "1/2 kilo" no es fecha: se pide "el 15/9" o el año.
    private val reDateSlash = rx("(?:(el|para el)\\s+)?(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?")
    private val rePriority = Regex("(?<!\\S)(?:!(alta|media|baja|1|2|3)|prioridad (alta|media|baja))(?!\\S)", RegexOption.IGNORE_CASE)

    fun parse(text: String, today: LocalDate = LocalDate.now()): Parsed {
        val taken = mutableListOf<IntRange>()
        fun free(r: IntRange) = taken.none { it.first <= r.last && r.first <= it.last }
        fun find(re: Regex, accept: (MatchResult) -> Boolean = { true }): MatchResult? =
            re.findAll(text).firstOrNull { free(it.range) && accept(it) }?.also { taken += it.range }

        var repeat: RepeatRule? = null
        var every = 1
        var days = 0
        var day: LocalDate? = null
        var minutes: Int? = null
        var priority: Int? = null

        // 1) Repetición
        find(reEveryN)?.let { m ->
            every = m.groupValues[1].toInt().coerceIn(1, 999)
            repeat = when (m.groupValues[2].lowercase().take(2)) {
                "dí", "di" -> RepeatRule.DAILY
                "se" -> RepeatRule.WEEKLY
                "me" -> RepeatRule.MONTHLY
                else -> RepeatRule.YEARLY
            }
        }
        if (repeat == null) find(reEveryWeekday)?.let { m ->
            reWeekdayWord.findAll(m.value).forEach { w -> days = days or (1 shl (weekday(w.value).value - 1)) }
            repeat = RepeatRule.WEEKLY
        }
        if (repeat == null) find(reWeekdays)?.let { repeat = RepeatRule.WEEKDAYS }
        if (repeat == null) find(reDaily)?.let { repeat = RepeatRule.DAILY }
        if (repeat == null) find(reWeekly)?.let { repeat = RepeatRule.WEEKLY }
        if (repeat == null) find(reMonthly)?.let { repeat = RepeatRule.MONTHLY }
        if (repeat == null) find(reYearly)?.let { repeat = RepeatRule.YEARLY }

        // 2) Hora (antes que la fecha para que "de la mañana" no se lea como "mañana")
        find(reNoon)?.let { minutes = 12 * 60 }
        if (minutes == null) find(reTime) { m ->
            val prefix = m.groupValues[1].isNotEmpty()
            val hasMin = m.groupValues[3].isNotEmpty()
            // "h"/"hrs" solas no bastan ("Leer 2 h" es duración, no hora): se piden a. m./p. m. o "de la tarde"…
            val ap = m.groupValues[4].lowercase().replace(".", "").replace(" ", "")
            val suffix = ap == "am" || ap == "pm" || m.groupValues[5].isNotEmpty()
            val h = m.groupValues[2].toInt()
            val mm = m.groupValues[3].toIntOrNull() ?: 0
            (prefix || hasMin || suffix) && h in 0..23 && mm in 0..59
        }?.let { m ->
            var h = m.groupValues[2].toInt()
            val mm = m.groupValues[3].toIntOrNull() ?: 0
            val ap = m.groupValues[4].lowercase().replace(".", "").replace(" ", "")
            val part = m.groupValues[5].lowercase()
            val pm = ap == "pm" || part.endsWith("tarde") || part.endsWith("noche")
            val am = ap == "am" || part.endsWith("ana") || part.endsWith("madrugada")
            if (pm && h in 1..11) h += 12
            if (am && h == 12) h = 0
            minutes = h * 60 + mm
        }

        // 3) Fecha
        find(reAfterTomorrow)?.let { day = today.plusDays(2) }
        if (day == null) find(reToday)?.let { day = today }
        if (day == null) find(reTomorrow)?.let { day = today.plusDays(1) }
        if (day == null) find(reIn)?.let { m ->
            val n = m.groupValues[1].toLong()
            day = when (m.groupValues[2].lowercase().take(2)) {
                "se" -> today.plusWeeks(n)
                "me" -> today.plusMonths(n)
                else -> today.plusDays(n)
            }
        }
        if (day == null) find(reNextWeek)?.let { day = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) }
        if (day == null) find(reDateWords)?.let { m ->
            day = dateOf(m.groupValues[1].toInt(), month(m.groupValues[2]), m.groupValues[3].toIntOrNull(), today)
        }
        if (day == null) find(reDateSlash) { m -> m.groupValues[1].isNotEmpty() || m.groupValues[4].isNotEmpty() }?.let { m ->
            val y = m.groupValues[4].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            day = dateOf(m.groupValues[2].toInt(), m.groupValues[3].toInt(), y, today)
        }
        if (day == null && repeat == null) find(reWeekday)?.let { m ->
            day = today.with(TemporalAdjusters.next(weekday(m.groupValues[1])))
        }

        // 4) Prioridad
        find(rePriority)?.let { m ->
            val v = (m.groupValues[1].ifEmpty { m.groupValues[2] }).lowercase()
            priority = when (v) { "alta", "3" -> 3; "media", "2" -> 2; else -> 1 }
        }

        // Repeticiones sin fecha: empiezan hoy (o en el primer día marcado).
        if (repeat != null && day == null) {
            day = if (repeat == RepeatRule.WEEKLY && days != 0) {
                (0..6).map { today.plusDays(it.toLong()) }.first { days and (1 shl (it.dayOfWeek.value - 1)) != 0 }
            } else today
        }

        if (taken.isEmpty()) return Parsed(text)
        val title = buildString {
            text.forEachIndexed { i, c -> if (taken.none { i in it }) append(c) }
        }.replace(Regex("\\s+"), " ").trim().trim(',', '-', ';', ':').trim()
        // Si no queda título, no se toca nada.
        if (title.isEmpty()) return Parsed(text)
        return Parsed(title, day, minutes, repeat, every, days, priority)
    }

    private fun dateOf(d: Int, m: Int, y: Int?, today: LocalDate): LocalDate? = runCatching {
        val candidate = LocalDate.of(y ?: today.year, m, d)
        if (y == null && candidate < today) candidate.plusYears(1) else candidate
    }.getOrNull()
}
