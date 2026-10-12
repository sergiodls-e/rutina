package com.rutina.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rutina.app.MainActivity
import com.rutina.app.RutinaApp
import com.rutina.app.data.HabitLog
import com.rutina.app.data.TaskOps
import com.rutina.app.data.TrackingType
import kotlinx.coroutines.flow.first

private val Bg = Color(0xFF1C1C1C)
private val RowBg = Color(0xFF2A2A2A)
private val ButtonBg = Color(0xFF3A3A3A)
private val White = ColorProvider(Color.White)
private val Dim = ColorProvider(Color(0xFFAAAAAA))
private val Black = ColorProvider(Color.Black)
private val Red = ColorProvider(Color(0xFFE57373))

/** Actualiza todos los widgets colocados (si no hay ninguno, no hace nada). */
object Widgets {
    suspend fun refresh(context: Context) {
        runCatching { HabitsWidget().updateAll(context) }
        runCatching { TasksWidget().updateAll(context) }
        runCatching { HabitReportWidget().updateAll(context) }
        runCatching { JournalWidget().updateAll(context) }
    }
}

private val IdKey = ActionParameters.Key<Long>("id")

// ---------------------------------------------------------------- Hábitos

class HabitsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = (context.applicationContext as RutinaApp).db
        val wid = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val sel0 = WidgetConfig.get(context, wid, "sel", "today")
        val initial = WidgetData.habits(db, sel0).first()
        provideContent {
            val sel by remember { WidgetConfig.flow(context, wid, "sel", "today") }.collectAsState(sel0)
            val data by remember(sel) { WidgetData.habits(db, sel) }.collectAsState(initial)
            HabitsContent(data.title, data.rows)
        }
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}

/** "+1" desde el widget. */
class AddHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[IdKey] ?: return
        val db = (context.applicationContext as RutinaApp).db
        val step = db.habits().get(id)?.step ?: 1.0
        db.habits().insertLog(HabitLog(habitId = id, timestamp = System.currentTimeMillis(), value = step))
        Widgets.refresh(context)
    }
}

@Composable
private fun HabitsContent(title: String, rows: List<WHabit>) {
    Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(10.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            // Los hábitos de Dejar y Peso no tienen meta diaria: no cuentan en "hechos de hoy".
            val goals = rows.filter { it.type != TrackingType.QUIT && it.type != TrackingType.WEIGHT }
            Text("${goals.count { it.met }}/${goals.size}", style = TextStyle(color = Dim, fontSize = 13.sp))
        }
        if (rows.isEmpty()) {
            Text("No hay hábitos aquí", modifier = GlanceModifier.padding(4.dp), style = TextStyle(color = Dim, fontSize = 13.sp))
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { it.id }) { r -> HabitRow(r) }
            }
        }
    }
}

@Composable
private fun HabitRow(r: WHabit) {
    Column(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().height(42.dp).cornerRadius(10.dp).background(RowBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier.width(118.dp).fillMaxHeight().background(Color(r.color)).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(r.name, maxLines = 1, style = TextStyle(color = Black, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
            Column(GlanceModifier.defaultWeight().padding(horizontal = 8.dp)) {
                Text(r.text, maxLines = 1, style = TextStyle(color = White, fontSize = 13.sp))
                if (r.fraction > 0f || r.type == TrackingType.COUNT || r.type == TrackingType.HEALTH || r.type == TrackingType.JOURNAL) {
                    Spacer(GlanceModifier.height(3.dp))
                    ProgressBar(r.fraction, Color(r.color))
                }
            }
            val action = if (r.type == TrackingType.COUNT) {
                actionRunCallback<AddHabitAction>(actionParametersOf(IdKey to r.id))
            } else actionStartActivity<MainActivity>()
            Box(
                GlanceModifier.size(42.dp).background(if (r.met) Color(r.color) else ButtonBg).clickable(action),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        r.type == TrackingType.QUIT -> "›"
                        r.type == TrackingType.JOURNAL -> "✎"
                        r.type == TrackingType.MOOD -> "☺"
                        r.type == TrackingType.WEIGHT -> "›"
                        r.met -> "✓"
                        r.type == TrackingType.HEALTH -> "↻"
                        else -> "+"
                    },
                    style = TextStyle(color = if (r.met) Black else White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Tareas

class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = (context.applicationContext as RutinaApp).db
        val wid = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val sel0 = WidgetConfig.get(context, wid, "sel", "today")
        val initial = WidgetData.tasks(db, sel0).first()
        provideContent {
            val sel by remember { WidgetConfig.flow(context, wid, "sel", "today") }.collectAsState(sel0)
            val data by remember(sel) { WidgetData.tasks(db, sel) }.collectAsState(initial)
            TasksContent(data.title, data.rows)
        }
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

/** Completar una tarea desde el widget. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[IdKey] ?: return
        val db = (context.applicationContext as RutinaApp).db
        db.tasks().get(id)?.let { if (!it.done) TaskOps.complete(db, it) }
        Widgets.refresh(context)
    }
}

@Composable
private fun TasksContent(title: String, rows: List<WTask>) {
    Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(10.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Text("${rows.size}", style = TextStyle(color = Dim, fontSize = 13.sp))
        }
        if (rows.isEmpty()) {
            Text("¡Todo listo! No hay pendientes.", modifier = GlanceModifier.padding(4.dp), style = TextStyle(color = Dim, fontSize = 13.sp))
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { it.id }) { t -> TaskRow(t) }
            }
        }
    }
}

@Composable
private fun TaskRow(t: WTask) {
    Column(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().height(42.dp).cornerRadius(10.dp).background(RowBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(GlanceModifier.width(10.dp))
            Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(Color(t.color))) { }
            Text(
                t.title, maxLines = 1,
                modifier = GlanceModifier.defaultWeight().padding(horizontal = 8.dp).clickable(actionStartActivity<MainActivity>()),
                style = TextStyle(color = White, fontSize = 13.sp),
            )
            if (t.due != null) {
                Text(t.due, maxLines = 1, style = TextStyle(color = if (t.overdue) Red else Dim, fontSize = 11.sp))
                Spacer(GlanceModifier.width(6.dp))
            }
            Box(
                GlanceModifier.size(42.dp).background(ButtonBg)
                    .clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(IdKey to t.id))),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}


/** Barrita de progreso hecha con cajas (los widgets no tienen lienzo). */
@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    val f = fraction.coerceIn(0f, 1f)
    Row(GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp).background(ButtonBg)) {
        if (f > 0f) Box(GlanceModifier.width((150 * f).dp).height(4.dp).cornerRadius(2.dp).background(color)) { }
    }
}

// ---------------------------------------------------------------- Informe del hábito

class HabitReportWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = (context.applicationContext as RutinaApp).db
        val wid = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val h0 = WidgetConfig.get(context, wid, "habit", "")
        val s0 = WidgetConfig.get(context, wid, "history", "1")
        val initial = WidgetData.report(db, h0.toLongOrNull(), s0 == "1").first()
        provideContent {
            val hid by remember { WidgetConfig.flow(context, wid, "habit", "") }.collectAsState(h0)
            val hist by remember { WidgetConfig.flow(context, wid, "history", "1") }.collectAsState(s0)
            val data by remember(hid, hist) { WidgetData.report(db, hid.toLongOrNull(), hist == "1") }.collectAsState(initial)
            ReportContent(data, wid)
        }
    }
}

class HabitReportWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitReportWidget()
}

@Composable
private fun ReportContent(d: WReport, wid: Int) {
    val h = d.habit
    Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(10.dp)) {
        if (h == null) {
            // Toca para elegir el hábito (en Android 11 o anterior no existe "Configurar" al mantener pulsado).
            val configure = actionStartActivity(
                android.content.Intent().setClassName("com.rutina.app", "com.rutina.app.widget.WidgetConfigActivity")
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, wid)
            )
            Text(
                "Toca aquí para elegir un hábito",
                modifier = GlanceModifier.padding(6.dp).clickable(configure),
                style = TextStyle(color = Dim, fontSize = 13.sp),
            )
        } else ReportBody(h, d)
    }
}

