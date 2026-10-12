package com.rutina.app.ui.stats

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitLog
import com.rutina.app.data.Note
import com.rutina.app.data.Prefs
import com.rutina.app.data.Task
import com.rutina.app.data.TimerSession
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.CellKind
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.HabitStats
import com.rutina.app.domain.NoteLogic
import com.rutina.app.ui.common.cellColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class ReportPeriod(val label: String) { WEEK("Semana"), MONTH("Mes") }

private data class Src(
    val habits: List<Habit>, val logs: List<HabitLog>, val notes: List<Note>,
    val tasks: List<Task>, val sessions: List<TimerSession>,
)

private data class Sel(val period: ReportPeriod, val anchor: LocalDate)

class ReportsViewModel(db: AppDatabase, private val prefs: Prefs) : ViewModel() {
    private val sel = MutableStateFlow(Sel(ReportPeriod.WEEK, LocalDate.now()))

    private val src = combine(
        db.habits().observeActive(), db.habits().observeAllLogs(), db.notes().observeAll(), db.tasks().observeAll(), db.timers().observeAll(),
    ) { h, l, n, t, s -> Src(h, l, n, t, s) }

    val bitmap: StateFlow<Bitmap?> = combine(src, sel, prefs.weekStart) { s, se, w -> Report.render(s, se.period, se.anchor, w) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selection: StateFlow<Pair<ReportPeriod, LocalDate>> = MutableStateFlow(ReportPeriod.WEEK to LocalDate.now()).also { out ->
        viewModelScope.launch { sel.collect { out.value = it.period to it.anchor } }
    }

    fun setPeriod(p: ReportPeriod) { sel.value = sel.value.copy(period = p) }

    fun shift(dir: Int) {
        val s = sel.value
        val next = if (s.period == ReportPeriod.WEEK) s.anchor.plusWeeks(dir.toLong()) else s.anchor.plusMonths(dir.toLong())
        if (next <= LocalDate.now().plusDays(6)) sel.value = s.copy(anchor = next)
    }

    /** Guarda la imagen en una copia compartible y devuelve su enlace. */
    suspend fun sharePng(ctx: Context, bmp: Bitmap): Uri = withContext(Dispatchers.IO) {
        val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.endsWith(".png") }?.forEach { it.delete() }
        val f = java.io.File(dir, "rutina-informe-${LocalDate.now()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    suspend fun savePng(ctx: Context, uri: Uri, bmp: Bitmap): Boolean = withContext(Dispatchers.IO) {
        runCatching { ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } != null }
            .getOrDefault(false)
    }
}

/** Dibuja el informe como imagen (sin Internet, con Canvas de Android). */
private object Report {
    private val es = Locale("es", "MX")
    private const val W = 1080
    private val BG = 0xFF151515.toInt()
    private val CARD = 0xFF222222.toInt()
    private val TEXT = 0xFFEDEDED.toInt()
    private val DIM = 0xFF9E9E9E.toInt()

    fun range(period: ReportPeriod, anchor: LocalDate, weekStart: DayOfWeek): Pair<LocalDate, LocalDate> = when (period) {
        ReportPeriod.WEEK -> HabitLogic.weekStartOf(anchor, weekStart).let { it to it.plusDays(6) }
        ReportPeriod.MONTH -> anchor.withDayOfMonth(1).let { it to it.withDayOfMonth(it.lengthOfMonth()) }
    }

    fun render(s: Src, period: ReportPeriod, anchor: LocalDate, weekStart: DayOfWeek): Bitmap {
        val today = LocalDate.now()
        val (from, to) = range(period, anchor, weekStart)
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
        val habits = s.habits.filter { HabitStats.created(it) <= to }
        val byHabit = s.logs.groupBy { it.habitId }
        val journal = HabitStats.journalLogs(s.notes)

        val rowH = 64f
        val top = 250f
        val footer = 260f
        val h = (top + habits.size.coerceAtLeast(1) * rowH + footer).toInt()
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Encabezado
        p.color = TEXT; p.textSize = 54f; p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(if (period == ReportPeriod.WEEK) "Mi semana" else "Mi mes", 48f, 90f, p)
        p.typeface = Typeface.DEFAULT; p.textSize = 32f; p.color = DIM
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", es)
        c.drawText(
            if (period == ReportPeriod.WEEK) "${from.format(fmt)} – ${to.format(fmt)}".replace(".", "")
            else "${from.month.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }} ${from.year}",
            48f, 140f, p,
        )
        p.textSize = 26f
        c.drawText("Rutina", W - 48f - p.measureText("Rutina"), 90f, p)

        // Columnas de días
        val nameW = 300f
        val pctW = 110f
        val gridX = 48f + nameW + 16f
        val gridW = W - gridX - pctW - 48f
        val cell = gridW / days.size
        p.textSize = if (days.size > 7) 18f else 26f
        p.textAlign = Paint.Align.CENTER
        days.forEachIndexed { i, d ->
            val label = if (days.size > 7) "${d.dayOfMonth}" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, es).replace(".", "")
            p.color = if (d == today) 0xFF8FD3C1.toInt() else DIM
            c.drawText(label, gridX + cell * i + cell / 2, top - 22f, p)
        }
        p.textAlign = Paint.Align.LEFT

