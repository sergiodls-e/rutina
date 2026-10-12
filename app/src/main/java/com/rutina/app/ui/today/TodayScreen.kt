package com.rutina.app.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.rutina.app.data.RepeatRule
import com.rutina.app.data.TaskList
import com.rutina.app.domain.TaskLogic
import com.rutina.app.ui.tasks.PriorityColors
import com.rutina.app.ui.theme.Accent
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutina.app.data.Habit
import com.rutina.app.data.Task
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale("es", "MX")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onNewHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onFullTaskEditor: (title: String, listId: Long?, epochDay: Long?) -> Unit,
    onManageLists: () -> Unit,
    onWriteNote: (habitId: Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var fabOpen by remember { mutableStateOf(false) }
    var showTaskSheet by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<HabitRow?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && state.view != MainView.Today) { vm.show(MainView.Today) }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            MainDrawer(
                state = state,
                onPick = { v -> vm.show(v); scope.launch { drawer.close() } },
                onManage = { scope.launch { drawer.close() }; onManageLists() },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, "Menú") }
                    },
                    title = {
                        val title = if (state.view == MainView.Today && state.date != LocalDate.now()) {
                            state.date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es))
                                .replaceFirstChar { it.uppercase() }
                        } else state.title
                        Text(
                            title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { vm.select(LocalDate.now()) },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                )
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(fabOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                            FabOption("Tarea", "Crea una lista de pendientes", Icons.Filled.TaskAlt) {
                                fabOpen = false; showTaskSheet = true
                            }
                            FabOption("Hábito", "Registra una actividad recurrente", Icons.Filled.Spa) {
                                fabOpen = false; onNewHabit()
                            }
                        }
                    }
                    FloatingActionButton(
                        onClick = { fabOpen = !fabOpen },
                        shape = CircleShape,
                        containerColor = Color(0xFFD6D6D6),
                        contentColor = Color.Black,
                    ) { Icon(if (fabOpen) Icons.Filled.Close else Icons.Filled.Add, contentDescription = "Crear") }
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (state.showsWeek) WeekStrip(state.date, vm) { vm.select(it) }
                (state.view as? MainView.Search)?.let { s ->
                    OutlinedTextField(
                        s.query, { vm.show(MainView.Search(it)) },
                        placeholder = { Text("Buscar tareas y hábitos…") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
                if (state.isEmpty) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                when (state.view) {
                                    MainView.Today -> "No hay tareas ni hábitos"
                                    MainView.AllHabits -> "No hay hábitos"
                                    is MainView.Search -> "Sin resultados"
                                    else -> "No hay tareas"
                                },
                                fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                            )
                            if (state.view !is MainView.Search) {
                                Text("Toca + para añadir", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.habits.isNotEmpty()) {
                            item { SectionHeader(if (state.view == MainView.Today) "Hábitos diarios" else "Hábitos", state.habits.size) }
                            items(state.habits, key = { "h" + it.habit.id }) { row ->
                                HabitCard(
                                    row = row,
                                    onPrimary = {
                                        when (row.habit.type) {
                                            TrackingType.QUIT -> actionsFor = row
                                            TrackingType.JOURNAL -> onWriteNote(row.habit.id)
                                            else -> vm.add(row.habit)
                                        }
                                    },
                                    onLong = { actionsFor = row },
                                )
                            }
                        }
                        state.taskGroups.forEach { (title, tasks) ->
                            item(key = "g$title") { SectionHeader(title, tasks.size) }
                            items(tasks, key = { "t" + it.id }) { t ->
                                TaskRow(
                                    task = t,
                                    list = state.lists.firstOrNull { it.id == t.listId },
                                    onToggle = { vm.toggleTask(t) },
                                    onOpen = { onEditTask(t.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTaskSheet) {
        val defaultDay = if (state.view == MainView.Today) state.date.toEpochDay() else null
        QuickTaskSheet(
            lists = state.lists,
            initialDay = defaultDay,
            initialList = vm.currentListId(),
            onDismiss = { showTaskSheet = false },
            onSave = { vm.addTask(it); showTaskSheet = false },
            onMore = { title, listId, day -> showTaskSheet = false; onFullTaskEditor(title, listId, day) },
        )
    }

    actionsFor?.let { row ->
        HabitActionsDialog(
            row = row,
            onDismiss = { actionsFor = null },
            onAdd = { amount, note -> vm.add(row.habit, amount, note); actionsFor = null },
            onWrite = { actionsFor = null; onWriteNote(row.habit.id) },
            onUndo = { vm.undo(row.habit); actionsFor = null },
            onReset = { vm.resetQuit(row.habit); actionsFor = null },
            onEdit = { actionsFor = null; onEditHabit(row.habit.id) },
        )
    }
}

@Composable
private fun MainDrawer(state: TodayState, onPick: (MainView) -> Unit, onManage: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = Color(0xFF181818), modifier = Modifier.width(300.dp)) {
        Column(Modifier.fillMaxHeight().padding(12.dp)) {
            Text("Listas / Filtros", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                item { DrawerItem("Vista de hoy", Icons.Filled.Today, Accent, state.todayCount, state.view == MainView.Today) { onPick(MainView.Today) } }
                item { DrawerItem("Todas las tareas", Icons.Filled.TaskAlt, Accent, state.pendingCount, state.view == MainView.AllTasks) { onPick(MainView.AllTasks) } }
                item { DrawerItem("Todos los hábitos", Icons.Filled.Spa, Accent, state.habitCount, state.view == MainView.AllHabits) { onPick(MainView.AllHabits) } }
                if (state.lists.isNotEmpty()) item { DrawerLabel("Mis listas") }
                items(state.lists, key = { "dl" + it.id }) { l ->
                    DrawerItem(l.name, Icons.Filled.FormatListBulleted, Color(l.color), null, state.view == MainView.OfList(l.id)) { onPick(MainView.OfList(l.id)) }
                }
                if (state.filters.isNotEmpty()) item { DrawerLabel("Filtros") }
                items(state.filters, key = { "df" + it.id }) { f ->
                    DrawerItem(f.name, Icons.Filled.FilterAlt, Color(f.color), null, state.view == MainView.OfFilter(f.id)) { onPick(MainView.OfFilter(f.id)) }
                }
            }
            Surface(
                onClick = { onPick(MainView.Search("")) },
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null); Spacer(Modifier.width(10.dp))
                    Text("Buscar tareas y hábitos…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                onClick = onManage, shape = RoundedCornerShape(12.dp), color = Color(0xFFD6D6D6), contentColor = Color.Black,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, null); Spacer(Modifier.width(10.dp)); Text("Gestionar listas / filtros", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DrawerLabel(t: String) =
    Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp, 16.dp, 12.dp, 4.dp))

@Composable
private fun DrawerItem(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, count: Int?, selected: Boolean, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF2A2A2A) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = Color.Black)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, vm: TodayViewModel, onSelect: (LocalDate) -> Unit) {
    val start = HabitLogic.weekStartOf(selected, vm.weekStart)
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = { onSelect(selected.minusWeeks(1)) }, modifier = Modifier.size(28.dp).align(Alignment.CenterVertically)) {
            Text("‹", fontSize = 22.sp)
        }
        (0 until 7).forEach { i ->
            val d = start.plusDays(i.toLong())
            val isSel = d == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF333333))
                    .then(if (isSel) Modifier.border(2.dp, Color(0xFF777777), RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { onSelect(d) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.SHORT, es).replace(".", "").replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    color = if (d == today) Color(0xFF8FD3C1) else Color(0xFFB8B8B8),
                )
                Text(d.dayOfMonth.toString(), fontSize = 22.sp, color = Color.White)
            }
        }
        IconButton(onClick = { onSelect(selected.plusWeeks(1)) }, modifier = Modifier.size(28.dp).align(Alignment.CenterVertically)) {
            Text("›", fontSize = 22.sp)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(row: HabitRow, onPrimary: () -> Unit, onLong: () -> Unit) {
    val h = row.habit
    val color = Color(h.color)
    val isQuit = h.type == TrackingType.QUIT

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (isQuit) LaunchedEffect(h.id) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onLong, onLongClick = onLong),
    ) {
        // Relleno de progreso
        val frac = if (isQuit) 1f else row.fraction
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(color.copy(alpha = 0.18f)))

        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.widthIn(min = 80.dp, max = 150.dp).clip(RoundedCornerShape(8.dp)).background(color)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    h.name, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, fontSize = 14.sp,
                    textDecoration = if (row.met) TextDecoration.LineThrough else null,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.streak.toString(), fontSize = 14.sp)
                Icon(
                    Icons.Filled.LocalFireDepartment, null, Modifier.size(14.dp),
                    tint = if (row.streak > 0) Color(0xFFFF8A50) else Color.Gray,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (isQuit) HabitLogic.formatElapsed(now - (h.quitSince ?: h.createdAt))
                else "${HabitLogic.formatNumber(row.progress)} / ${HabitLogic.amountLabel(h.target, h.unit)}",
                fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 140.dp),
            )
            Spacer(Modifier.width(8.dp))
            val filled = row.met
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (filled) color else Color.Transparent)
                    .border(2.dp, if (filled) color else Color(0xFF555555), RoundedCornerShape(10.dp))
                    .clickable(onClick = onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        isQuit -> Icons.Filled.Refresh
                        filled -> Icons.Filled.Check
                        h.type == TrackingType.JOURNAL -> Icons.Filled.EditNote
                        else -> Icons.Filled.Add
                    },
                    contentDescription = "Registrar",
                    tint = if (filled) Color.Black else Color.White,
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, list: TaskList?, onToggle: () -> Unit, onOpen: () -> Unit) {
    val overdue = TaskLogic.isOverdue(task)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(list?.let { Color(it.color) } ?: Accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.TaskAlt, null, Modifier.size(18.dp), tint = Color.Black) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
            val parts = listOfNotNull(
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
        Checkbox(checked = task.done, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun FabOption(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2A2A2A),
        modifier = Modifier.width(300.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(subtitle, fontSize = 13.sp, color = Color(0xFFDDDDDD))
            }
            Text("›", fontSize = 22.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTaskSheet(
    lists: List<TaskList>,
    initialDay: Long?,
    initialList: Long?,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onMore: (title: String, listId: Long?, epochDay: Long?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(initialDay) }
    var priority by remember { mutableIntStateOf(0) }
    var listId by remember { mutableStateOf(initialList) }
    var dateMenu by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("¿Qué quieres hacer?") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    FilterChip(
                        selected = day != null, onClick = { dateMenu = true },
                        label = { Text(day?.let { TaskLogic.dueLabel(Task(title = "", dueEpochDay = it), today) } ?: "Sin fecha") },
                    )
                    DropdownMenu(dateMenu, { dateMenu = false }) {
                        listOf("Hoy" to today.toEpochDay(), "Mañana" to today.plusDays(1).toEpochDay(), "Sin fecha" to null)
                            .forEach { (l, v) -> DropdownMenuItem(text = { Text(l) }, onClick = { day = v; dateMenu = false }) }
                        DropdownMenuItem(text = { Text("Otra fecha u hora…") }, onClick = { dateMenu = false; onMore(title, listId, day) })
                    }
                }
                IconButton(onClick = { priority = (priority + 1) % 4 }) {
                    Icon(if (priority > 0) Icons.Filled.Flag else Icons.Outlined.Flag, "Prioridad", tint = PriorityColors[priority])
                }
                Box {
                    FilterChip(
                        selected = listId != null, onClick = { listMenu = true },
                        label = { Text(lists.firstOrNull { it.id == listId }?.name ?: "Lista") },
                    )
                    DropdownMenu(listMenu, { listMenu = false }) {
                        DropdownMenuItem(text = { Text("Ninguna") }, onClick = { listId = null; listMenu = false })
                        lists.forEach { l -> DropdownMenuItem(text = { Text(l.name) }, onClick = { listId = l.id; listMenu = false }) }
                    }
                }
                Spacer(Modifier.weight(1f))
                FloatingActionButton(
                    onClick = {
                        if (title.isNotBlank()) onSave(
                            Task(title = title.trim(), description = desc.trim(), dueEpochDay = day, priority = priority, listId = listId)
                        )
                    },
                    shape = CircleShape, containerColor = Color(0xFFD6D6D6), contentColor = Color.Black,
                ) { Icon(Icons.Filled.ArrowUpward, "Guardar") }
            }
            TextButton(onClick = { onMore(title, listId, day) }) { Text("Más opciones (hora, repetir, aviso)…") }
        }
    }
}

