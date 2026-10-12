package com.rutina.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.DateRange
import com.rutina.app.data.TaskFilter
import com.rutina.app.data.TaskList
import com.rutina.app.domain.Palette
import com.rutina.app.domain.TaskLogic
import com.rutina.app.ui.common.Dropdown
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageListsViewModel(private val db: AppDatabase) : ViewModel() {
    val lists: StateFlow<List<TaskList>> =
        db.tasks().observeLists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filters: StateFlow<List<TaskFilter>> =
        db.tasks().observeFilters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveList(l: TaskList) = viewModelScope.launch {
        if (l.id == 0L) db.tasks().insertList(l.copy(sortOrder = lists.value.size)) else db.tasks().updateList(l)
    }

    /** Al borrar una lista, sus tareas se conservan y quedan "sin lista". */
    fun deleteList(l: TaskList) = viewModelScope.launch { db.tasks().deleteList(l) }

    fun saveFilter(f: TaskFilter) = viewModelScope.launch {
        if (f.id == 0L) db.tasks().insertFilter(f.copy(sortOrder = filters.value.size)) else db.tasks().updateFilter(f)
    }

    fun deleteFilter(f: TaskFilter) = viewModelScope.launch { db.tasks().deleteFilter(f) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageListsScreen(vm: ManageListsViewModel, onClose: () -> Unit) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    var editingList by remember { mutableStateOf<TaskList?>(null) }
    var editingFilter by remember { mutableStateOf<TaskFilter?>(null) }
    var deleting by remember { mutableStateOf<Any?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listas / Filtros", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Header("Listas") }
            if (lists.isEmpty()) item { Hint("Agrupa tareas por tema: trabajo, casa, compras…") }
            items(lists, key = { "l" + it.id }) { l ->
                ItemRow(l.name, Color(l.color), onEdit = { editingList = l }, onDelete = { deleting = l })
            }
            item {
                CreateButton(Icons.Filled.FormatListBulleted, "Crear una lista", "Define una colección de tareas") {
                    editingList = TaskList(name = "", color = Palette.at(lists.size + 4))
                }
            }
            item { Spacer(Modifier.padding(6.dp)); Header("Filtros") }
            if (filters.isEmpty()) item { Hint("Muestra tareas que cumplan criterios: prioridad alta, vencidas, próximos 7 días…") }
            items(filters, key = { "f" + it.id }) { f ->
                ItemRow(
                    f.name, Color(f.color),
                    subtitle = buildList {
                        add(TaskLogic.rangeLabel(f.dateRange))
                        if (f.minPriority > 0) add("prioridad ≥ ${TaskLogic.priorityLabel(f.minPriority).lowercase()}")
                        lists.firstOrNull { it.id == f.listId }?.let { add("lista ${it.name}") }
                    }.joinToString(" · "),
                    onEdit = { editingFilter = f }, onDelete = { deleting = f },
                )
            }
            item {
                CreateButton(Icons.Filled.FilterAlt, "Crear un filtro", "Consulta tareas que cumplen los criterios") {
                    editingFilter = TaskFilter(name = "", color = Palette.at(filters.size + 7))
                }
            }
            item { Spacer(Modifier.padding(24.dp)) }
        }
    }

    editingList?.let { l -> ListDialog(l, onDismiss = { editingList = null }) { vm.saveList(it); editingList = null } }
    editingFilter?.let { f -> FilterDialog(f, lists, onDismiss = { editingFilter = null }) { vm.saveFilter(it); editingFilter = null } }
    deleting?.let { d ->
        val name = (d as? TaskList)?.name ?: (d as TaskFilter).name
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Eliminar \"$name\"?") },
            text = { if (d is TaskList) Text("Las tareas de esta lista no se borran; quedan sin lista.") },
            confirmButton = {
                TextButton(onClick = {
                    if (d is TaskList) vm.deleteList(d) else vm.deleteFilter(d as TaskFilter)
                    deleting = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Header(t: String) = Text(t, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun Hint(t: String) = Text(t, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)

@Composable
private fun ItemRow(name: String, color: Color, subtitle: String? = null, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onEdit).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(name)
            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Eliminar") }
    }
}

@Composable
private fun CreateButton(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = Color(0xFF2E2E2E), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(title, fontWeight = FontWeight.Bold)
            }
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Palette.colors.forEach { c ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                    .then(if (c == selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun ListDialog(initial: TaskList, onDismiss: () -> Unit, onSave: (TaskList) -> Unit) {
    var l by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nueva lista" else "Editar lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(l.name, { l = l.copy(name = it) }, label = { Text("Nombre") }, singleLine = true)
                ColorPicker(l.color) { l = l.copy(color = it) }
            }
        },
        confirmButton = { TextButton(enabled = l.name.isNotBlank(), onClick = { onSave(l.copy(name = l.name.trim())) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun FilterDialog(initial: TaskFilter, lists: List<TaskList>, onDismiss: () -> Unit, onSave: (TaskFilter) -> Unit) {
    var f by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nuevo filtro" else "Editar filtro") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(f.name, { f = f.copy(name = it) }, label = { Text("Nombre") }, singleLine = true)
                Text("Fecha", fontWeight = FontWeight.Medium)
                Dropdown(TaskLogic.rangeLabel(f.dateRange), DateRange.entries.map { TaskLogic.rangeLabel(it) to it }) { f = f.copy(dateRange = it) }
                Text("Prioridad mínima", fontWeight = FontWeight.Medium)
                Dropdown(
                    if (f.minPriority == 0) "Cualquiera" else TaskLogic.priorityLabel(f.minPriority),
                    listOf("Cualquiera" to 0, "Baja" to 1, "Media" to 2, "Alta" to 3),
                ) { f = f.copy(minPriority = it) }
                Text("Lista", fontWeight = FontWeight.Medium)
                Dropdown(
                    lists.firstOrNull { it.id == f.listId }?.name ?: "Todas",
                    listOf<Pair<String, Long?>>("Todas" to null) + lists.map { it.name to it.id },
                ) { f = f.copy(listId = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Incluir completadas", Modifier.weight(1f))
                    Switch(checked = f.includeDone, onCheckedChange = { f = f.copy(includeDone = it) })
                }
                ColorPicker(f.color) { f = f.copy(color = it) }
            }
        },
        confirmButton = { TextButton(enabled = f.name.isNotBlank(), onClick = { onSave(f.copy(name = f.name.trim())) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