        if (habits.isEmpty()) {
            p.color = DIM; p.textSize = 30f
            c.drawText("Aún no tienes hábitos.", 48f, top + 40f, p)
        }

        var y = top
        habits.forEach { hb ->
            val logs = HabitStats.logsOf(hb, byHabit, journal)
            val totals = HabitStats.dayValues(hb, logs)
            val resets = if (hb.type == TrackingType.QUIT) HabitStats.resetDays(logs) else emptySet()
            val color = Color(hb.color)
            // Nombre
            p.color = hb.color
            c.drawRoundRect(RectF(48f, y + 8f, 48f + nameW, y + rowH - 8f), 12f, 12f, p)
            p.color = 0xFF000000.toInt(); p.textSize = 26f
            c.drawText(ellipsize(hb.name, p, nameW - 24f), 60f, y + rowH / 2 + 9f, p)
            // Celdas
            var done = 0
            var counted = 0
            days.forEachIndexed { i, d ->
                val cl = HabitStats.cell(hb, d, totals, resets, today)
                if (cl.kind != CellKind.FUTURE && cl.kind != CellKind.SKIP && HabitLogic.isScheduled(hb, d)) {
                    counted++; if (cl.kind == CellKind.DONE) done++
                }
                p.color = cellColor(cl, color).toArgb()
                val pad = if (days.size > 7) 2f else 6f
                c.drawRoundRect(RectF(gridX + cell * i + pad, y + 10f, gridX + cell * (i + 1) - pad, y + rowH - 10f), 8f, 8f, p)
            }
            // Porcentaje
            p.color = TEXT; p.textSize = 28f; p.typeface = Typeface.DEFAULT_BOLD
            val pct = if (counted == 0) "–" else "${done * 100 / counted}%"
            c.drawText(pct, W - 48f - p.measureText(pct), y + rowH / 2 + 10f, p)
            p.typeface = Typeface.DEFAULT
            y += rowH
        }

        // Resumen
        val inRange = { ms: Long -> HabitLogic.dateOf(ms).let { it in from..to } }
        val tasksDone = s.tasks.count { it.done && !it.skipped && it.doneAt?.let(inRange) == true }
        val words = s.notes.filter { inRange(it.timestamp) }.sumOf { NoteLogic.wordCount(it.content) }
        val focus = s.sessions.filter { inRange(it.start) }.sumOf { it.activeMillis }
        y += 30f
        val boxW = (W - 96f - 40f) / 3
        listOf(
            "Tareas completadas" to "$tasksDone",
            "Palabras escritas" to "%,d".format(es, words),
            "Concentración" to com.rutina.app.timer.TimerController.formatDuration(focus),
        ).forEachIndexed { i, (label, value) ->
            val x = 48f + i * (boxW + 20f)
            p.color = CARD
            c.drawRoundRect(RectF(x, y, x + boxW, y + 160f), 20f, 20f, p)
            p.color = DIM; p.textSize = 24f
            c.drawText(label, x + 24f, y + 50f, p)
            p.color = TEXT; p.textSize = 44f; p.typeface = Typeface.DEFAULT_BOLD
            c.drawText(ellipsize(value, p, boxW - 40f), x + 24f, y + 120f, p)
            p.typeface = Typeface.DEFAULT
        }
        return bmp
    }

    private fun ellipsize(t: String, p: Paint, max: Float): String {
        if (p.measureText(t) <= max) return t
        var s = t
        while (s.isNotEmpty() && p.measureText("$s…") > max) s = s.dropLast(1)
        return "$s…"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(vm: ReportsViewModel, onClose: () -> Unit) {
    val bmp by vm.bitmap.collectAsState()
    val sel by vm.selection.collectAsState()
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val b = bmp
        if (uri != null && b != null) scope.launch {
            val ok = vm.savePng(ctx, uri, b)
            Toast.makeText(ctx, if (ok) "Imagen guardada ✓" else "No se pudo guardar", Toast.LENGTH_SHORT).show()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) } },
                title = { Text("Informes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPeriod.entries.forEach { p ->
                    FilterChip(selected = sel.first == p, onClick = { vm.setPeriod(p) }, label = { Text(p.label) })
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.Filled.ChevronLeft, "Anterior") }
                IconButton(onClick = { vm.shift(1) }) { Icon(Icons.Filled.ChevronRight, "Siguiente") }
            }
            val b = bmp
            if (b == null) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Image(b.asImageBitmap(), "Informe", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { save.launch("rutina-informe-${LocalDate.now()}.png") }, Modifier.weight(1f)) { Text("Guardar") }
                    Button(
                        onClick = {
                            scope.launch {
                                val uri = vm.sharePng(ctx, b)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = android.content.ClipData.newRawUri("informe", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { ctx.startActivity(Intent.createChooser(send, "Compartir informe")) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD6D6D6), contentColor = Color.Black),
                    ) { Text("Compartir") }
                }
                Text(
                    "La imagen se crea en tu teléfono; solo sale si tú la compartes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
