package com.rutina.app

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.rutina.app.ui.timer.TimerScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rutina.app.domain.Suggestions
import com.rutina.app.ui.habit.HabitEditorViewModel
import com.rutina.app.ui.habit.HabitFormScreen
import com.rutina.app.ui.habit.SuggestionsScreen
import com.rutina.app.ui.theme.RutinaTheme
import com.rutina.app.ui.today.TodayScreen
import com.rutina.app.ui.journal.JournalScreen
import com.rutina.app.ui.journal.JournalViewModel
import com.rutina.app.ui.journal.NoteEditorScreen
import com.rutina.app.ui.journal.NoteEditorViewModel
import com.rutina.app.data.Task
import com.rutina.app.ui.tasks.ManageListsScreen
import com.rutina.app.ui.tasks.ManageListsViewModel
import com.rutina.app.ui.tasks.TaskEditorScreen
import com.rutina.app.ui.tasks.TaskEditorViewModel
import com.rutina.app.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {

    /** Se pone en true al abrir la app desde la notificación del temporizador. */
    private val openTimer = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RutinaApp
        val db = app.db
        openTimer.value = intent?.getBooleanExtra(EXTRA_OPEN_TIMER, false) == true
        setContent {
            RutinaTheme {
                val nav = rememberNavController()
                LaunchedEffect(openTimer.value) {
                    if (openTimer.value) {
                        nav.navigate("timer") { launchSingleTop = true }
                        openTimer.value = false
                    }
                }
                val timerState by app.timer.state.collectAsState()
                NavHost(nav, startDestination = "main") {
                    composable("timer") {
                        TimerScreen(app.timer, remember { db.habits().observeActive() }, onClose = { nav.popBackStack() })
                    }
                    composable("main") {
                        val todayVm: TodayViewModel = viewModel { TodayViewModel(db) }
                        val journalVm: JournalViewModel = viewModel { JournalViewModel(db) }
                        MainTabs(
                            todayVm = todayVm,
                            journalVm = journalVm,
                            onNewNote = { habitId -> nav.navigate("note/form?habit=${habitId ?: -1L}") },
                            onEditNote = { id -> nav.navigate("note/form?id=$id") },
                            onNewHabit = { nav.navigate("habit/suggest") },
                            onEditHabit = { id -> nav.navigate("habit/form?id=$id") },
                            onEditTask = { id -> nav.navigate("task/form?id=$id") },
                            onFullTaskEditor = { title, listId, day ->
                                nav.navigate(
                                    "task/form?title=${Uri.encode(title)}&list=${listId ?: -1L}&day=${day ?: Long.MIN_VALUE}"
                                )
                            },
                            onManageLists = { nav.navigate("lists") },
                            timerActive = timerState.active,
                            onOpenTimer = { nav.navigate("timer") { launchSingleTop = true } },
                        )
                    }
                    composable(
                        "task/form?id={id}&title={title}&list={list}&day={day}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                            navArgument("title") { type = NavType.StringType; defaultValue = "" },
                            navArgument("list") { type = NavType.LongType; defaultValue = -1L },
                            navArgument("day") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                        ),
                    ) { entry ->
                        val a = entry.arguments
                        val id = a?.getLong("id") ?: 0L
                        val initial = Task(
                            title = a?.getString("title").orEmpty(),
                            listId = a?.getLong("list")?.takeIf { it > 0 },
                            dueEpochDay = a?.getLong("day")?.takeIf { it != Long.MIN_VALUE },
                        )
                        val vm: TaskEditorViewModel = viewModel { TaskEditorViewModel(db, id, initial) }
                        TaskEditorScreen(vm, onClose = { nav.popBackStack() })
                    }
                    composable(
                        "note/form?id={id}&habit={habit}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                            navArgument("habit") { type = NavType.LongType; defaultValue = -1L },
                        ),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        val habit = entry.arguments?.getLong("habit")?.takeIf { it > 0 }
                        val vm: NoteEditorViewModel = viewModel { NoteEditorViewModel(db, id, habit) }
                        NoteEditorScreen(vm, onClose = { nav.popBackStack() })
                    }
                    composable("lists") {
                        val vm: ManageListsViewModel = viewModel { ManageListsViewModel(db) }
                        ManageListsScreen(vm, onClose = { nav.popBackStack() })
                    }
                    composable("habit/suggest") {
                        SuggestionsScreen(
                            onClose = { nav.popBackStack() },
                            onPick = { g, i -> nav.navigate("habit/form?g=$g&i=$i") { popUpTo("main") } },
                            onCustom = { nav.navigate("habit/form") { popUpTo("main") } },
                        )
                    }
                    composable(
                        "habit/form?id={id}&g={g}&i={i}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                            navArgument("g") { type = NavType.IntType; defaultValue = -1 },
                            navArgument("i") { type = NavType.IntType; defaultValue = -1 },
                        ),
                    ) { entry ->
                        val args = entry.arguments
                        val id = args?.getLong("id") ?: 0L
                        val g = args?.getInt("g") ?: -1
                        val i = args?.getInt("i") ?: -1
                        val preset = Suggestions.groups.getOrNull(g)?.items?.getOrNull(i)
                        val vm: HabitEditorViewModel = viewModel { HabitEditorViewModel(db, id, preset) }
                        HabitFormScreen(vm, onClose = { nav.popBackStack("main", inclusive = false) })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TIMER, false)) openTimer.value = true
    }

    companion object {
        const val EXTRA_OPEN_TIMER = "open_timer"
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Hoy", Icons.Filled.CheckBox),
    JOURNAL("Diario", Icons.Filled.LibraryBooks),
    CALENDAR("Calendario", Icons.Filled.CalendarMonth),
    SETTINGS("Ajustes", Icons.Filled.Tune),
}

@Composable
private fun MainTabs(
    todayVm: TodayViewModel,
    journalVm: JournalViewModel,
    onNewNote: (habitId: Long?) -> Unit,
    onEditNote: (Long) -> Unit,
    onNewHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onFullTaskEditor: (String, Long?, Long?) -> Unit,
    onManageLists: () -> Unit,
    timerActive: Boolean,
    onOpenTimer: () -> Unit,
) {
    var tab by rememberSaveable { mutableTabState() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF151515)) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF333333)),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (Tab.entries[tab]) {
                Tab.TODAY -> TodayScreen(
                    todayVm, onNewHabit, onEditHabit, onEditTask, onFullTaskEditor, onManageLists,
                    onWriteNote = { habitId -> onNewNote(habitId) },
                    timerActive = timerActive,
                    onOpenTimer = onOpenTimer,
                )
                Tab.JOURNAL -> JournalScreen(
                    journalVm,
                    onNewNote = { onNewNote(null) },
                    onEditNote = onEditNote,
                    onOpenTask = onEditTask,
                )
                Tab.CALENDAR -> ComingSoon("Calendario", "Llega en la Fase 6: vista semanal por horas y programar tareas.")
                Tab.SETTINGS -> ComingSoon(
                    "Ajustes",
                    "Llega en la Fase 9: tema, inicio de semana, exportar/importar respaldo y borrar datos.\n\n" +
                        "Tus datos viven solo en este teléfono: la app no tiene permiso de Internet.",
                )
            }
        }
    }
}

private fun mutableTabState() = androidx.compose.runtime.mutableIntStateOf(0)

@Composable
private fun ComingSoon(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
