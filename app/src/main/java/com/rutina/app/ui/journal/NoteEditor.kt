package com.rutina.app.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.Habit
import com.rutina.app.data.Note
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.NoteLogic
import com.rutina.app.ui.common.MarkdownTransformation
import com.rutina.app.ui.common.listPrefixOf
import com.rutina.app.ui.theme.Accent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------------------------------------------------------- ViewModel

class NoteEditorViewModel(
    private val db: AppDatabase,
    private val editId: Long,
    presetHabit: Long?,
) : ViewModel() {
    private val _note = MutableStateFlow(Note(content = "", habitId = presetHabit))
    val note: StateFlow<Note> = _note
    private val _loaded = MutableStateFlow(editId == 0L)
    val loaded: StateFlow<Boolean> = _loaded
    val isEdit = editId > 0

    val habits: StateFlow<List<Habit>> =
        db.habits().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var finished = false

    init {
        if (isEdit) viewModelScope.launch {
            db.notes().get(editId)?.let { _note.value = it }
            _loaded.value = true
        }
    }

    fun update(f: (Note) -> Note) { _note.value = f(_note.value) }

    /** Guarda al salir. Una nota vacía no se guarda (y si ya existía, se conserva la versión anterior). */
    fun save(content: String, onDone: () -> Unit) = viewModelScope.launch {
        if (!finished && _loaded.value) {
            finished = true
            val n = _note.value.copy(content = content.trimEnd())
            if (n.content.isNotBlank()) {
                if (isEdit) db.notes().update(n) else db.notes().insert(n)
            }
        }
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        finished = true
        if (isEdit) db.notes().delete(_note.value)
        onDone()
    }
}

// ---------------------------------------------------------------- Edición de texto

private val linePrefixes = listOf("[ ] ", "[x] ", "- ", "# ")

/** Rodea la selección con una marca; sin selección, inserta el par y deja el cursor en medio. */
private fun wrap(v: TextFieldValue, m: String): TextFieldValue {
    val s = v.selection.min
    val e = v.selection.max
    val t = v.text
    return if (s == e) {
        TextFieldValue(t.substring(0, s) + m + m + t.substring(s), TextRange(s + m.length))
    } else {
        TextFieldValue(t.substring(0, s) + m + t.substring(s, e) + m + t.substring(e), TextRange(s + m.length, e + m.length))
    }
}

/** Pone o quita un prefijo (lista, casilla, título) en la línea del cursor. */
private fun togglePrefix(v: TextFieldValue, p: String): TextFieldValue {
    val t = v.text
    val c = v.selection.min
    val ls = if (c == 0) 0 else t.lastIndexOf('\n', c - 1) + 1
    val line = t.substring(ls)
    val existing = linePrefixes.firstOrNull { line.startsWith(it) }
    val removeLen = existing?.length ?: 0
    val add = if (existing == p || (p == "[ ] " && existing == "[x] ")) "" else p
    val nt = t.substring(0, ls) + add + t.substring(ls + removeLen)
    val delta = add.length - removeLen
    return TextFieldValue(
        nt,
        TextRange((v.selection.start + delta).coerceAtLeast(ls), (v.selection.end + delta).coerceAtLeast(ls)),
    )
}

/**
 * Al presionar Enter en una lista o casilla, la siguiente línea continúa la lista.
 * Enter en un elemento vacío termina la lista.
 */
private fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (new.text.length != old.text.length + 1 || !new.selection.collapsed) return new
    val c = new.selection.start
    if (c == 0 || new.text[c - 1] != '\n') return new
    if (old.text != new.text.removeRange(c - 1, c)) return new
    val lineStart = if (c < 2) 0 else new.text.lastIndexOf('\n', c - 2) + 1
    val prevLine = new.text.substring(lineStart, c - 1)
    val prefix = listPrefixOf(prevLine) ?: return new
    if (prevLine == prefix) {
        return TextFieldValue(new.text.removeRange(lineStart, c), TextRange(lineStart))
    }
    val next = if (prefix.startsWith("[")) "[ ] " else prefix
    return TextFieldValue(new.text.substring(0, c) + next + new.text.substring(c), TextRange(c + next.length))
}

