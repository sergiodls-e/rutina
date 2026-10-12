package com.rutina.app.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutina.app.data.CalView
import com.rutina.app.data.Task
import com.rutina.app.domain.TaskLogic
import com.rutina.app.ui.common.weekdayLetters
import com.rutina.app.ui.theme.Accent
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val es = Locale("es", "MX")
private val HourH = 56.dp
private val Gutter = 44.dp
private val NowColor = Color(0xFFE57373)
private val GridLine = Color(0xFF2C2C2C)

private fun hourLabel(h: Int): String {
    val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
    return "$h12 ${if (h < 12) "a. m." else "p. m."}"
}

private fun monthLabel(d: LocalDate) =
    d.month.getDisplayName(TextStyle.SHORT, es).replace(".", "").replaceFirstChar { it.uppercase() } + " ${d.year}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    vm: CalendarViewModel,
    onOpenTask: (Long) -> Unit,
    onCreateAt: (LocalDate, Int?) -> Unit,
    onHabitStats: (Long) -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var viewMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var jump by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    var scheduleTask by remember { mutableStateOf<Task?>(null) }

    fun open(e: CalEvent) {
        when (e.kind) {
            EventKind.TASK, EventKind.GHOST -> e.task?.let { onOpenTask(it.id) }
            EventKind.SESSION -> e.habitId?.let(onHabitStats)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Calendario", fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A))
                                .clickable { viewMenu = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.view.label, fontSize = 14.sp)
                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
                        }
                        DropdownMenu(viewMenu, { viewMenu = false }) {
                            CalView.entries.forEach { v ->
                                DropdownMenuItem(text = { Text(v.label) }, onClick = { vm.setView(v); viewMenu = false })
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreHoriz, "Más opciones", tint = Color.White) }
                        DropdownMenu(moreMenu, { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Programar tareas") },
                                leadingIcon = { Icon(Icons.Filled.EventAvailable, null) },
                                onClick = { moreMenu = false; scheduling = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val d = if (LocalDate.now() in s.days) LocalDate.now() else s.anchor
                    val nextHour = ((LocalTime.now().hour + 1).coerceAtMost(23)) * 60
                    onCreateAt(d, nextHour)
                },
                shape = CircleShape, containerColor = Color(0xFFD6D6D6), contentColor = Color.Black,
            ) { Icon(Icons.Filled.Add, "Nueva tarea") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Mes · ‹ Hoy ›
            Row(
                Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A))
                        .clickable { jump = true }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(monthLabel(s.anchor), fontWeight = FontWeight.Medium) }
                NavButton(Icons.Filled.ChevronLeft, "Anterior") { vm.step(false) }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF9E9E9E))
                        .clickable { vm.today() }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("Hoy", color = Color.Black) }
                NavButton(Icons.Filled.ChevronRight, "Siguiente") { vm.step(true) }
            }
            if (s.view == CalView.MONTH) {
                MonthView(s, onDay = { d -> vm.goTo(d); vm.setView(CalView.DAY) }, onOpen = { open(it) })
            } else {
                TimeGrid(
                    s,
                    onTapSlot = { d, m -> onCreateAt(d, m) },
                    onOpen = { open(it) },
                    onMove = { e, d, m -> e.task?.let { vm.move(it, d, m) } },
                    onDayHeader = { d -> vm.goTo(d); vm.setView(CalView.DAY) },
                )
            }
        }
    }

    if (jump) {
        val st = rememberDatePickerState(initialSelectedDateMillis = s.anchor.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { jump = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { vm.goTo(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    jump = false
                }) { Text("Ir") }
            },
            dismissButton = { TextButton(onClick = { jump = false }) { Text("Cancelar") } },
        ) { DatePicker(state = st) }
    }

    if (scheduling) {
        ModalBottomSheet(onDismissRequest = { scheduling = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("Programar tareas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Tareas pendientes sin hora. Elige una para darle día y hora.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (s.unscheduled.isEmpty()) {
                    Text("Todo está programado. 🎉", modifier = Modifier.padding(vertical = 16.dp))
                }
                LazyColumn(Modifier.height(360.dp)) {
                    items(s.unscheduled, key = { it.id }) { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { scheduleTask = t; scheduling = false }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                TaskLogic.dueLabel(t) ?: "Sin fecha",
                                color = if (TaskLogic.isOverdue(t)) NowColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }

    scheduleTask?.let { t ->
        ScheduleDialogs(
            initialDay = TaskLogic.due(t) ?: s.anchor,
            onDone = { d, m -> vm.schedule(t, d, m); scheduleTask = null },
            onCancel = { scheduleTask = null },
        )
    }
}

@Composable
private fun NavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A)).clickable(onClick = onClick).padding(8.dp),
    ) { Icon(icon, label, tint = Color.White) }
}

