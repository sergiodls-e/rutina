package com.rutina.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.TaskLogic
import com.rutina.app.ui.journal.userNote
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ¿Se puede cambiar el valor del registro a mano? */
fun valueEditable(h: Habit) = h.type == TrackingType.COUNT || h.type == TrackingType.WEIGHT

/** ¿Se puede editar este registro? (los de Diario salen de las notas y los de Salud se sincronizan solos). */
fun logEditable(h: Habit) = h.type != TrackingType.JOURNAL && h.type != TrackingType.HEALTH

/**
 * Crear o corregir un registro: valor, fecha, hora y nota.
 * [log] null = registro nuevo (por ejemplo, de otro día).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogEditDialog(
    habit: Habit,
    log: HabitLog?,
    onDismiss: () -> Unit,
    onSave: (HabitLog) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val start = log?.let { Instant.ofEpochMilli(it.timestamp).atZone(HabitLogic.zone).toLocalDateTime() } ?: LocalDateTime.now()
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime().withSecond(0).withNano(0)) }
    var value by remember { mutableStateOf(HabitLogic.formatNumber(log?.value ?: habit.step)) }
    var note by remember { mutableStateOf(log?.userNote().orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val es = Locale("es", "MX")
    val parsed = value.replace(',', '.').toDoubleOrNull()
    val valid = !valueEditable(habit) || (parsed != null && parsed > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (log == null) "Nuevo registro" else if (habit.type == TrackingType.QUIT) "Recaída" else "Registro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (valueEditable(habit)) {
                    OutlinedTextField(
                        value, { value = it }, singleLine = true,
                        label = { Text("Cantidad (${if (habit.type == TrackingType.WEIGHT) habit.unit else habit.unitPlural.ifBlank { habit.unit }})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = !valid,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }, Modifier.weight(1f)) {
                        Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", es)).replace(".", ""))
                    }
                    OutlinedButton(onClick = { pickTime = true }, Modifier.weight(1f)) {
                        Text(TaskLogic.timeLabel(time.hour * 60 + time.minute))
                    }
                }
                if (habit.type != TrackingType.MOOD) {
                    OutlinedTextField(note, { note = it }, label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth())
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Eliminar registro", color = MaterialTheme.colorScheme.error) }
                }
                if (date > LocalDate.now()) Text("La fecha está en el futuro.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = valid && date <= LocalDate.now(), onClick = {
                val ts = date.atTime(time).atZone(HabitLogic.zone).toInstant().toEpochMilli()
                val keepMarker = if (habit.type == TrackingType.QUIT) "reinicio" else log?.note?.takeIf { it == com.rutina.app.health.HealthSync.NOTE }
                val base = log ?: HabitLog(habitId = habit.id, timestamp = ts, value = 1.0)
                onSave(
                    base.copy(
                        timestamp = ts,
                        value = if (valueEditable(habit)) parsed ?: base.value else base.value,
                        note = if (habit.type == TrackingType.MOOD) base.note else note.trim().ifBlank { keepMarker },
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state = st) }
    }
    if (pickTime) {
        val st = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Hora") },
            text = { TimePicker(state = st) },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(st.hour, st.minute); pickTime = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancelar") } },
        )
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar este registro?") },
            text = { Text("Se quitará del progreso y de la racha.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