// ---------------------------------------------------------------- Pantalla

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(vm: NoteEditorViewModel, onClose: () -> Unit) {
    val note by vm.note.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<LocalDate?>(null) }
    var tagMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val transformation = remember(dim) { MarkdownTransformation(dim) }

    LaunchedEffect(loaded) {
        if (loaded && vm.isEdit) text = TextFieldValue(note.content, TextRange(note.content.length))
    }

    fun close() { vm.save(text.text, onClose) }
    BackHandler { close() }

    val tag = habits.firstOrNull { it.id == note.habitId }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.Filled.Close, "Cerrar y guardar") } },
                title = { Text(if (vm.isEdit) "Editar nota" else "Nueva nota", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.update { it.copy(starred = !it.starred) } }) {
                        Icon(
                            if (note.starred) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            if (note.starred) "Quitar marca" else "Marcar",
                            tint = if (note.starred) Accent else Color.White,
                        )
                    }
                    if (vm.isEdit) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
                    IconButton(onClick = { close() }) { Icon(Icons.Filled.Check, "Guardar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF1C1C1C)).navigationBarsPadding().imePadding()
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(Icons.Filled.FormatBold, "Negrita") { text = wrap(text, "**") }
                ToolButton(Icons.Filled.FormatItalic, "Cursiva") { text = wrap(text, "_") }
                ToolButton(Icons.Filled.FormatStrikethrough, "Tachado") { text = wrap(text, "~~") }
                ToolButton(Icons.Filled.BorderColor, "Resaltar") { text = wrap(text, "==") }
                ToolButton(Icons.Filled.Title, "Título") { text = togglePrefix(text, "# ") }
                ToolButton(Icons.Filled.FormatListBulleted, "Lista") { text = togglePrefix(text, "- ") }
                ToolButton(Icons.Filled.CheckBox, "Casilla") { text = togglePrefix(text, "[ ] ") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(stampLabel(note.timestamp), Color(0xFF2A2A2A), Color.White) { pickDate = true }
                Box {
                    Chip(
                        tag?.name ?: "Sin etiqueta",
                        tag?.let { Color(it.color) } ?: Color(0xFF2A2A2A),
                        if (tag != null) Color.Black else Color.White,
                        trailing = Icons.Filled.KeyboardArrowDown,
                    ) { tagMenu = true }
                    DropdownMenu(tagMenu, { tagMenu = false }) {
                        DropdownMenuItem(text = { Text("Sin etiqueta") }, onClick = { vm.update { it.copy(habitId = null) }; tagMenu = false })
                        habits.forEach { h ->
                            DropdownMenuItem(
                                text = { Text(h.name) },
                                trailingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(h.color))) },
                                onClick = { vm.update { it.copy(habitId = h.id) }; tagMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("${NoteLogic.wordCount(text.text)} palabras", fontSize = 12.sp, color = dim)
            }
            TextField(
                value = text,
                onValueChange = { text = continueList(text, it) },
                placeholder = { Text("Escribe aquí…") },
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (pickDate) {
        val initial = HabitLogic.dateOf(note.timestamp)
        val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = st.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: initial
                    pickDate = false
                }) { Text("Siguiente") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state = st) }
    }

    pickTime?.let { day ->
        val cur = Instant.ofEpochMilli(note.timestamp).atZone(HabitLogic.zone)
        val st = rememberTimePickerState(initialHour = cur.hour, initialMinute = cur.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            title = { Text("Hora") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    val ms = day.atTime(st.hour, st.minute).atZone(HabitLogic.zone).toInstant().toEpochMilli()
                    vm.update { it.copy(timestamp = ms) }
                    pickTime = null
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar esta nota?") },
            confirmButton = { TextButton(onClick = { vm.delete(onClose) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label) }
}

@Composable
internal fun Chip(
    text: String,
    bg: Color,
    fg: Color,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = fg, fontSize = 14.sp, maxLines = 1)
        if (trailing != null) { Spacer(Modifier.width(4.dp)); Icon(trailing, null, Modifier.size(16.dp), tint = fg) }
    }
}
