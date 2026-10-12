package com.rutina.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.domain.Cell
import com.rutina.app.domain.CellKind
import com.rutina.app.domain.ChartPoint
import com.rutina.app.domain.HabitLogic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale("es", "MX")

val FailColor = Color(0xFFE57373)
private val EmptyColor = Color(0xFF2E2E2E)
private val SkipColor = Color(0xFF1F1F1F)
private val FutureColor = Color(0xFF191919)

/** Color de una celda de calendario según su estado. */
fun cellColor(c: Cell, color: Color): Color = when (c.kind) {
    CellKind.DONE -> color
    CellKind.PARTIAL -> color.copy(alpha = 0.25f + 0.5f * c.fraction)
    CellKind.FAILED -> FailColor
    CellKind.EMPTY -> EmptyColor
    CellKind.SKIP -> SkipColor
    CellKind.FUTURE -> FutureColor
}

/** Letras de los días empezando por el inicio de semana: D L M X J V S. */
fun weekdayLetters(weekStart: DayOfWeek): List<String> =
    (0 until 7).map { weekStart.plus(it.toLong()).getDisplayName(TextStyle.NARROW, es).uppercase() }

/** Celda cuadrada redondeada con el número del día; tocable si se pasan acciones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayCell(
    day: LocalDate,
    cell: Cell,
    color: Color,
    modifier: Modifier = Modifier,
    showNumber: Boolean = true,
    highlight: Boolean = false,
    onTap: ((LocalDate) -> Unit)? = null,
    onLongTap: ((LocalDate) -> Unit)? = null,
) {
    val bg = cellColor(cell, color)
    val onColored = cell.kind == CellKind.DONE || cell.kind == CellKind.FAILED || (cell.kind == CellKind.PARTIAL && cell.fraction > 0.5f)
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(
                if (onTap != null || onLongTap != null) Modifier.combinedClickable(
                    onClick = { onTap?.invoke(day) },
                    onLongClick = onLongTap?.let { { it(day) } },
                ) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showNumber) {
            Text(
                day.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    onColored -> Color.Black
                    cell.kind == CellKind.FUTURE || cell.kind == CellKind.SKIP -> Color(0xFF666666)
                    else -> Color(0xFFDDDDDD)
                },
            )
        }
    }
}

/** Calendario de un mes con el estado de cada día. */
@Composable
fun MonthGrid(
    month: YearMonth,
    weekStart: DayOfWeek,
    color: Color,
    cellFor: (LocalDate) -> Cell,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    onTap: ((LocalDate) -> Unit)? = null,
    onLongTap: ((LocalDate) -> Unit)? = null,
) {
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - weekStart.value) + 7) % 7
    val days = month.lengthOfMonth()
    val rows = (lead + days + 6) / 7
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weekdayLetters(weekStart).forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    if (idx < 0 || idx >= days) {
                        Spacer(Modifier.weight(1f).height(34.dp))
                    } else {
                        val d = first.plusDays(idx.toLong())
                        DayCell(
                            d, cellFor(d), color,
                            modifier = Modifier.weight(1f).height(34.dp),
                            highlight = d == selected,
                            onTap = onTap, onLongTap = onLongTap,
                        )
                    }
                }
            }
        }
    }
}

