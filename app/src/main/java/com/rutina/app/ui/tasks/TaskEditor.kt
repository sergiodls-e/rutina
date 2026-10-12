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

    init {
        if (isEdit) viewModelScope.launch { db.tasks().get(editId)?.let { _task.value = it } }
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
fun TaskEditorScreen(vm: TaskEditorViewModel, onClose: () -> Unit) {
    val t by vm.task.collectAsStateWithLifecycle()
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
                    vm.update { it.copy(repeat = r, dueEpochDay = it.dueEpochDay ?: if (r != RepeatRule.NEVER) today.toEpochDay() else null) }
                }
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
