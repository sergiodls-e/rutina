package com.rutina.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutina.app.data.GoalType
import com.rutina.app.data.Habit
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.HabitStats
import com.rutina.app.ui.common.BarChart
import com.rutina.app.ui.common.LineChart
import com.rutina.app.domain.ChartPoint
import com.rutina.app.domain.Mood
import com.rutina.app.domain.Weight
import com.rutina.app.ui.common.CellLegend
import com.rutina.app.ui.common.MonthGrid
import com.rutina.app.ui.common.YearHeatmap
import com.rutina.app.ui.journal.logLabel
import com.rutina.app.ui.journal.stampLabel
import com.rutina.app.ui.journal.userNote
import com.rutina.app.ui.theme.Accent
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val es = Locale("es", "MX")
private val Streak = Color(0xFFFF8A50)

private fun pct(r: Float?) = r?.let { "${(it * 100).roundToInt()} %" } ?: "—"

// ---------------------------------------------------------------- Resumen general

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel, onClose: () -> Unit, onHabit: (Long) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text("Estadísticas", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Hábitos hoy", "${s.habitsDoneToday}/${s.habitsDueToday}", "cumplidos", Modifier.weight(1f))
                Tile("Tareas hoy", "${s.tasksToday}", "completadas", Modifier.weight(1f))
                Tile("Esta semana", "${s.tasksWeek}", "tareas hechas", Modifier.weight(1f))
            }

            Section("Cumplimiento de hábitos diarios") {
                BarChart(s.dailyRate, Accent, target = null, valueLabel = { "${it.roundToInt()} %" })
                Text("Porcentaje de tus hábitos diarios cumplidos cada día.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section("Tareas completadas") {
                BarChart(s.tasksDaily, Color(0xFF8FD3C1), target = null, valueLabel = { "${it.roundToInt()} tareas" })
                Text("${s.pendingTasks} pendientes en total.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("Tus hábitos") {
                if (s.loaded && s.habits.isEmpty()) {
                    Text("Aún no tienes hábitos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.habits.forEach { o ->
                    HabitOverviewRow(o, onClick = { onHabit(o.habit.id) })
                }
            }
        }
    }
}

