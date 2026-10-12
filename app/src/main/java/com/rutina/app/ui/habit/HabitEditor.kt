package com.rutina.app.ui.habit

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.GoalPeriod
import com.rutina.app.data.GoalType
import com.rutina.app.data.Habit
import com.rutina.app.data.ScheduleType
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.Palette
import com.rutina.app.domain.Suggestion
import com.rutina.app.domain.Suggestions
import com.rutina.app.ui.common.Dropdown
import com.rutina.app.ui.common.FormRow
import com.rutina.app.ui.common.Pill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- ViewModel

class HabitEditorViewModel(
    private val db: AppDatabase,
    private val editId: Long,
    preset: Suggestion?,
) : ViewModel() {

    private val _habit = MutableStateFlow(
        preset?.let {
            Habit(
                name = it.name, color = it.color, type = it.type, goalType = it.goalType,
                target = it.target, unit = it.unit, period = it.period,
                quitSince = if (it.type == TrackingType.QUIT) System.currentTimeMillis() else null,
            )
        } ?: Habit(name = "", color = Palette.at(0))
    )
    val habit: StateFlow<Habit> = _habit
    val isEdit = editId > 0

    /** Tipo con el que se cargó el hábito (para detectar cambios de tipo). */
    private var originalType: TrackingType? = null
    private val _logCount = MutableStateFlow(0)
    val logCount: StateFlow<Int> = _logCount

    init {
        if (isEdit) viewModelScope.launch {
            db.habits().get(editId)?.let { _habit.value = it; originalType = it.type }
            _logCount.value = db.habits().countLogs(editId)
        }
    }

    /** Cambiar de tipo en un hábito existente reinicia su historial (los registros significan otra cosa). */
    fun typeChanged(): Boolean = isEdit && originalType != null && originalType != _habit.value.type

    /** Se puede elegir la fecha de inicio de un hábito de Dejar al crearlo o al convertirlo. */
    fun canPickQuitStart(): Boolean = _habit.value.type == TrackingType.QUIT && (!isEdit || typeChanged())

    fun update(transform: (Habit) -> Habit) { _habit.value = transform(_habit.value) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        var h = _habit.value.copy(name = _habit.value.name.trim())
        val fresh = typeChanged()
        if (fresh) {
            // Historial limpio: los registros anteriores tenían otro significado.
            db.habits().deleteLogsFor(h.id)
            h = h.copy(createdAt = now, quitSince = if (h.type == TrackingType.QUIT) h.quitSince else null)
        }
        if (h.type == TrackingType.QUIT) {
            val since = (h.quitSince ?: now).coerceAtMost(now)
            // El historial empieza cuando lo dejaste (puede ser antes de crear el hábito).
            h = h.copy(quitSince = since, createdAt = if (!isEdit || fresh) minOf(h.createdAt, since) else h.createdAt)
        }
        if (isEdit) db.habits().update(h) else db.habits().insert(h)
        onDone()
    }

    fun archive(onDone: () -> Unit) = viewModelScope.launch {
        db.habits().update(_habit.value.copy(archived = true)); onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        db.habits().delete(_habit.value); onDone()
    }
}

