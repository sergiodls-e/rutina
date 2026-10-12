package com.rutina.app.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutina.app.data.Habit
import com.rutina.app.data.Task
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale("es", "MX")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onNewHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var fabOpen by remember { mutableStateOf(false) }
    var showTaskSheet by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<HabitRow?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.date == LocalDate.now()) "Hoy" else state.date.format(
                            java.time.format.DateTimeFormatter.ofPattern(
                                if (state.date.year == LocalDate.now().year) "EEEE d 'de' MMMM" else "EEEE d 'de' MMMM yyyy", es,
                            )
                        ).replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { vm.select(LocalDate.now()) },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(fabOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                        FabOption("Tarea", "Crea una lista de pendientes", Icons.Filled.TaskAlt) {
                            fabOpen = false; showTaskSheet = true
                        }
                        FabOption("Hábito", "Registra una actividad recurrente", Icons.Filled.Spa) {
                            fabOpen = false; onNewHabit()
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { fabOpen = !fabOpen },
                    shape = CircleShape,
                    containerColor = Color(0xFFD6D6D6),
                    contentColor = Color.Black,
                ) { Icon(if (fabOpen) Icons.Filled.Close else Icons.Filled.Add, contentDescription = "Crear") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            WeekStrip(state.date, vm) { vm.select(it) }
            if (state.loaded && state.habits.isEmpty() && state.tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No hay tareas ni hábitos", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("Toca + para añadir", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.habits.isNotEmpty()) {
                        item { SectionHeader("Hábitos", state.habits.size) }
                        items(state.habits, key = { "h" + it.habit.id }) { row ->
                            HabitCard(
                                row = row,
                                onPrimary = {
                                    if (row.habit.type == TrackingType.QUIT) actionsFor = row else vm.add(row.habit)
                                },
                                onLong = { actionsFor = row },
                            )
                        }
                    }
                    if (state.tasks.isNotEmpty()) {
                        item { SectionHeader("Lista de pendientes", state.tasks.size) }
                        items(state.tasks, key = { "t" + it.id }) { t -> TaskRow(t) { vm.toggleTask(t) } }
                    }
                }
            }
        }
    }

    if (showTaskSheet) {
        QuickTaskSheet(
            onDismiss = { showTaskSheet = false },
            onSave = { title, desc, today -> vm.addTask(title, desc, today); showTaskSheet = false },
        )
    }

    actionsFor?.let { row ->
        HabitActionsDialog(
            row = row,
            onDismiss = { actionsFor = null },
            onAdd = { vm.add(row.habit, it); actionsFor = null },
            onUndo = { vm.undo(row.habit); actionsFor = null },
            onReset = { vm.resetQuit(row.habit); actionsFor = null },
            onEdit = { actionsFor = null; onEditHabit(row.habit.id) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekStrip(selected: LocalDate, vm: TodayViewModel, onSelect: (LocalDate) -> Unit) {
    val start = HabitLogic.weekStartOf(selected, vm.weekStart)
    val end = start.plusDays(6)
    val today = LocalDate.now()
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().background(Color.Black)) {
        // Mes y año de la semana visible (si cruza de mes o de año, se muestran ambos)
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))
                    .clickable { showPicker = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(monthYearLabel(start, end), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.CalendarMonth, "Elegir fecha", Modifier.size(16.dp), tint = Color(0xFFB8B8B8))
            }
            Spacer(Modifier.weight(1f))
            if (selected != today) {
                TextButton(onClick = { onSelect(today) }) { Text("Hoy") }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = { onSelect(selected.minusWeeks(1)) }, modifier = Modifier.size(28.dp).align(Alignment.CenterVertically)) {
                Text("‹", fontSize = 22.sp)
            }
            (0 until 7).forEach { i ->
                val d = start.plusDays(i.toLong())
                val isSel = d == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF333333))
                        .then(if (isSel) Modifier.border(2.dp, Color(0xFF777777), RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { onSelect(d) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, es).replace(".", "").replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp,
                        color = if (d == today) Color(0xFF8FD3C1) else Color(0xFFB8B8B8),
                    )
                    Text(d.dayOfMonth.toString(), fontSize = 22.sp, color = Color.White)
                }
            }
            IconButton(onClick = { onSelect(selected.plusWeeks(1)) }, modifier = Modifier.size(28.dp).align(Alignment.CenterVertically)) {
                Text("›", fontSize = 22.sp)
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("Ir") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = pickerState) }
    }
}

