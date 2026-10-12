package com.rutina.app.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.data.RepeatRule
import com.rutina.app.data.Task
import com.rutina.app.data.TaskGroupBy
import com.rutina.app.data.TaskList
import com.rutina.app.data.TaskSortBy
import com.rutina.app.domain.GroupKind
import com.rutina.app.domain.Parsed
import com.rutina.app.domain.TaskGroup
import com.rutina.app.domain.TaskLogic
import com.rutina.app.ui.tasks.ListIcons
import com.rutina.app.ui.tasks.PriorityColors
import com.rutina.app.ui.theme.Accent
import java.time.LocalDate

// ---------------------------------------------------------------- fila de tarea

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskRow(
    task: Task,
    list: TaskList?,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLong: () -> Unit,
) {
    val overdue = TaskLogic.isOverdue(task)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF3A3F5A) else MaterialTheme.colorScheme.surfaceContainer)
            .then(if (selected) Modifier.border(1.5.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onOpen, onLongClick = onLong)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(list?.let { Color(it.color) } ?: Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) Icons.Filled.Check else list?.let { ListIcons.of(it.icon) } ?: Icons.Filled.TaskAlt,
                null, Modifier.size(18.dp), tint = Color.Black,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
            )
            val parts = listOfNotNull(
                if (task.skipped) "Omitida" else null,
                TaskLogic.dueLabel(task),
                if (task.repeat != RepeatRule.NEVER) "↻" else null,
                list?.name,
            )
            if (parts.isNotEmpty() || task.description.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (parts.isNotEmpty()) {
                        Text(
                            parts.joinToString(" · "), fontSize = 12.sp,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (task.description.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Notes, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (task.priority > 0) Icon(Icons.Filled.Flag, null, Modifier.size(18.dp), tint = PriorityColors[task.priority])
        if (task.skipped) {
            IconButton(onClick = onToggle) { Icon(Icons.Filled.Block, "Volver a pendiente", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
        }
    }
}

// ---------------------------------------------------------------- encabezado de sección

@Composable
internal fun TaskGroupHeader(
    group: TaskGroup,
    collapsed: Boolean,
    onCollapse: () -> Unit,
    onPostpone: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (group.kind == GroupKind.DONE) Modifier.clickable(onClick = onCollapse) else Modifier)
            .padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (group.kind) {
                GroupKind.OVERDUE -> Icons.Filled.Event
                GroupKind.DONE -> Icons.Filled.CheckCircle
                else -> Icons.Filled.TaskAlt
            },
            null, Modifier.size(18.dp),
            tint = if (group.kind == GroupKind.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            group.title, Modifier.weight(1f), fontWeight = FontWeight.Medium,
            color = if (group.kind == GroupKind.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onPostpone != null) {
            TextButton(onClick = onPostpone) { Text("Posponer a hoy") }
        }
        Text("${group.tasks.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
        if (group.kind == GroupKind.DONE) {
            Icon(if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, if (collapsed) "Mostrar" else "Ocultar")
        }
    }
}

// ---------------------------------------------------------------- barra de selección

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionBar(
    count: Int,
    lists: List<TaskList>,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onMove: (LocalDate?) -> Unit,
    onPickDate: () -> Unit,
    onList: (Long?) -> Unit,
    onPriority: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var dateMenu by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var prioMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cancelar selección", tint = Color.White) } },
        title = { Text("$count", fontWeight = FontWeight.Bold) },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, "Seleccionar todo", tint = Color.White) }
            IconButton(onClick = onComplete) { Icon(Icons.Filled.DoneAll, "Completar", tint = Color.White) }
            IconButton(onClick = onSkip) { Icon(Icons.Filled.Block, "No lo haré", tint = Color.White) }
            Box {
                IconButton(onClick = { dateMenu = true }) { Icon(Icons.Filled.Event, "Cambiar fecha", tint = Color.White) }
                DropdownMenu(dateMenu, { dateMenu = false }) {
                    listOf<Pair<String, LocalDate?>>(
                        "Hoy" to today, "Mañana" to today.plusDays(1), "Próxima semana" to today.plusWeeks(1), "Sin fecha" to null,
                    ).forEach { (l, d) -> DropdownMenuItem(text = { Text(l) }, onClick = { dateMenu = false; onMove(d) }) }
                    DropdownMenuItem(text = { Text("Elegir fecha…") }, onClick = { dateMenu = false; onPickDate() })
                }
            }
            Box {
                IconButton(onClick = { listMenu = true }) { Icon(Icons.Filled.FormatListBulleted, "Mover a lista", tint = Color.White) }
                DropdownMenu(listMenu, { listMenu = false }) {
                    DropdownMenuItem(text = { Text("Sin lista") }, onClick = { listMenu = false; onList(null) })
                    lists.forEach { l -> DropdownMenuItem(text = { Text(l.name) }, onClick = { listMenu = false; onList(l.id) }) }
                }
            }
            Box {
                IconButton(onClick = { prioMenu = true }) { Icon(Icons.Filled.Flag, "Prioridad", tint = Color.White) }
                DropdownMenu(prioMenu, { prioMenu = false }) {
                    (3 downTo 0).forEach { p ->
                        DropdownMenuItem(
                            text = { Text(TaskLogic.priorityLabel(p)) },
                            leadingIcon = { Icon(Icons.Filled.Flag, null, tint = PriorityColors[p]) },
                            onClick = { prioMenu = false; onPriority(p) },
                        )
                    }
                }
            }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Eliminar", tint = Color.White) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2A2F45)),
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar $count ${if (count == 1) "tarea" else "tareas"}?") },
            text = { Text("No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------- agrupar / ordenar

@Composable
internal fun SortMenuButton(
    groupBy: TaskGroupBy,
    sortBy: TaskSortBy,
    onGroup: (TaskGroupBy) -> Unit,
    onSort: (TaskSortBy) -> Unit,
    onReorder: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.Sort, "Agrupar y ordenar", tint = Color.White) }
        DropdownMenu(open, { open = false }) {
            Text("Agrupar por", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            TaskGroupBy.entries.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.label) },
                    leadingIcon = { if (g == groupBy) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                    onClick = { onGroup(g); open = false },
                )
            }
            HorizontalDivider()
            Text("Ordenar por", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            TaskSortBy.entries.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label) },
                    leadingIcon = { if (o == sortBy) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                    onClick = { onSort(o); open = false },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Reordenar tareas…") }, onClick = { open = false; onReorder() })
        }
    }
}

