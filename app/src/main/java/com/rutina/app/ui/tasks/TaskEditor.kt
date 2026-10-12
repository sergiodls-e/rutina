package com.rutina.app.ui.tasks

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.RepeatRule
import com.rutina.app.data.Task
import com.rutina.app.data.TaskList
import com.rutina.app.domain.TaskLogic
import com.rutina.app.domain.SmartInput
import com.rutina.app.data.TaskOps
import com.rutina.app.data.series
import com.rutina.app.ui.today.SmartChips
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.rutina.app.ui.common.Dropdown
import com.rutina.app.ui.common.FormRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Colores de las banderas de prioridad: ninguna, baja, media, alta. */
val PriorityColors = listOf(Color(0xFF888888), Color(0xFF8EC5F0), Color(0xFFF6D98E), Color(0xFFF28B95))

class TaskEditorViewModel(
    private val db: AppDatabase,
    private val editId: Long,
    initial: Task,
) : ViewModel() {
    private val _task = MutableStateFlow(initial)
    val task: StateFlow<Task> = _task
    val isEdit = editId > 0
    val lists: StateFlow<List<TaskList>> =
        db.tasks().observeLists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _history = MutableStateFlow<List<Task>>(emptyList())
    /** Repeticiones anteriores de esta tarea (completadas u omitidas). */
    val history: StateFlow<List<Task>> = _history

    init {
        if (isEdit) viewModelScope.launch {
            db.tasks().get(editId)?.let { t ->
                _task.value = t
                db.tasks().observeSeries(t.series).collect { all -> _history.value = all.filter { it.done && it.id != editId } }
            }
        }
    }

    /** Completa o marca "No lo haré" guardando también los cambios hechos. */
    fun close(skip: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        val t = _task.value.copy(title = _task.value.title.trim(), description = _task.value.description.trim())
        if (skip) TaskOps.skip(db, t) else TaskOps.complete(db, t)
        onDone()
    }

    fun reopen(onDone: () -> Unit) = viewModelScope.launch {
        TaskOps.reopen(db, _task.value)
        onDone()
    }

    fun update(f: (Task) -> Task) { _task.value = f(_task.value) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val t = _task.value.copy(title = _task.value.title.trim(), description = _task.value.description.trim())
        if (isEdit) db.tasks().update(t) else db.tasks().insert(t)
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        if (isEdit) db.tasks().delete(_task.value)
        onDone()
    }
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.utcToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(vm: TaskEditorViewModel, smart: Boolean = false, onClose: () -> Unit) {
    val t by vm.task.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var pickUntil by remember { mutableStateOf(false) }
    val lists by vm.lists.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val askNotif = com.rutina.app.ui.common.rememberNotificationPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Editar tarea" else "Nueva tarea", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().imePadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onClose, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancelar") }
                Button(
                    onClick = {
                        if (t.title.isBlank()) Toast.makeText(ctx, "La tarea necesita un título.", Toast.LENGTH_SHORT).show()
                        else vm.save(onClose)
                    },
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD6D6D6), contentColor = Color.Black),
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    t.title, { v -> vm.update { it.copy(title = v) } },
                    label = { Text("¿Qué quieres hacer?") }, modifier = Modifier.fillMaxWidth(),
                )
                if (smart && !vm.isEdit) {
                    val parsed = remember(t.title) { SmartInput.parse(t.title) }
                    if (parsed.found) Row(verticalAlignment = Alignment.CenterVertically) {
                        SmartChips(parsed, Modifier.weight(1f))
                        TextButton(onClick = { vm.update { parsed.applyTo(it) } }) { Text("Aplicar") }
                    }
                }
                OutlinedTextField(
                    t.description, { v -> vm.update { it.copy(description = v) } },
                    label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            FormRow("Fecha") {
                Dropdown(
                    current = TaskLogic.dueLabel(t.copy(dueMinutes = null), today) ?: "Ninguna",
                    options = listOf(
                        "Ninguna" to -1L, "Hoy" to today.toEpochDay(), "Mañana" to today.plusDays(1).toEpochDay(),
                        "Próxima semana" to today.plusWeeks(1).toEpochDay(), "Elegir fecha…" to -2L,
                    ),
                ) { v ->
                    when (v) {
                        -1L -> vm.update { it.copy(dueEpochDay = null, dueMinutes = null, repeat = RepeatRule.NEVER) }
                        -2L -> pickDate = true
                        else -> vm.update { it.copy(dueEpochDay = v) }
                    }
                }
            }
            FormRow("Hora") {
                Dropdown(
                    current = t.dueMinutes?.let(TaskLogic::timeLabel) ?: "Ninguna",
                    options = listOf("Ninguna" to false, "Elegir hora…" to true),
                ) { pick ->
                    if (pick) pickTime = true else vm.update { it.copy(dueMinutes = null) }
                }
            }
            if (t.dueMinutes != null) {
                FormRow("Duración") {
                    Dropdown(
                        current = durationLabel(t.durationMin),
                        options = listOf(15, 30, 45, 60, 90, 120, 180, 240).map { durationLabel(it) to it },
                    ) { m -> vm.update { it.copy(durationMin = m) } }
                }
            }
            FormRow("Repetir") {
                Dropdown(
                    current = TaskLogic.repeatLabel(t.repeat),
                    options = RepeatRule.entries.map { TaskLogic.repeatLabel(it) to it },
                ) { r ->
                    // Repetir necesita una fecha base: si no hay, empieza hoy.
                    vm.update {
                        it.copy(
                            repeat = r, repeatEvery = 1, repeatDays = 0,
                            dueEpochDay = it.dueEpochDay ?: if (r != RepeatRule.NEVER) today.toEpochDay() else null,
                        )
                    }
                }
            }
            if (t.repeat in setOf(RepeatRule.DAILY, RepeatRule.WEEKLY, RepeatRule.MONTHLY, RepeatRule.YEARLY)) {
                FormRow("Cada") {
                    val unit = when (t.repeat) {
                        RepeatRule.DAILY -> "día" to "días"
                        RepeatRule.WEEKLY -> "semana" to "semanas"
                        RepeatRule.MONTHLY -> "mes" to "meses"
                        else -> "año" to "años"
                    }
                    fun label(n: Int) = "$n ${if (n == 1) unit.first else unit.second}"
                    Dropdown(label(t.repeatEvery), (1..12).map { label(it) to it } + listOf(label(14) to 14, label(30) to 30)) { n ->
                        vm.update { it.copy(repeatEvery = n) }
                    }
                }
                if (t.repeat == RepeatRule.WEEKLY) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, d ->
                            val on = t.repeatDays and (1 shl i) != 0
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                    .background(if (on) Color(0xFF9FA8DA) else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { vm.update { it.copy(repeatDays = it.repeatDays xor (1 shl i)) } },
                                contentAlignment = Alignment.Center,
                            ) { Text(d, color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Text(
                        if (t.repeatDays == 0) "Sin días marcados: se repite el mismo día de la semana que la fecha." else "Se repite en los días marcados.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (t.repeat != RepeatRule.NEVER) {
                FormRow("Termina") {
                    Dropdown(
                        current = t.repeatUntil?.let { "El " + (TaskLogic.dueLabel(Task(title = "", dueEpochDay = it), today) ?: "") } ?: "Nunca",
                        options = listOf("Nunca" to false, "Elegir fecha…" to true),
                    ) { pick -> if (pick) pickUntil = true else vm.update { it.copy(repeatUntil = null) } }
                }
                Text(
                    TaskLogic.repeatLabel(t),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            FormRow("Prioridad") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..3).forEach { p ->
                        IconButton(onClick = { vm.update { it.copy(priority = p) } }) {
                            Icon(
                                if (t.priority == p) Icons.Filled.Flag else Icons.Outlined.Flag,
                                TaskLogic.priorityLabel(p), tint = PriorityColors[p],
                                modifier = Modifier.size(if (t.priority == p) 28.dp else 22.dp),
                            )
                        }
                    }
                }
            }
            FormRow("Lista") {
                Dropdown(
                    current = lists.firstOrNull { it.id == t.listId }?.name ?: "Ninguna",
                    options = listOf<Pair<String, Long?>>("Ninguna" to null) + lists.map { it.name to it.id },
                ) { id -> vm.update { it.copy(listId = id) } }
            }
            FormRow("Notificación") {
                Switch(
                    checked = t.notify,
                    onCheckedChange = { on -> if (on) askNotif(); vm.update { it.copy(notify = on) } },
                    enabled = t.dueEpochDay != null && t.dueMinutes != null,
                )
            }
            Text(
                if (t.dueEpochDay == null || t.dueMinutes == null) "Para avisarte, la tarea necesita fecha y hora."
                else if (t.notify) "Te avisaremos a la hora de la tarea, con un botón para completarla."
                else "Activa para recibir un aviso a la hora de la tarea.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            if (vm.isEdit) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!t.done) {
                        OutlinedButton(onClick = { vm.close(skip = false, onDone = onClose) }, Modifier.weight(1f)) { Text("Completar") }
                        OutlinedButton(onClick = { vm.close(skip = true, onDone = onClose) }, Modifier.weight(1f)) { Text("No lo haré") }
                    } else {
                        OutlinedButton(onClick = { vm.reopen(onClose) }, Modifier.weight(1f)) {
                            Text(if (t.skipped) "Omitida · volver a pendiente" else "Completada · volver a pendiente")
                        }
                    }
                }
                if (history.isNotEmpty()) {
                    Text(
                        "Historial de repeticiones", fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
                    )
                    history.take(30).forEach { h ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (h.skipped) "⊘" else "✓", color = if (h.skipped) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFA5D6A7), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Text(TaskLogic.dueLabel(h.copy(dueMinutes = null), today) ?: "Sin fecha", Modifier.weight(1f))
                            Text(
                                (if (h.skipped) "Omitida" else "Hecha") + (h.doneAt?.let { " · " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { d -> TaskLogic.dueLabel(Task(title = "", dueEpochDay = d.toEpochDay()), today) } } ?: ""),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val done = history.count { !it.skipped }
                    Text(
                        "$done hechas · ${history.size - done} omitidas",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                OutlinedButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Eliminar tarea", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (TaskLogic.due(t) ?: today).toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms -> vm.update { it.copy(dueEpochDay = ms.utcToDate().toEpochDay()) } }
                    pickDate = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state = st) }
    }

    if (pickUntil) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = (t.repeatUntil?.let(LocalDate::ofEpochDay) ?: (TaskLogic.due(t) ?: today).plusMonths(1)).toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickUntil = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms -> vm.update { it.copy(repeatUntil = ms.utcToDate().toEpochDay()) } }
                    pickUntil = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickUntil = false }) { Text("Cancelar") } },
        ) { DatePicker(state = st) }
    }

    if (pickTime) {
        val m = t.dueMinutes ?: (9 * 60)
        val st = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Hora") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    vm.update { it.copy(dueMinutes = st.hour * 60 + st.minute, dueEpochDay = it.dueEpochDay ?: today.toEpochDay()) }
                    pickTime = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar esta tarea?") },
            text = { Text(t.title) },
            confirmButton = { TextButton(onClick = { vm.delete(onClose) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private fun durationLabel(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}