/** Elegir fecha y luego hora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialogs(initialDay: LocalDate, onDone: (LocalDate, Int) -> Unit, onCancel: () -> Unit) {
    var day by remember { mutableStateOf<LocalDate?>(null) }
    if (day == null) {
        val st = rememberDatePickerState(initialSelectedDateMillis = initialDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = onCancel,
            confirmButton = {
                TextButton(onClick = {
                    day = st.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: initialDay
                }) { Text("Siguiente") }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
        ) { DatePicker(state = st) }
    } else {
        val st = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Hora") },
            text = { TimePicker(state = st) },
            confirmButton = { TextButton(onClick = { onDone(day!!, st.hour * 60 + st.minute) }) { Text("Programar") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------- Cuadrícula por horas

@Composable
private fun TimeGrid(
    s: CalState,
    onTapSlot: (LocalDate, Int) -> Unit,
    onOpen: (CalEvent) -> Unit,
    onMove: (CalEvent, LocalDate, Int) -> Unit,
    onDayHeader: (LocalDate) -> Unit,
) {
    val days = s.days
    val today = LocalDate.now()
    val letters = weekdayLetters(s.weekStart)
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    // Encabezado de días
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(Gutter))
        days.forEach { d ->
            val idx = ((d.dayOfWeek.value - s.weekStart.value) + 7) % 7
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onDayHeader(d) }.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(letters[idx], fontSize = 11.sp, color = dim)
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(if (d == today) Color(0xFF3A3A3A) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(d.dayOfMonth.toString(), fontSize = 16.sp, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }

    // Fila "todo el día"
    if (days.any { s.allDay[it].orEmpty().isNotEmpty() }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("Todo\nel día", Modifier.width(Gutter), fontSize = 9.sp, color = dim, textAlign = TextAlign.Center, lineHeight = 10.sp)
            days.forEach { d ->
                val items = s.allDay[d].orEmpty()
                Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items.take(3).forEach { e -> MiniChip(e) { onOpen(e) } }
                    if (items.size > 3) Text("+${items.size - 3}", fontSize = 10.sp, color = dim, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
    HorizontalDivider(color = GridLine)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val hourPx = with(density) { HourH.toPx() }
        val gutterPx = with(density) { Gutter.toPx() }
        val colPx = (constraints.maxWidth - gutterPx) / days.size
        val scroll = rememberScrollState()
        LaunchedEffect(Unit) {
            scroll.scrollTo(((LocalTime.now().hour - 1).coerceAtLeast(0) * hourPx).toInt())
        }
        var nowMin by remember { mutableLongStateOf(LocalTime.now().toSecondOfDay() / 60L) }
        LaunchedEffect(Unit) { while (true) { delay(30_000); nowMin = LocalTime.now().toSecondOfDay() / 60L } }
        val tap by rememberUpdatedState(onTapSlot)

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(HourH * 24)) {
                Canvas(Modifier.matchParentSize()) {
                    for (h in 1 until 24) {
                        drawLine(GridLine, Offset(gutterPx, h * hourPx), Offset(size.width, h * hourPx), 1f)
                    }
                    for (c in 0..days.size) {
                        val x = gutterPx + c * colPx
                        drawLine(GridLine, Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                }
                for (h in 1 until 24) {
                    Text(
                        hourLabel(h),
                        Modifier.offset(y = HourH * h - 7.dp).width(Gutter).padding(end = 4.dp),
                        fontSize = 9.sp, color = dim, textAlign = TextAlign.End,
                    )
                }
                // Toque en un hueco: crear tarea a esa hora (redondeada a media hora)
                Box(
                    Modifier.matchParentSize().pointerInput(days, colPx) {
                        detectTapGestures { o ->
                            if (o.x < gutterPx) return@detectTapGestures
                            val col = ((o.x - gutterPx) / colPx).toInt().coerceIn(0, days.lastIndex)
                            val minute = ((o.y / hourPx) * 60).toInt() / 30 * 30
                            tap(days[col], minute.coerceIn(0, 23 * 60 + 30))
                        }
                    }
                )
                days.forEachIndexed { ci, d ->
                    s.timed[d].orEmpty().forEach { p ->
                        EventBlock(
                            p = p, colIndex = ci, colPx = colPx, gutterPx = gutterPx, hourPx = hourPx,
                            daysCount = days.size,
                            onOpen = { onOpen(p.event) },
                            onDrop = { dDays, newMin ->
                                val target = (ci + dDays).coerceIn(0, days.lastIndex)
                                onMove(p.event, days[target], newMin)
                            },
                        )
                    }
                }
                // Línea de la hora actual
                val ti = days.indexOf(today)
                if (ti >= 0) {
                    val y = (nowMin / 60f * hourPx).roundToInt()
                    val x = (gutterPx + ti * colPx).roundToInt()
                    Box(
                        Modifier.offset { IntOffset(x, y - 2) }.width(with(density) { colPx.toDp() }).height(2.dp)
                            .background(NowColor).zIndex(2f)
                    )
                    Box(
                        Modifier.offset { IntOffset(x - 10, y - 12) }.size(8.dp).clip(CircleShape)
                            .background(NowColor).zIndex(2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EventBlock(
    p: Placed,
    colIndex: Int,
    colPx: Float,
    gutterPx: Float,
    hourPx: Float,
    daysCount: Int,
    onOpen: () -> Unit,
    onDrop: (dayDelta: Int, newMinute: Int) -> Unit,
) {
    val e = p.event
    val density = LocalDensity.current
    val start = e.startMin ?: 0
    val laneW = colPx / p.lanes
    val x = gutterPx + colIndex * colPx + p.lane * laneW + 1f
    val y = start / 60f * hourPx + 1f
    val hPx = maxOf(e.durMin / 60f * hourPx - 2f, with(density) { 20.dp.toPx() })
    var drag by remember(e.key) { mutableStateOf(Offset.Zero) }
    var dragging by remember(e.key) { mutableStateOf(false) }
    val drop by rememberUpdatedState(onDrop)

    val color = Color(e.color)
    val ghost = e.kind == EventKind.GHOST
    val session = e.kind == EventKind.SESSION
    val bg = when {
        ghost -> color.copy(alpha = 0.18f)
        session -> color.copy(alpha = 0.45f)
        e.done -> color.copy(alpha = 0.35f)
        else -> color
    }
    val minuteNow = (start + (drag.y / hourPx * 60).roundToInt() / 15 * 15).coerceIn(0, 24 * 60 - 15)

    Column(
        Modifier
            .offset { IntOffset((x + drag.x).roundToInt(), (y + drag.y).roundToInt()) }
            .zIndex(if (dragging) 3f else 1f)
            .width(with(density) { (laneW - 2f).toDp() })
            .height(with(density) { hPx.toDp() })
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .then(if (ghost) Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(5.dp)) else Modifier)
            .then(
                if (e.movable) Modifier.pointerInput(e.key, colPx, hourPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            val dd = (drag.x / colPx).roundToInt().coerceIn(-colIndex, daysCount - 1 - colIndex)
                            val dm = (drag.y / hourPx * 60).roundToInt() / 15 * 15
                            val newMin = (start + dm).coerceIn(0, 24 * 60 - 15)
                            drag = Offset.Zero
                            dragging = false
                            if (dd != 0 || dm != 0) drop(dd, newMin)
                        },
                        onDragCancel = { drag = Offset.Zero; dragging = false },
                        onDrag = { change, amount -> change.consume(); drag += amount },
                    )
                } else Modifier
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        val fg = if (ghost || session) Color.White else Color.Black
        Text(
            e.title, fontSize = 11.sp, lineHeight = 12.sp, color = fg, maxLines = if (hPx > 60) 3 else 1,
            overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
            textDecoration = if (e.done) TextDecoration.LineThrough else null,
        )
        if (dragging) {
            Text(TaskLogic.timeLabel(minuteNow), fontSize = 10.sp, color = fg)
        } else if (hPx > 44 && e.startMin != null) {
            Text(TaskLogic.timeLabel(e.startMin), fontSize = 10.sp, color = fg.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

@Composable
private fun MiniChip(e: CalEvent, onClick: () -> Unit) {
    val color = Color(e.color)
    val ghost = e.kind == EventKind.GHOST
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(if (ghost) color.copy(alpha = 0.2f) else if (e.done) color.copy(alpha = 0.35f) else color)
            .clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Text(
            e.title, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (ghost) Color.White else Color.Black,
            textDecoration = if (e.done) TextDecoration.LineThrough else null,
        )
    }
}

// ---------------------------------------------------------------- Vista mes

@Composable
private fun MonthView(s: CalState, onDay: (LocalDate) -> Unit, onOpen: (CalEvent) -> Unit) {
    val today = LocalDate.now()
    val month = YearMonth.from(s.anchor)
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            weekdayLetters(s.weekStart).forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = dim)
            }
        }
        s.days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { d ->
                    val inMonth = YearMonth.from(d) == month
                    val items = s.allDay[d].orEmpty() + s.timed[d].orEmpty().map { it.event }.sortedBy { it.startMin }
                    Column(
                        Modifier.weight(1f).fillMaxSize().border(0.5.dp, GridLine).clickable { onDay(d) }.padding(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).size(22.dp).clip(CircleShape)
                                .background(if (d == today) Accent else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                d.dayOfMonth.toString(), fontSize = 12.sp,
                                color = when { d == today -> Color.Black; inMonth -> MaterialTheme.colorScheme.onSurface; else -> Color(0xFF555555) },
                            )
                        }
                        items.take(3).forEach { e -> MiniChip(e) { onOpen(e) } }
                        if (items.size > 3) Text("+${items.size - 3}", fontSize = 9.sp, color = dim)
                    }
                }
            }
        }
    }
}