/** Orden manual con flechas (cambia "Ordenar por" a Manual al guardar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReorderSheet(tasks: List<Task>, onDismiss: () -> Unit, onSave: (List<Task>) -> Unit) {
    val items = remember(tasks) { mutableStateListOf<Task>().apply { addAll(tasks) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reordenar tareas", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { onSave(items.toList()) }) { Text("Guardar") }
            }
            Text(
                "Usa las flechas para mover cada tarea. Al guardar, el orden pasa a \"Manual\".",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.heightIn(max = 480.dp).padding(top = 8.dp)) {
                itemsIndexed(items, key = { _, t -> t.id }) { i, t ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { if (i > 0) { val x = items.removeAt(i); items.add(i - 1, x) } }, enabled = i > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Subir")
                        }
                        IconButton(onClick = { if (i < items.lastIndex) { val x = items.removeAt(i); items.add(i + 1, x) } }, enabled = i < items.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Bajar")
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- entrada inteligente

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartChips(parsed: Parsed?, modifier: Modifier = Modifier) {
    if (parsed == null || !parsed.found) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("✨", fontSize = 13.sp)
        parsed.chips().forEach { c ->
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A3F5A)).padding(horizontal = 8.dp, vertical = 3.dp),
            ) { Text(c, fontSize = 12.sp, color = Accent) }
        }
    }
}