/** Mapa del último año: 53 columnas (semanas) × 7 filas (días). */
@Composable
fun YearHeatmap(
    end: LocalDate,
    weekStart: DayOfWeek,
    color: Color,
    cellFor: (LocalDate) -> Cell,
    modifier: Modifier = Modifier,
) {
    val weeks = 53
    val firstWeek = HabitLogic.weekStartOf(end, weekStart).minusWeeks((weeks - 1).toLong())
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 2.dp
        val cell = (maxWidth - gap * (weeks - 1)) / weeks
        Column {
            Canvas(Modifier.fillMaxWidth().height(cell * 7 + gap * 6)) {
                val c = cell.toPx()
                val g = gap.toPx()
                val r = CornerRadius(c * 0.25f)
                for (w in 0 until weeks) {
                    for (d in 0 until 7) {
                        val day = firstWeek.plusDays((w * 7 + d).toLong())
                        if (day > end) continue
                        drawRoundRect(
                            cellColor(cellFor(day), color),
                            topLeft = Offset(w * (c + g), d * (c + g)),
                            size = Size(c, c),
                            cornerRadius = r,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(monthShort(firstWeek), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(monthShort(end.minusMonths(6)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(monthShort(end), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun monthShort(d: LocalDate) =
    d.month.getDisplayName(TextStyle.SHORT, es).replace(".", "").replaceFirstChar { it.uppercase() } + " ${d.year % 100}"

/** Leyenda de colores para calendarios. */
@Composable
fun CellLegend(
    color: Color,
    showFail: Boolean,
    doneLabel: String = "Cumplido",
    failLabel: String = "Fallado",
    showPartial: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(color, doneLabel)
        if (showPartial) LegendItem(color.copy(alpha = 0.5f), "Parcial")
        if (showFail) LegendItem(FailColor, failLabel)
        if (showPartial) LegendItem(EmptyColor, "Sin registro")
    }
}

@Composable
private fun LegendItem(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Gráfica de barras de una sola serie, con línea de meta opcional.
 * Toca una barra para ver su valor.
 */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    color: Color,
    target: Double?,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    var selected by remember(points) { mutableStateOf(points.lastIndex) }
    val max = maxOf(points.maxOf { it.value }, target ?: 0.0, 1.0) * 1.1
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outline
    Column(modifier) {
        val p = points[selected.coerceIn(0, points.lastIndex)]
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valueLabel(p.value), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(p.label, color = dim, fontSize = 13.sp)
            if (target != null) {
                Spacer(Modifier.weight(1f))
                Text("Meta: ${valueLabel(target)}", color = dim, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).pointerInput(points) {
                detectTapGestures { o ->
                    val slot = size.width / points.size
                    selected = (o.x / slot).toInt().coerceIn(0, points.lastIndex)
                }
            }
        ) {
            val n = points.size
            val slot = size.width / n
            val gap = 2.dp.toPx() + slot * 0.25f
            val barW = slot - gap
            val r = CornerRadius(minOf(4.dp.toPx(), barW / 2))
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            points.forEachIndexed { i, pt ->
                val h = (pt.value / max * size.height).toFloat()
                if (h <= 0f) return@forEachIndexed
                val left = i * slot + gap / 2
                val c = if (i == selected) color else color.copy(alpha = 0.7f)
                // Extremo superior redondeado; la base queda recta sobre el eje.
                drawRoundRect(c, topLeft = Offset(left, size.height - h), size = Size(barW, h), cornerRadius = r)
                val sq = minOf(h, r.y)
                drawRect(c, topLeft = Offset(left, size.height - sq), size = Size(barW, sq))
            }
            if (target != null && target > 0) {
                val y = (size.height - target / max * size.height).toFloat()
                drawLine(
                    dim, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            points.forEachIndexed { i, pt ->
                Text(
                    pt.label, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    fontSize = 10.sp, color = if (i == selected) MaterialTheme.colorScheme.onSurface else dim,
                )
            }
        }
    }
}

/**
 * Gráfica de línea de una sola serie (p. ej. peso), con meta opcional.
 * Toca para ver el valor del punto más cercano.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    color: Color,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    target: Double? = null,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    if (points.size < 2) {
        Text("Registra al menos dos veces para ver la tendencia.", color = dim, fontSize = 13.sp)
        return
    }
    var selected by remember(points) { mutableStateOf(points.lastIndex) }
    val values = points.map { it.value } + listOfNotNull(target)
    val lo0 = values.min()
    val hi0 = values.max()
    val pad = ((hi0 - lo0) * 0.15).coerceAtLeast(0.5)
    val lo = lo0 - pad
    val hi = hi0 + pad
    val grid = MaterialTheme.colorScheme.outline
    Column(modifier) {
        val p = points[selected.coerceIn(0, points.lastIndex)]
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valueLabel(p.value), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(p.label, color = dim, fontSize = 13.sp)
            if (target != null) {
                Spacer(Modifier.weight(1f))
                Text("Meta: ${valueLabel(target)}", color = dim, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).pointerInput(points) {
                detectTapGestures { o ->
                    val step = size.width.toFloat() / (points.size - 1)
                    selected = ((o.x / step) + 0.5f).toInt().coerceIn(0, points.lastIndex)
                }
            }
        ) {
            val step = size.width / (points.size - 1)
            fun y(v: Double) = (size.height - ((v - lo) / (hi - lo)) * size.height).toFloat()
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            if (target != null) {
                drawLine(
                    dim, Offset(0f, y(target)), Offset(size.width, y(target)), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, pt ->
                val x = i * step
                if (i == 0) path.moveTo(x, y(pt.value)) else path.lineTo(x, y(pt.value))
            }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            points.forEachIndexed { i, pt ->
                val c = Offset(i * step, y(pt.value))
                if (i == selected) {
                    drawCircle(Color(0xFF222222), radius = 7.dp.toPx(), center = c)
                    drawCircle(color, radius = 5.dp.toPx(), center = c)
                } else if (points.size <= 31) {
                    drawCircle(color, radius = 3.dp.toPx(), center = c)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(points.first().label, fontSize = 10.sp, color = dim)
            Spacer(Modifier.weight(1f))
            Text(points.last().label, fontSize = 10.sp, color = dim)
        }
    }
}