@Composable
private fun ReportBody(h: WHabit, d: WReport) {
    Column(GlanceModifier.fillMaxSize()) {
        val open = actionStartActivity(
            android.content.Intent().setClassName("com.rutina.app", "com.rutina.app.MainActivity")
                .putExtra(MainActivity.EXTRA_HABIT_STATS, h.id)
        )
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(open)) {
                Box(GlanceModifier.cornerRadius(8.dp).background(Color(h.color)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(h.name, maxLines = 1, style = TextStyle(color = Black, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
                Spacer(GlanceModifier.height(4.dp))
                Text(h.text, maxLines = 1, style = TextStyle(color = White, fontSize = 13.sp))
            }
            val action = if (h.type == TrackingType.COUNT) actionRunCallback<AddHabitAction>(actionParametersOf(IdKey to h.id)) else open
            Box(
                GlanceModifier.size(42.dp).cornerRadius(10.dp).background(if (h.met) Color(h.color) else ButtonBg).clickable(action),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (h.met) "✓" else if (h.type == TrackingType.COUNT) "+" else "›",
                    style = TextStyle(color = if (h.met) Black else White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        d.history?.let { bmp ->
            Spacer(GlanceModifier.height(8.dp))
            androidx.glance.Image(
                androidx.glance.ImageProvider(bmp), contentDescription = "Historial",
                modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(open),
                contentScale = androidx.glance.layout.ContentScale.Fit,
            )
        }
    }
}

// ---------------------------------------------------------------- Nota del diario

class JournalWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = (context.applicationContext as RutinaApp).db
        val wid = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val t0 = WidgetConfig.get(context, wid, "tag", "")
        val initial = WidgetData.journal(db, t0.toLongOrNull()).first()
        provideContent {
            val tag by remember { WidgetConfig.flow(context, wid, "tag", "") }.collectAsState(t0)
            val data by remember(tag) { WidgetData.journal(db, tag.toLongOrNull()) }.collectAsState(initial)
            JournalContent(data)
        }
    }
}

class JournalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JournalWidget()
}

@Composable
private fun JournalContent(d: WJournal) {
    val open = actionStartActivity(
        android.content.Intent().setClassName("com.rutina.app", "com.rutina.app.MainActivity")
            .putExtra(MainActivity.EXTRA_NEW_NOTE, d.habitId ?: -1L)
    )
    Column(
        GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(12.dp).clickable(open),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nueva nota ✎", style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(8.dp))
        Box(
            GlanceModifier.fillMaxWidth().cornerRadius(10.dp).background(Color(d.tagColor ?: 0xFFB39DDB.toInt())).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { Text(d.tag, maxLines = 1, style = TextStyle(color = Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
        Spacer(GlanceModifier.height(6.dp))
        Column(
            GlanceModifier.fillMaxWidth().cornerRadius(10.dp).background(RowBg).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("es", "MX"))).replace(".", ""),
                style = TextStyle(color = White, fontSize = 12.sp),
            )
            Text("${d.wordsToday} ${if (d.wordsToday == 1) "palabra" else "palabras"} hoy", style = TextStyle(color = Dim, fontSize = 12.sp))
        }
    }
}
