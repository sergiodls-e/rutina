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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
    }
}

private val IdKey = ActionParameters.Key<Long>("id")

// ---------------------------------------------------------------- Hábitos

class HabitsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = (context.applicationContext as RutinaApp).db
        val initial = WidgetData.habits(db).first()
        provideContent {
            val rows by remember { WidgetData.habits(db) }.collectAsState(initial)
            HabitsContent(rows)
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
        db.habits().insertLog(HabitLog(habitId = id, timestamp = System.currentTimeMillis(), value = 1.0))
        Widgets.refresh(context)
    }
}

@Composable
private fun HabitsContent(rows: List<WHabit>) {
    Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(10.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Hábitos de hoy", modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            // Los hábitos de Dejar y Peso no tienen meta diaria: no cuentan en "hechos de hoy".
            val goals = rows.filter { it.type != TrackingType.QUIT && it.type != TrackingType.WEIGHT }
            Text("${goals.count { it.met }}/${goals.size}", style = TextStyle(color = Dim, fontSize = 13.sp))
        }
        if (rows.isEmpty()) {
            Text("No hay hábitos para hoy", modifier = GlanceModifier.padding(4.dp), style = TextStyle(color = Dim, fontSize = 13.sp))
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
            Text(
                r.text, maxLines = 1, modifier = GlanceModifier.defaultWeight().padding(horizontal = 8.dp),
                style = TextStyle(color = White, fontSize = 13.sp),
            )
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
        val initial = WidgetData.tasks(db).first()
        provideContent {
            val rows by remember { WidgetData.tasks(db) }.collectAsState(initial)
            TasksContent(rows)
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
private fun TasksContent(rows: List<WTask>) {
    Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(10.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tareas", modifier = GlanceModifier.defaultWeight(),
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
