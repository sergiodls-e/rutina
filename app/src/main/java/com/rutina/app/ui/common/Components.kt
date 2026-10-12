package com.rutina.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Fila de formulario: etiqueta a la izquierda, control a la derecha, separador abajo. */
@Composable
fun FormRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        content()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/** Botón gris redondeado (estilo de los selectores de la app). */
@Composable
fun Pill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun <T> Dropdown(current: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill(onClick = { open = true }) { Text(current); Spacer(Modifier.width(8.dp)); Icon(Icons.Filled.KeyboardArrowDown, null) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onPick(value); open = false })
            }
        }
    }
}
