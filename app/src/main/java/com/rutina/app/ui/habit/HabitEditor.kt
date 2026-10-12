package com.rutina.app.ui.habit

import android.widget.Toast
import androidx.compose.foundation.background
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

    init {
        if (isEdit) viewModelScope.launch { db.habits().get(editId)?.let { _habit.value = it } }
    }

    fun update(transform: (Habit) -> Habit) { _habit.value = transform(_habit.value) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        var h = _habit.value.copy(name = _habit.value.name.trim())
        if (h.type == TrackingType.QUIT && h.quitSince == null) h = h.copy(quitSince = System.currentTimeMillis())
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
    TypeInfo(TrackingType.JOURNAL, "Diario", "Cuánto escribes en tu diario", Icons.Filled.MenuBook, false),
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

            if (h.type == TrackingType.COUNT) {
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
                                IconButton(onClick = { vm.update { it.copy(target = (it.target - 1).coerceAtLeast(0.0)) } }) { Icon(Icons.Filled.KeyboardArrowDown, "Menos") }
                                Text(HabitLogic.formatNumber(h.target), fontSize = 20.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                                IconButton(onClick = { vm.update { it.copy(target = it.target + 1) } }) { Icon(Icons.Filled.KeyboardArrowUp, "Más") }
                            }
                            Spacer(Modifier.width(12.dp))
                            Dropdown(h.unit, Suggestions.units.map { it to it }) { u -> vm.update { it.copy(unit = u) } }
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
                Text(
                    "El contador empieza al guardar. Si recaes, toca el hábito y elige \"Reiniciar contador\".",
                    modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        it.copy(
                                            type = t.type,
                                            goalType = if (t.type == TrackingType.QUIT) GoalType.NONE else GoalType.POSITIVE,
                                        )
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
private fun FormRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        content()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun Pill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun <T> Dropdown(current: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
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

@Composable
private fun ScheduleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = if (selected) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
