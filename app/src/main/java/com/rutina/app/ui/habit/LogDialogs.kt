package com.rutina.app.ui.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.domain.Mood
import com.rutina.app.domain.Weight
import com.rutina.app.ui.theme.Accent

/** Registrar estado de ánimo: nivel, emociones y nota. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodDialog(onDismiss: () -> Unit, onSave: (level: Int, note: String?) -> Unit) {
    var level by remember { mutableIntStateOf(0) }
    val emotions = remember { mutableStateListOf<String>() }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Cómo te sientes?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Mood.levels.forEach { l ->
                        val sel = level == l.value
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .then(if (sel) Modifier.background(Accent.copy(alpha = 0.25f)).border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { level = l.value }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(l.emoji, fontSize = 30.sp)
                            Text(
                                l.label, fontSize = 11.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text("Emociones (opcional)", fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Mood.emotions.forEach { e ->
                        FilterChip(
                            selected = e in emotions,
                            onClick = { if (e in emotions) emotions.remove(e) else emotions.add(e) },
                            label = { Text(e) },
                        )
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Nota (opcional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = level > 0, onClick = {
                val text = listOfNotNull(
                    emotions.joinToString(", ").ifBlank { null },
                    note.trim().ifBlank { null },
                ).joinToString(" · ").ifBlank { null }
                onSave(level, text)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Registrar peso corporal. */
@Composable
fun WeightDialog(unit: String, last: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(last?.let(Weight::format) ?: "") }
    val value = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar peso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { text = it },
                    label = { Text("Peso") },
                    suffix = { Text(unit.ifBlank { "kg" }) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (last != null && value != null && value > 0) {
                    Text(
                        "Cambio desde el último: ${Weight.delta(value - last)} ${unit.ifBlank { "kg" }}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0 && value < 1000, onClick = { value?.let(onSave) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
