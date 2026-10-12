package com.rutina.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.rutina.app.RutinaApp
import com.rutina.app.ui.theme.RutinaTheme
import kotlinx.coroutines.launch

private enum class WKind { HABITS, TASKS, REPORT, JOURNAL }

/** Una opción de la lista: valor guardado, texto y color opcional. */
private data class Opt(val value: String, val label: String, val color: Int? = null, val header: Boolean = false)

/** Se abre al colocar un widget (o al mantenerlo pulsado → Configurar) para elegir qué muestra. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val wid = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
        if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        // Solo se configuran widgets de Rutina (otra app no puede usar esta pantalla con widgets ajenos).
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(wid)?.provider
        if (info == null || info.packageName != packageName) { finish(); return }
        val provider = info.className
        val kind = when {
            provider.endsWith("TasksWidgetReceiver") -> WKind.TASKS
            provider.endsWith("HabitReportWidgetReceiver") -> WKind.REPORT
            provider.endsWith("JournalWidgetReceiver") -> WKind.JOURNAL
            else -> WKind.HABITS
        }
        val db = (application as RutinaApp).db
        val key = when (kind) { WKind.REPORT -> "habit"; WKind.JOURNAL -> "tag"; else -> "sel" }
        val default = when (kind) { WKind.HABITS, WKind.TASKS -> "today"; else -> "" }

        setContent {
            RutinaTheme(dark = true) {
                val lists by db.tasks().observeLists().collectAsState(emptyList())
                val filters by db.tasks().observeFilters().collectAsState(emptyList())
                val habits by db.habits().observeActive().collectAsState(emptyList())
                var picked by remember { mutableStateOf(WidgetConfig.get(this, wid, key, default)) }
                var history by remember { mutableStateOf(WidgetConfig.get(this, wid, "history", "1") == "1") }

                val options: List<Opt> = when (kind) {
                    WKind.HABITS -> listOf(Opt("today", "Hábitos de hoy"), Opt("all", "Todos los hábitos")) +
                        (if (lists.isEmpty()) emptyList() else listOf(Opt("", "Listas", header = true))) +
                        lists.map { Opt("l${it.id}", it.name, it.color) }
                    WKind.TASKS -> listOf(Opt("today", "Hoy y vencidas"), Opt("all", "Todas las pendientes")) +
                        (if (lists.isEmpty()) emptyList() else listOf(Opt("", "Listas", header = true))) +
                        lists.map { Opt("l${it.id}", it.name, it.color) } +
                        (if (filters.isEmpty()) emptyList() else listOf(Opt("", "Filtros", header = true))) +
                        filters.map { Opt("f${it.id}", it.name, it.color) }
                    WKind.REPORT -> habits.map { Opt("${it.id}", it.name, it.color) }
                    WKind.JOURNAL -> listOf(Opt("", "Sin etiqueta (todas las notas)")) + habits.map { Opt("${it.id}", it.name, it.color) }
                }

                Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().background(MaterialTheme.colorScheme.background)) {
                    Text(
                        when (kind) {
                            WKind.HABITS -> "¿Qué hábitos mostrar?"
                            WKind.TASKS -> "¿Qué tareas mostrar?"
                            WKind.REPORT -> "Elige un hábito"
                            WKind.JOURNAL -> "Etiqueta para las notas nuevas"
                        },
                        fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(20.dp),
                    )
                    if (kind == WKind.REPORT) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Mostrar historial", Modifier.weight(1f))
                            Switch(history, { history = it })
                        }
                        if (options.isEmpty()) Text("Primero crea un hábito en Rutina.", Modifier.padding(20.dp))
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(options) { o ->
                            if (o.header) {
                                Text(o.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp, 14.dp, 20.dp, 4.dp))
                            } else {
                                Row(
                                    Modifier.fillMaxWidth().clickable { picked = o.value }.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = picked == o.value, onClick = { picked = o.value })
                                    if (o.color != null) {
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(o.color)))
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(o.label)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                    Button(
                        onClick = { save(wid, kind, key, picked, history) },
                        enabled = kind != WKind.REPORT || picked.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD6D6D6), contentColor = Color.Black),
                    ) { Text("Listo", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    private fun save(wid: Int, kind: WKind, key: String, value: String, history: Boolean) {
        val values = buildMap {
            put(key, value)
            if (kind == WKind.REPORT) put("history", if (history) "1" else "0")
        }
        WidgetConfig.set(this, wid, values)
        lifecycleScope.launch {
            runCatching {
                val gid = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(wid)
                when (kind) {
                    WKind.HABITS -> HabitsWidget().update(this@WidgetConfigActivity, gid)
                    WKind.TASKS -> TasksWidget().update(this@WidgetConfigActivity, gid)
                    WKind.REPORT -> HabitReportWidget().update(this@WidgetConfigActivity, gid)
                    WKind.JOURNAL -> JournalWidget().update(this@WidgetConfigActivity, gid)
                }
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
            finish()
        }
    }
}
