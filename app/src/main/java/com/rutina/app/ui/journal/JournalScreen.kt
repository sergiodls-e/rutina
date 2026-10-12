package com.rutina.app.ui.journal

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.NoteLogic
import com.rutina.app.ui.common.NoteBody
import com.rutina.app.ui.theme.Accent
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    vm: JournalViewModel,
    onNewNote: () -> Unit,
    onEditNote: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val f = state.filter
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<HabitEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<Entry?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) vm.export(ctx.contentResolver, uri) { ok ->
            Toast.makeText(ctx, if (ok) "Diario exportado" else "No se pudo exportar", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = f.searching) { vm.setSearching(false) }
    BackHandler(enabled = !f.searching && f.starredOnly) { vm.toggleStarredOnly() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = vm::toggleStarredOnly) {
                        Icon(
                            if (f.starredOnly) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            "Notas marcadas",
                            tint = if (f.starredOnly) Accent else Color.White,
                        )
                    }
                },
                title = { Text(if (f.starredOnly) "Notas marcadas" else "Diario", fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, "Más opciones") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Exportar notas") },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = { menu = false; exportLauncher.launch("diario-${LocalDate.now()}.md") },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewNote, shape = CircleShape,
                containerColor = Color(0xFFD6D6D6), contentColor = Color.Black,
            ) { Icon(Icons.Filled.Add, "Nueva nota") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            FilterBar(state, vm)
            if (state.loaded && state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                f.query.isNotBlank() -> "Sin resultados"
                                f.starredOnly -> "No hay notas marcadas"
                                else -> "Tu diario está vacío"
                            },
                            fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                        )
                        Text(
                            if (f.starredOnly) "Marca una nota con el ícono de marcador para encontrarla aquí."
                            else "Escribe una nota con + o registra un hábito: todo aparece aquí.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.entries, key = { it.key }) { e ->
                        when (e) {
                            is NoteEntry -> EntryCard(
                                time = e.time,
                                body = e.note.content,
                                onToggleCheck = { idx -> vm.toggleCheck(e.note, idx) },
                                onOpen = { onEditNote(e.note.id) },
                                label = {
                                    if (e.habit != null) HabitChip(e.habit.name, Color(e.habit.color))
                                    else IconChip(Icons.Filled.Notes, "Nota")
                                    if (e.habit?.type == TrackingType.JOURNAL) {
                                        ValueChip("${NoteLogic.wordCount(e.note.content)} palabras")
                                    }
                                    if (e.note.starred) {
                                        Icon(Icons.Filled.Bookmark, "Marcada", Modifier.padding(start = 6.dp).size(18.dp), tint = Accent)
                                    }
                                },
                                menu = listOf(
                                    MenuItem("Editar") { onEditNote(e.note.id) },
                                    MenuItem(if (e.note.starred) "Quitar marca" else "Marcar") { vm.toggleStar(e.note) },
                                    MenuItem("Eliminar") { confirmDelete = e },
                                ),
                            )
                            is HabitEntry -> EntryCard(
                                time = e.time,
                                body = e.log.userNote(),
                                onOpen = { editingLog = e },
                                label = {
                                    HabitChip(e.habit.name, Color(e.habit.color))
                                    ValueChip(logLabel(e.habit, e.log))
                                },
                                menu = listOf(
                                    MenuItem((if (e.log.userNote() == null) "Añadir nota" else "Editar nota")) { editingLog = e },
                                    MenuItem("Eliminar registro") { confirmDelete = e },
                                ),
                            )
                            is TaskEntry -> EntryCard(
                                time = e.time,
                                body = null,
                                onOpen = { onOpenTask(e.task.id) },
                                label = { IconChip(Icons.Filled.CheckCircle, e.task.title, tint = e.list?.let { Color(it.color) } ?: Accent) },
                                menu = listOf(
                                    MenuItem("Abrir tarea") { onOpenTask(e.task.id) },
                                    MenuItem("Marcar como pendiente") { vm.reopenTask(e.task) },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    editingLog?.let { e ->
        var text by remember(e.key) { mutableStateOf(e.log.userNote().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingLog = null },
            title = { Text("${e.habit.name} · ${logLabel(e.habit, e.log)}") },
            text = {
                Column {
                    Text(stampLabel(e.time), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    OutlinedTextField(
                        text, { text = it }, label = { Text("Nota") }, minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.saveLogNote(e.log, text); editingLog = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editingLog = null }) { Text("Cancelar") } },
        )
    }

    confirmDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (e is NoteEntry) "¿Eliminar esta nota?" else "¿Eliminar este registro?") },
            text = {
                if (e is HabitEntry) Text("Se quitará del progreso y la racha de \"${e.habit.name}\".")
            },
            confirmButton = {
                TextButton(onClick = {
                    when (e) {
                        is NoteEntry -> vm.deleteNote(e.note)
                        is HabitEntry -> vm.deleteLog(e.log)
                        is TaskEntry -> Unit
                    }
                    confirmDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FilterBar(state: JournalState, vm: JournalViewModel) {
    val f = state.filter
    Row(
        Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (f.searching) {
            OutlinedTextField(
                f.query, vm::setQuery,
                placeholder = { Text("Buscar cualquier nota…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.setSearching(false) }) { Icon(Icons.Filled.Close, "Cerrar búsqueda") }
        } else {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A))
                    .clickable { vm.setSearching(true) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Search, "Buscar") }
            val tag = state.tags.firstOrNull { it.id == f.tagId }
            MenuChip(
                label = tag?.name ?: "Todas las etiquetas",
                options = listOf<Pair<String, Long?>>("Todas las etiquetas" to null) + state.tags.map { it.name to it.id },
                dot = { id -> state.tags.firstOrNull { it.id == id }?.let { Color(it.color) } },
                onPick = vm::setTag,
                modifier = Modifier.weight(1f),
            )
            MenuChip(
                label = f.kind.label,
                options = EntryKind.entries.map { it.label to it },
                onPick = vm::setKind,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun <T> MenuChip(
    label: String,
    options: List<Pair<String, T>>,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    dot: (T) -> Color? = { null },
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF333333))
                .clickable { open = true }.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Icon(Icons.Filled.KeyboardArrowDown, null)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (l, v) ->
                val c = dot(v)
                DropdownMenuItem(
                    text = { Text(l) },
                    trailingIcon = { if (c != null) Box(Modifier.size(12.dp).clip(CircleShape).background(c)) },
                    onClick = { onPick(v); open = false },
                )
            }
        }
    }
}

private class MenuItem(val label: String, val action: () -> Unit)

@Composable
private fun EntryCard(
    time: Long,
    body: String?,
    onOpen: () -> Unit,
    label: @Composable () -> Unit,
    menu: List<MenuItem>,
    onToggleCheck: ((Int) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen),
    ) {
        if (!body.isNullOrBlank()) {
            NoteBody(body, Modifier.padding(14.dp), maxLines = 12, onToggleCheck = onToggleCheck)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(stampLabel(time), Color(0xFF1A1A1A), Color.White)
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { label() }
            Box {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)).clickable { open = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.MoreHoriz, "Opciones") }
                DropdownMenu(open, { open = false }) {
                    menu.forEach { item ->
                        DropdownMenuItem(text = { Text(item.label) }, onClick = { open = false; item.action() })
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitChip(name: String, color: Color) {
    Box(
        Modifier.widthIn(max = 140.dp).clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)).background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(name, color = Color.Black, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun ValueChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)).background(Color(0xFF1A1A1A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, fontSize = 14.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun IconChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color = Accent) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