// ---------------------------------------------------------------- Paso 1: sugerencias

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(onClose: () -> Unit, onPick: (group: Int, item: Int) -> Unit, onCustom: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nuevo hábito", fontWeight = FontWeight.Bold)
                        Text("Prueba una sugerencia…", fontSize = 14.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().imePadding().padding(16.dp)) {
                Button(
                    onClick = onCustom, modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD6D6D6), contentColor = Color.Black),
                ) { Text("Hábito personalizado  ›", fontWeight = FontWeight.Bold) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Suggestions.groups.forEachIndexed { gi, group ->
                Text(group.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    group.items.forEachIndexed { ii, s ->
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Color(s.color))
                                .clickable { onPick(gi, ii) }.padding(horizontal = 18.dp, vertical = 16.dp),
                        ) { Text(s.name, color = Color.Black) }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ---------------------------------------------------------------- Paso 2: formulario

private data class TypeInfo(val type: TrackingType, val title: String, val desc: String, val icon: ImageVector, val ready: Boolean)

private val typeInfos = listOf(
    TypeInfo(TrackingType.COUNT, "Recuento", "Con qué frecuencia completas una actividad", Icons.Filled.CheckBox, true),
    TypeInfo(TrackingType.QUIT, "Dejar", "Tiempo desde que dejaste un mal hábito", Icons.Filled.Block, true),
    TypeInfo(TrackingType.MOOD, "Estado de ánimo", "Tus emociones a lo largo del tiempo", Icons.Filled.SentimentSatisfied, false),
    TypeInfo(TrackingType.WEIGHT, "Peso corporal", "Progreso y tendencia de tu peso", Icons.Filled.MonitorWeight, false),
    TypeInfo(TrackingType.HEALTH, "Health Connect", "Pasos, distancia, calorías (automático)", Icons.Filled.Favorite, false),
    TypeInfo(TrackingType.JOURNAL, "Diario", "Cuánto escribes en tu diario", Icons.Filled.MenuBook, true),
)

private val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitFormScreen(vm: HabitEditorViewModel, onClose: () -> Unit) {
    val h by vm.habit.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showColors by remember { mutableStateOf(false) }
    var showTypes by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmType by remember { mutableStateOf(false) }
    var pickQuitDate by remember { mutableStateOf(false) }
    var pickQuitTime by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val logCount by vm.logCount.collectAsStateWithLifecycle()
    val color = Color(h.color)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (vm.isEdit) "Editar hábito" else "Nuevo hábito", color = Color.Black, fontSize = 16.sp)
                        if (h.name.isNotBlank()) Text(h.name, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.Black) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = color),
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().imePadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancelar") }
                Button(
                    onClick = {
                        if (h.name.isBlank()) Toast.makeText(ctx, "El nombre de tu hábito no puede estar vacío.", Toast.LENGTH_SHORT).show()
                        else if (vm.typeChanged() && logCount > 0) confirmType = true
                        else vm.save(onClose)
                    },
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD6D6D6), contentColor = Color.Black),
                ) { Text("Guardar hábito", fontWeight = FontWeight.Bold) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            FormRow("Nombre") {
                OutlinedTextField(
                    h.name, { v -> vm.update { it.copy(name = v) } },
                    placeholder = { Text("Ponle nombre a tu hábito") }, singleLine = true,
                    modifier = Modifier.width(210.dp),
                )
            }
            FormRow("Color") {
                Box(Modifier.size(48.dp).clip(CircleShape).background(color).clickable { showColors = !showColors })
            }
            if (showColors) {
                FlowRow(Modifier.padding(16.dp, 0.dp, 16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.colors.forEach { c ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                                .then(if (c == h.color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                .clickable { vm.update { it.copy(color = c) }; showColors = false },
                        )
                    }
                }
            }
            FormRow("Tipo de seguimiento") {
                val info = typeInfos.first { it.type == h.type }
                Pill(onClick = { showTypes = true }) {
                    Icon(info.icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(info.title); Text("  ›")
                }
            }

            if (h.type == TrackingType.COUNT || h.type == TrackingType.JOURNAL) {
                val step = if (h.type == TrackingType.JOURNAL) 10.0 else 1.0
                FormRow("Tipo de objetivo") {
                    Dropdown(
                        current = when (h.goalType) { GoalType.POSITIVE -> "👍 Positivo"; GoalType.NEGATIVE -> "👎 Negativo"; GoalType.NONE -> "⊖ Sin objetivo" },
                        options = listOf("👍 Positivo" to GoalType.POSITIVE, "👎 Negativo" to GoalType.NEGATIVE, "⊖ Sin objetivo" to GoalType.NONE),
                    ) { g -> vm.update { it.copy(goalType = g) } }
                }
                if (h.goalType != GoalType.NONE) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (h.goalType == GoalType.POSITIVE) "al menos" else "como máximo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { vm.update { it.copy(target = (it.target - step).coerceAtLeast(0.0)) } }) { Icon(Icons.Filled.KeyboardArrowDown, "Menos") }
                                var targetText by remember(h.target) { mutableStateOf(HabitLogic.formatNumber(h.target)) }
                                BasicTextField(
                                    value = targetText,
                                    onValueChange = { v ->
                                        targetText = v
                                        v.replace(',', '.').toDoubleOrNull()?.let { d -> vm.update { it.copy(target = d) } }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = TextStyle(fontSize = 20.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.width(64.dp),
                                )
                                IconButton(onClick = { vm.update { it.copy(target = it.target + step) } }) { Icon(Icons.Filled.KeyboardArrowUp, "Más") }
                            }
                            Spacer(Modifier.width(12.dp))
                            if (h.type == TrackingType.JOURNAL) Text("palabras", fontSize = 18.sp)
                            else Dropdown(h.unit, Suggestions.units.map { it to it }) { u -> vm.update { it.copy(unit = u) } }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("por", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Dropdown(
                            when (h.period) { GoalPeriod.DAY -> "Día"; GoalPeriod.WEEK -> "Semana"; GoalPeriod.MONTH -> "Mes" },
                            listOf("Día" to GoalPeriod.DAY, "Semana" to GoalPeriod.WEEK, "Mes" to GoalPeriod.MONTH),
                        ) { p -> vm.update { it.copy(period = p) } }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Column(Modifier.padding(horizontal = 40.dp, vertical = 12.dp)) {
                    ScheduleOption("Todos los días de la semana", h.scheduleType == ScheduleType.ALL_DAYS) {
                        vm.update { it.copy(scheduleType = ScheduleType.ALL_DAYS) }
                    }
                    ScheduleOption("Días específicos de la semana", h.scheduleType == ScheduleType.SPECIFIC_DAYS) {
                        vm.update { it.copy(scheduleType = ScheduleType.SPECIFIC_DAYS) }
                    }
                    if (h.scheduleType == ScheduleType.SPECIFIC_DAYS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            dayLetters.forEachIndexed { i, l ->
                                val on = h.scheduleDays and (1 shl i) != 0
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(if (on) color else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { vm.update { it.copy(scheduleDays = it.scheduleDays xor (1 shl i)) } },
                                    contentAlignment = Alignment.Center,
                                ) { Text(l, color = if (on) Color.Black else Color.White) }
                            }
                        }
                    }
                    ScheduleOption("Número de días por semana", h.scheduleType == ScheduleType.DAYS_PER_WEEK) {
                        vm.update { it.copy(scheduleType = ScheduleType.DAYS_PER_WEEK, period = GoalPeriod.DAY) }
                    }
                    if (h.scheduleType == ScheduleType.DAYS_PER_WEEK) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.update { it.copy(daysPerWeek = (it.daysPerWeek - 1).coerceAtLeast(1)) } }) { Icon(Icons.Filled.KeyboardArrowDown, "Menos") }
                            Text("${h.daysPerWeek} días por semana")
                            IconButton(onClick = { vm.update { it.copy(daysPerWeek = (it.daysPerWeek + 1).coerceAtMost(7)) } }) { Icon(Icons.Filled.KeyboardArrowUp, "Más") }
                        }
                    }
                }
            } else if (h.type == TrackingType.QUIT) {
                if (vm.canPickQuitStart()) {
                    FormRow("Lo dejé desde") {
                        Pill(onClick = { pickQuitDate = true }) {
                            Text(h.quitSince?.let { com.rutina.app.ui.journal.stampLabel(it) } ?: "Ahora")
                        }
                    }
                }
                Text(
                    if (vm.canPickQuitStart()) "Si ya lo habías dejado, elige desde cuándo; si no, el contador empieza al guardar. " +
                        "Si recaes, toca el hábito y elige \"Reiniciar contador\"."
                    else "Si recaes, toca el hábito y elige \"Reiniciar contador\".",
                    modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vm.typeChanged()) {
                Text(
                    "Cambiaste el tipo de seguimiento: al guardar, este hábito empieza con historial limpio.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                )
            }

            if (vm.isEdit) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.archive(onClose) }, Modifier.weight(1f)) { Text("Archivar") }
                    OutlinedButton(onClick = { confirmDelete = true }, Modifier.weight(1f)) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTypes) {
        AlertDialog(
            onDismissRequest = { showTypes = false },
            title = { Text("Tipo de seguimiento") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    typeInfos.forEach { t ->
                        val sel = t.type == h.type
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .border(2.dp, if (sel) color else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable(enabled = t.ready) {
                                    vm.update {
                                        val wasJournal = it.type == TrackingType.JOURNAL
                                        when (t.type) {
                                            TrackingType.QUIT -> it.copy(
                                                type = t.type, goalType = GoalType.NONE,
                                                period = GoalPeriod.DAY, scheduleType = ScheduleType.ALL_DAYS,
                                            )
                                            TrackingType.JOURNAL -> it.copy(
                                                type = t.type, goalType = GoalType.POSITIVE, unit = "palabras",
                                                target = if (wasJournal) it.target else 100.0,
                                            )
                                            else -> it.copy(
                                                type = t.type, goalType = GoalType.POSITIVE,
                                                unit = if (wasJournal) "vez" else it.unit,
                                                target = if (wasJournal) 1.0 else it.target,
                                            )
                                        }
                                    }
                                    showTypes = false
                                }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(t.icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                Text(t.title, fontWeight = FontWeight.Bold, color = if (t.ready) Color.Unspecified else Color.Gray)
                            }
                            Text(
                                if (t.ready) t.desc else "${t.desc} · próximamente",
                                fontSize = 13.sp, textAlign = TextAlign.Center,
                                color = if (t.ready) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTypes = false }) { Text("Cerrar") } },
        )
    }

    if (confirmType) {
        AlertDialog(
            onDismissRequest = { confirmType = false },
            title = { Text("¿Cambiar el tipo de seguimiento?") },
            text = {
                Text(
                    "Este hábito tiene $logCount registros que significan otra cosa con el tipo nuevo. " +
                        "Al cambiarlo se borran y empieza con historial limpio. Tus notas del diario no se tocan."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmType = false; vm.save(onClose) }) {
                    Text("Cambiar y empezar de cero", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmType = false }) { Text("Cancelar") } },
        )
    }

    if (pickQuitDate) {
        val init = java.time.Instant.ofEpochMilli(h.quitSince ?: System.currentTimeMillis()).atZone(java.time.ZoneOffset.UTC)
        val st = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = HabitLogic.dateOf(h.quitSince ?: System.currentTimeMillis())
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickQuitDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickQuitTime = st.selectedDateMillis?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    } ?: init.toLocalDate()
                    pickQuitDate = false
                }) { Text("Siguiente") }
            },
            dismissButton = { TextButton(onClick = { pickQuitDate = false }) { Text("Cancelar") } },
        ) { androidx.compose.material3.DatePicker(state = st) }
    }

    pickQuitTime?.let { day ->
        val cur = java.time.Instant.ofEpochMilli(h.quitSince ?: System.currentTimeMillis()).atZone(HabitLogic.zone)
        val st = androidx.compose.material3.rememberTimePickerState(initialHour = cur.hour, initialMinute = cur.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickQuitTime = null },
            title = { Text("Hora") },
            text = { androidx.compose.material3.TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    val ms = day.atTime(st.hour, st.minute).atZone(HabitLogic.zone).toInstant().toEpochMilli()
                    vm.update { it.copy(quitSince = ms.coerceAtMost(System.currentTimeMillis())) }
                    pickQuitTime = null
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickQuitTime = null }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar \"${h.name}\"?") },
            text = { Text("Se borrarán también todos sus registros. Si solo quieres ocultarlo, usa Archivar.") },
            confirmButton = { TextButton(onClick = { vm.delete(onClose) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ScheduleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = if (selected) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