/** "Octubre 2026", "Sep – Oct 2026" o "Dic 2026 – Ene 2027". */
private fun monthYearLabel(start: LocalDate, end: LocalDate): String {
    fun full(d: LocalDate) = d.month.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }
    fun short(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, es).replace(".", "").replaceFirstChar { it.uppercase() }
    return when {
        start.month == end.month -> "${full(start)} ${start.year}"
        start.year == end.year -> "${short(start)} – ${short(end)} ${end.year}"
        else -> "${short(start)} ${start.year} – ${short(end)} ${end.year}"
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(row: HabitRow, onPrimary: () -> Unit, onLong: () -> Unit) {
    val h = row.habit
    val color = Color(h.color)
    val isQuit = h.type == TrackingType.QUIT

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (isQuit) LaunchedEffect(h.id) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onLong, onLongClick = onLong),
    ) {
        // Relleno de progreso
        val frac = if (isQuit) 1f else row.fraction
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(color.copy(alpha = 0.18f)))

        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.widthIn(min = 80.dp, max = 150.dp).clip(RoundedCornerShape(8.dp)).background(color)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    h.name, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, fontSize = 14.sp,
                    textDecoration = if (row.met) TextDecoration.LineThrough else null,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.streak.toString(), fontSize = 14.sp)
                Icon(
                    Icons.Filled.LocalFireDepartment, null, Modifier.size(14.dp),
                    tint = if (row.streak > 0) Color(0xFFFF8A50) else Color.Gray,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (isQuit) HabitLogic.formatElapsed(now - (h.quitSince ?: h.createdAt))
                else "${HabitLogic.formatNumber(row.progress)} / ${HabitLogic.formatNumber(h.target)} ${h.unit}",
                fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 140.dp),
            )
            Spacer(Modifier.width(8.dp))
            val filled = row.met
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (filled) color else Color.Transparent)
                    .border(2.dp, if (filled) color else Color(0xFF555555), RoundedCornerShape(10.dp))
                    .clickable(onClick = onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        isQuit -> Icons.Filled.Refresh
                        filled -> Icons.Filled.Check
                        else -> Icons.Filled.Add
                    },
                    contentDescription = "Registrar",
                    tint = if (filled) Color.Black else Color.White,
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.TaskAlt, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, textDecoration = if (task.done) TextDecoration.LineThrough else null)
            if (task.description.isNotBlank()) {
                Text(task.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Checkbox(checked = task.done, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun FabOption(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2A2A2A),
        modifier = Modifier.width(300.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(subtitle, fontSize = 13.sp, color = Color(0xFFDDDDDD))
            }
            Text("›", fontSize = 22.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTaskSheet(onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var today by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("¿Qué quieres hacer?") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = today, onClick = { today = !today }, label = { Text(if (today) "Hoy" else "Sin fecha") })
                Spacer(Modifier.weight(1f))
                FloatingActionButton(
                    onClick = { if (title.isNotBlank()) onSave(title, desc, today) },
                    shape = CircleShape, containerColor = Color(0xFFD6D6D6), contentColor = Color.Black,
                ) { Text("↑", fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun HabitActionsDialog(
    row: HabitRow,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit,
) {
    val h: Habit = row.habit
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(h.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (h.type == TrackingType.QUIT) {
                    Text("¿Recaíste? Reiniciar pone el contador en cero y queda registrado en tu historial.")
                } else {
                    Text("Progreso: ${HabitLogic.formatNumber(row.progress)} / ${HabitLogic.formatNumber(h.target)} ${h.unit}")
                    OutlinedTextField(
                        amount, { amount = it.replace(',', '.') },
                        label = { Text("Añadir cantidad (${h.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    TextButton(onClick = onUndo) { Text("Deshacer último registro") }
                }
                TextButton(onClick = onEdit) { Text("Editar hábito") }
            }
        },
        confirmButton = {
            if (h.type == TrackingType.QUIT) {
                TextButton(onClick = onReset) { Text("Reiniciar contador", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = { amount.toDoubleOrNull()?.let(onAdd) ?: onAdd(1.0) }) {
                    Text(if (amount.isBlank()) "Añadir 1" else "Añadir")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
