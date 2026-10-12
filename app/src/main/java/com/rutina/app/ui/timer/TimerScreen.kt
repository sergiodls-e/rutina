package com.rutina.app.ui.timer

import com.rutina.app.timer.useHabit
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rutina.app.data.Habit
import com.rutina.app.data.TrackingType
import com.rutina.app.timer.TimerController
import com.rutina.app.timer.TimerMode
import com.rutina.app.timer.TimerState
import com.rutina.app.ui.theme.Accent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es = Locale("es", "MX")
private val eventFmt = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss", es)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(timer: TimerController, habitsFlow: Flow<List<Habit>>, onClose: () -> Unit) {
    val s by timer.state.collectAsState()
    val finished by timer.finished.collectAsState()
    val allHabits by habitsFlow.collectAsState(initial = emptyList())
    val habits = remember(allHabits) { allHabits.filter { it.type == TrackingType.COUNT } }
    val ctx = LocalContext.current

    // Reloj de pantalla: se actualiza ~30 veces por segundo mientras corre.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val cur by rememberUpdatedState(s)
    LaunchedEffect(s.running) {
        while (cur.running) {
            now = System.currentTimeMillis()
            if (cur.mode == TimerMode.COUNTDOWN && cur.remaining(now) == 0L) timer.onCountdownFinished()
            delay(33)
        }
        now = System.currentTimeMillis()
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun start() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        timer.start()
    }

    var askStop by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val elapsed = s.elapsed(now)

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    fun saveAndToast() {
        val habit = s.habitName
        val e = s.elapsed(System.currentTimeMillis())
        timer.stop(save = true)
        toast(
            if (habit != null) "Registrado: ${TimerController.formatDuration(e)} en $habit"
            else "Sesión guardada: ${TimerController.formatDuration(e)}"
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar") } },
                    title = { Text("Temporizador", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                )
                TabRow(selectedTabIndex = s.mode.ordinal, containerColor = Color.Black) {
                    listOf(TimerMode.STOPWATCH to "Cronómetro", TimerMode.COUNTDOWN to "Cuenta atrás").forEach { (m, label) ->
                        Tab(
                            selected = s.mode == m,
                            onClick = { if (!timer.setMode(m)) toast("Detén el temporizador actual para cambiar") },
                            text = { Text(label, fontWeight = if (s.mode == m) FontWeight.Bold else FontWeight.Normal) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Concentración", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { showHelp = true }) { Icon(Icons.Filled.HelpOutline, "Ayuda") }
            }
            HabitPicker(s, habits) { h -> if (h != null) timer.useHabit(h) else timer.setHabit(null, null) }
            HorizontalDivider(Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)

            when (s.mode) {
                TimerMode.STOPWATCH -> StopwatchPanel(
                    s = s, elapsed = elapsed,
                    onStart = { start() }, onPause = timer::pause, onLap = timer::lap,
                    onSave = { saveAndToast() },
                    onReset = { if (elapsed > 60_000) askReset = true else timer.stop(save = false) },
                )
                TimerMode.COUNTDOWN -> CountdownPanel(
                    s = s, now = now,
                    onDuration = timer::setDuration,
                    onStart = { start() }, onPause = timer::pause,
                    onStop = {
                        when {
                            elapsed < 5_000 -> timer.stop(save = false)
                            s.habitId != null -> askStop = true
                            else -> saveAndToast()
                        }
                    },
                )
            }

            if (s.events.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 16.dp).background(Color(0xFF1C1C1C)).padding(horizontal = 40.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    s.events.takeLast(8).forEach { ev ->
                        Row {
                            Text(
                                Instant.ofEpochMilli(ev.time).atZone(java.time.ZoneId.systemDefault()).format(eventFmt).replace(".", ""),
                                Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(ev.kind.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (askStop) {
        AlertDialog(
            onDismissRequest = { askStop = false },
            title = { Text("¿Registrar el tiempo?") },
            text = { Text("${TimerController.formatDuration(elapsed)} en \"${s.habitName}\".") },
            confirmButton = { TextButton(onClick = { askStop = false; saveAndToast() }) { Text("Registrar") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { askStop = false; timer.stop(save = false) }) {
                        Text("Descartar", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { askStop = false }) { Text("Seguir") }
                }
            },
        )
    }

    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("¿Reiniciar sin guardar?") },
            text = { Text("Se perderán ${TimerController.formatDuration(elapsed)}. Para registrarlos usa el botón de guardar.") },
            confirmButton = {
                TextButton(onClick = { askReset = false; timer.stop(save = false) }) {
                    Text("Reiniciar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text("Cancelar") } },
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Concentración") },
            text = {
                Text(
                    "Elige un hábito y, al terminar, el tiempo se registra en él: en minutos u horas si esa es su " +
                        "unidad; si no, cuenta como una vez.\n\nEl temporizador sigue corriendo aunque cierres la app; " +
                        "lo verás en la notificación, donde también puedes pausarlo o detenerlo."
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Entendido") } },
        )
    }

    finished?.let { f ->
        AlertDialog(
            onDismissRequest = timer::clearFinished,
            icon = { Icon(Icons.Filled.Timer, null, tint = Accent) },
            title = { Text("¡Tiempo cumplido!") },
            text = {
                Text(
                    "${TimerController.formatDuration(f.durationMs)} de concentración" +
                        (f.habitName?.let { ", registrados en \"$it\"." } ?: ".")
                )
            },
            confirmButton = { TextButton(onClick = timer::clearFinished) { Text("Listo") } },
        )
    }
}

@Composable
private fun HabitPicker(s: TimerState, habits: List<Habit>, onPick: (Habit?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = habits.firstOrNull { it.id == s.habitId }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp))
                .background(selected?.let { Color(it.color) } ?: MaterialTheme.colorScheme.surfaceVariant)
                .clickable { open = true }.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (selected != null) Color.Black else Color.White
            Text(selected?.name ?: s.habitName ?: "Seleccionar hábito", color = fg)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = fg)
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("Ninguno") }, onClick = { onPick(null); open = false })
            if (habits.isEmpty()) {
                DropdownMenuItem(text = { Text("Crea un hábito de Recuento para registrar tiempo") }, onClick = { open = false }, enabled = false)
            }
            habits.forEach { h ->
                DropdownMenuItem(
                    text = { Text(h.name) },
                    trailingIcon = { Text(h.unit, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
                    leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(h.color))) },
                    onClick = { onPick(h); open = false },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Cronómetro

@Composable
private fun StopwatchPanel(
    s: TimerState,
    elapsed: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onLap: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFDDDDDD)).padding(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Text(
                TimerController.formatStopwatch(elapsed), fontSize = 52.sp, color = Color.Black,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("Vuelta", Icons.Filled.Flag, primary = false, enabled = s.running, onClick = onLap, modifier = Modifier.weight(1f))
            if (s.running) BigButton("Pausa", Icons.Filled.Pause, primary = true, onClick = onPause, modifier = Modifier.weight(1f))
            else BigButton(if (s.active) "Reanudar" else "Iniciar", Icons.Filled.PlayArrow, primary = true, onClick = onStart, modifier = Modifier.weight(1f))
        }
        if (s.active) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RingButton(Icons.AutoMirrored.Filled.Undo, "Reiniciar", Color(0xFFE57373), onReset)
                RingButton(Icons.Filled.EditNote, "Guardar", Accent, onSave)
            }
        }
        if (s.laps.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 16.dp)) {
                s.laps.withIndex().reversed().forEach { (i, total) ->
                    val split = total - (if (i > 0) s.laps[i - 1] else 0L)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("Vuelta ${i + 1}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+${TimerController.formatStopwatch(split)}", Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        Text(TimerController.formatStopwatch(total), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Cuenta atrás

private val presetsMin = listOf(5, 10, 15, 20, 25, 30, 45, 60, 90)

@Composable
private fun CountdownPanel(
    s: TimerState,
    now: Long,
    onDuration: (Long) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (!s.active) {
            val total = s.durationMs / 1000
            val h = (total / 3600).toInt()
            val m = ((total % 3600) / 60).toInt()
            val sec = (total % 60).toInt()
            fun set(hh: Int, mm: Int, ss: Int) = onDuration((hh * 3600L + mm * 60L + ss) * 1000L)

            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    WheelPicker(24, h, { set(it, m, sec) }, "horas")
                    WheelPicker(60, m, { set(h, it, sec) }, "min")
                    WheelPicker(60, sec, { set(h, m, it) }, "s")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presetsMin.forEach { p ->
                    val sel = s.durationMs == p * 60_000L
                    Surface(
                        onClick = { onDuration(p * 60_000L) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (sel) Accent else Color(0xFF2A2A2A),
                        contentColor = if (sel) Color.Black else Color.White,
                    ) { Text(if (p < 60) "$p min" else "${p / 60} h${if (p % 60 > 0) " ${p % 60}" else ""}", Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                }
            }
        } else {
            val remaining = s.remaining(now)
            val fraction = if (s.durationMs > 0) remaining.toFloat() / s.durationMs else 0f
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(250.dp)) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val w = 10.dp.toPx()
                    drawCircle(Color(0xFF333333), style = Stroke(w))
                    drawArc(
                        Accent, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(TimerController.formatClock(remaining), fontSize = 46.sp, fontFamily = FontFamily.Monospace)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(TimerController.formatDuration(s.durationMs), fontSize = 18.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("Detener", Icons.Filled.Stop, primary = false, enabled = s.active, onClick = onStop, modifier = Modifier.weight(1f))
            when {
                s.running -> BigButton("Pausa", Icons.Filled.Pause, primary = true, onClick = onPause, modifier = Modifier.weight(1f))
                s.active -> BigButton("Reanudar", Icons.Filled.PlayArrow, primary = true, onClick = onStart, modifier = Modifier.weight(1f))
                else -> BigButton(
                    "Iniciar", Icons.Filled.PlayArrow, primary = true, enabled = s.durationMs >= 1000,
                    onClick = onStart, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Selector tipo rueda: desliza para elegir un número. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(count: Int, value: Int, onChange: (Int) -> Unit, label: String) {
    val itemH = 48.dp
    val itemPx = with(LocalDensity.current) { itemH.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val selected by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0).coerceIn(0, count - 1)
        }
    }
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress && selected != value) change(selected)
    }
    LaunchedEffect(value) {
        if (!state.isScrollInProgress && selected != value) state.scrollToItem(value)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemH),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.height(itemH * 3).width(56.dp),
        ) {
            items(count) { i ->
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        i.toString(),
                        fontSize = if (i == selected) 26.sp else 18.sp,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
    }
}

// ---------------------------------------------------------------- Botones

@Composable
private fun BigButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = if (primary) ButtonDefaults.buttonColors(containerColor = Color(0xFFDDDDDD), contentColor = Color.Black)
        else ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E), contentColor = Color.White),
        modifier = modifier.height(64.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 17.sp)
    }
}

@Composable
private fun RingButton(icon: ImageVector, label: String, ring: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color(0xFFDDDDDD),
            contentColor = Color.Black,
            border = BorderStroke(4.dp, ring.copy(alpha = 0.8f)),
            modifier = Modifier.size(72.dp),
        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(30.dp)) } }
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}