@Composable
private fun HabitOverviewRow(o: HabitOverview, onClick: () -> Unit) {
    val color = Color(o.habit.color)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(o.habit.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (o.summary != null) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { o.summary.recentRate ?: 0f },
                    color = color,
                    trackColor = Color(0xFF2E2E2E),
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            } else if (o.quit != null) {
                Text(
                    "${HabitLogic.formatElapsed(o.quit.currentMs)} sin recaer",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (o.summary != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(pct(o.summary.recentRate), fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${o.summary.current}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(12.dp), tint = if (o.summary.current > 0) Streak else Color.Gray)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Detalle de un hábito

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitStatsScreen(vm: HabitStatsViewModel, onClose: () -> Unit, onEdit: (Long) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val h = s.habit
    if (s.loaded && h == null) { LaunchedEffect(Unit) { onClose() }; return }
    val color = h?.let { Color(it.color) } ?: Accent
    var month by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.Black) } },
                title = { Text(h?.name ?: "", fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (h != null) IconButton(onClick = { onEdit(h.id) }) { Icon(Icons.Filled.Edit, "Editar hábito", tint = Color.Black) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = color),
            )
        },
    ) { pad ->
        if (h == null) return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (h.type == TrackingType.QUIT && s.quit != null) QuitHeader(h, s.quit!!)
            if (h.type == TrackingType.MOOD) MoodStats(h, s)
            if (h.type == TrackingType.WEIGHT) WeightStats(h, s, color)
            s.summary?.takeIf { h.type != TrackingType.MOOD && h.type != TrackingType.WEIGHT }?.let { sm ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Racha actual", "${sm.current}", sm.streakUnit, Modifier.weight(1f), accent = if (sm.current > 0) Streak else null)
                    Tile("Mejor racha", "${sm.best}", sm.streakUnit, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Cumplimiento", pct(sm.recentRate), sm.recentLabel, Modifier.weight(1f))
                    Tile(
                        "Total", HabitLogic.amountLabel(sm.total, h.unit), "${sm.activeDays} días con registro",
                        Modifier.weight(1f),
                    )
                }
            }

            Section("Calendario") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "Mes anterior") }
                    Text(
                        "${month.month.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }} ${month.year}",
                        Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                        Icon(Icons.Filled.ChevronRight, "Mes siguiente")
                    }
                }
                MonthGrid(
                    month, StatsWeekStart, color,
                    cellFor = { d -> HabitStats.cell(h, d, s.totals, s.resets, today) },
                    selected = today,
                )
                if (h.type == TrackingType.QUIT) CellLegend(color, showFail = true, doneLabel = "Sin recaída", failLabel = "Recaída", showPartial = false)
                else CellLegend(color, showFail = h.goalType == GoalType.NEGATIVE)
            }

            if (s.chart.isNotEmpty() && h.type != TrackingType.WEIGHT) {
                Section(if (h.type == TrackingType.MOOD) "Ánimo promedio · ${HabitStats.chartTitle(h).lowercase()}" else HabitStats.chartTitle(h)) {
                    BarChart(
                        s.chart, color,
                        target = h.target.takeIf { h.goalType != GoalType.NONE && h.type != TrackingType.MOOD },
                        valueLabel = { if (h.type == TrackingType.MOOD) (if (it > 0) Mood.label(it) else "Sin registro") else HabitLogic.amountLabel(it, h.unit) },
                    )
                }
            }

            Section("Último año") {
                YearHeatmap(today, StatsWeekStart, color, cellFor = { d -> HabitStats.cell(h, d, s.totals, s.resets, today) })
            }

            Section(if (h.type == TrackingType.QUIT) "Recaídas" else "Historial") {
                val items = if (h.type == TrackingType.JOURNAL) s.logs.take(30) else s.logs.take(30)
                if (items.isEmpty()) {
                    Text(
                        if (h.type == TrackingType.QUIT) "Sin recaídas. ¡Sigue así!" else "Aún no hay registros.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(stampLabel(l.timestamp), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text(logLabel(h, l), fontSize = 14.sp)
                    }
                    l.userNote()?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuitHeader(h: Habit, q: com.rutina.app.domain.QuitSummary) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(h.id) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val current = now - (h.quitSince ?: h.createdAt)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tiempo sin recaer", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(HabitLogic.formatElapsed(current), fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Mejor racha", HabitLogic.formatShort(maxOf(q.bestMs, current)), "sin recaer", Modifier.weight(1f))
        Tile("Recaídas", "${q.relapses.size}", "en total", Modifier.weight(1f))
        Tile("Desde", stampLabel(h.createdAt).substringBefore(","), "inicio", Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Piezas

@Composable
private fun Tile(label: String, value: String, caption: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (accent != null) Icon(Icons.Filled.LocalFireDepartment, null, Modifier.padding(start = 4.dp).size(20.dp), tint = accent)
        }
        if (caption.isNotBlank()) Text(caption, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        content()
    }
}

// ---------------------------------------------------------------- Ánimo y peso

@Composable
private fun MoodStats(h: Habit, s: HabitDetail) {
    val today = LocalDate.now()
    val last30 = s.totals.filterKeys { it > today.minusDays(30) }.values
    val avg30 = last30.takeIf { it.isNotEmpty() }?.average()
    val counts = Mood.levels.associateWith { lv -> s.logs.count { Mood.level(it.value).value == lv.value } }
    val top = counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Promedio 30 días", avg30?.let { Mood.level(it).emoji } ?: "—", avg30?.let { "${Mood.level(it).label} · ${Mood.percent(it)} %" } ?: "Sin registros", Modifier.weight(1f))
        Tile("Más frecuente", top?.emoji ?: "—", top?.label ?: "", Modifier.weight(1f))
        Tile("Días registrados", "${s.totals.size}", "racha ${s.summary?.current ?: 0}", Modifier.weight(1f))
    }
    Section("Distribución") {
        val total = counts.values.sum().coerceAtLeast(1)
        Mood.levels.reversed().forEach { lv ->
            val n = counts[lv] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lv.emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                Text(lv.label, Modifier.width(72.dp), fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { n.toFloat() / total },
                    color = Color(h.color), trackColor = Color(0xFF2E2E2E), strokeCap = StrokeCap.Round,
                    modifier = Modifier.weight(1f).height(8.dp),
                )
                Text("$n", Modifier.width(36.dp), textAlign = TextAlign.End, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun WeightStats(h: Habit, s: HabitDetail, color: Color) {
    val w = Weight.summary(s.logs)
    val u = h.unit.ifBlank { "kg" }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Actual", w.current?.let(Weight::format) ?: "—", u, Modifier.weight(1f))
        Tile(
            "Cambio total",
            if (w.current != null && w.first != null) Weight.delta(w.current - w.first) else "—",
            "$u desde el inicio", Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Últimos 30 días", w.change30?.let(Weight::delta) ?: "—", u, Modifier.weight(1f))
        val goal = h.target.takeIf { h.goalType != GoalType.NONE && it > 0 }
        val remaining = if (goal != null && w.current != null) goal - w.current else null
        val reached = remaining != null && when (h.goalType) {
            GoalType.NEGATIVE -> remaining >= 0
            GoalType.POSITIVE -> remaining <= 0
            GoalType.NONE -> false
        }
        Tile(
            "Meta",
            when { goal == null -> "—"; reached -> "✓"; else -> Weight.format(kotlin.math.abs(remaining ?: 0.0)) },
            when { goal == null -> "Sin meta"; reached -> "¡Alcanzada! (${Weight.format(goal)} $u)"; else -> "$u para llegar a ${Weight.format(goal)}" },
            Modifier.weight(1f),
        )
    }
    Section("Tendencia") {
        val pts = s.logs.sortedBy { it.timestamp }.takeLast(60).map {
            val d = HabitLogic.dateOf(it.timestamp)
            ChartPoint("${d.dayOfMonth}/${d.monthValue}", it.value)
        }
        LineChart(
            pts, color,
            target = h.target.takeIf { h.goalType != GoalType.NONE && it > 0 },
            valueLabel = { "${Weight.format(it)} $u" },
        )
        if (w.min != null && w.max != null) {
            Text(
                "Mínimo ${Weight.format(w.min)} · máximo ${Weight.format(w.max)} · ${w.entries} registros",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