@Composable
private fun HabitActionsDialog(
    row: HabitRow,
    onDismiss: () -> Unit,
    onAdd: (Double, String?) -> Unit,
    onWrite: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit,
) {
    val h: Habit = row.habit
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(h.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (h.type) {
                    TrackingType.QUIT ->
                        Text("¿Recaíste? Reiniciar pone el contador en cero y queda registrado en tu diario.")
                    TrackingType.JOURNAL -> {
                        Text("Progreso: ${HabitLogic.formatNumber(row.progress)} / ${HabitLogic.amountLabel(h.target, h.unit)}")
                        Text(
                            "Las palabras se cuentan de las notas del diario etiquetadas con este hábito.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text("Progreso: ${HabitLogic.formatNumber(row.progress)} / ${HabitLogic.amountLabel(h.target, h.unit)}")
                        OutlinedTextField(
                            amount, { amount = it.replace(',', '.') },
                            label = { Text("Cantidad (${h.unit})") },
                            placeholder = { Text("1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            note, { note = it },
                            label = { Text("Nota (opcional)") },
                            minLines = 2,
                        )
                        TextButton(onClick = onUndo) { Text("Deshacer último registro") }
                    }
                }
                TextButton(onClick = onEdit) { Text("Editar hábito") }
            }
        },
        confirmButton = {
            when (h.type) {
                TrackingType.QUIT ->
                    TextButton(onClick = onReset) { Text("Reiniciar contador", color = MaterialTheme.colorScheme.error) }
                TrackingType.JOURNAL ->
                    TextButton(onClick = onWrite) { Text("Escribir nota") }
                else ->
                    TextButton(onClick = { onAdd(amount.toDoubleOrNull() ?: 1.0, note.trim().ifBlank { null }) }) {
                        Text(if (amount.isBlank()) "Añadir 1" else "Añadir")
                    }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
