package com.rutina.app.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colores de ejemplo
private val Pink = Color(0xFFF28B95)
private val Sky = Color(0xFF8EC5F0)
private val Sun = Color(0xFFF6D98E)
private val Teal = Color(0xFF8FD3C1)
private val Lav = Color(0xFFB39DDB)
private val Indigo = Color(0xFF9FA8DA)
private val Mint = Color(0xFFA5D6A7)
private val Card = Color(0xFF1E1E1E)
private val Line = Color(0xFF2E2E2E)
private val Dim = Color(0xFFAAAAAA)
private val Light = Color(0xFFD6D6D6)

/** Datos de un hábito de ejemplo. */
private data class MH(
    val name: String,
    val color: Color,
    val streak: String,
    val label: String,
    val fraction: Float,
    val done: Boolean = false,
    val icon: ImageVector = Icons.Filled.Add,
)

private val baseHabits = listOf(
    MH("Leer", Pink, "6", "15 / 30 min", 0.5f),
    MH("Beber agua", Sky, "5", "4 / 8 vasos", 0.5f),
    MH("Madrugar", Sun, "5", "1 / 1 vez", 1f, done = true),
    MH("Correr", Teal, "1", "10 / 30 min", 0.33f),
    MH("Hacer la cama", Lav, "10", "1 / 1 vez", 1f, done = true),
)

@Composable
fun MockScreen(mock: Mock) {
    Box(Modifier.fillMaxSize()) {
        when (mock) {
            Mock.HOME -> Home()
            Mock.HOME_FAB -> { Home(); Scrim(); FabMenu() }
            Mock.HABITS -> Habits(baseHabits)
            Mock.HABITS_DIALOG -> { Habits(baseHabits); Scrim(); HabitDialog() }
            Mock.LIMIT -> Habits(listOf(MH("Limitar cafeína", Color(0xFFBCAAA4), "43", "1 taza (Límite: < 4)", 0.25f)) + baseHabits.take(4))
            Mock.QUIT -> Habits(listOf(MH("Dejar de fumar", Color(0xFF90A4AE), "12", "12 días, 3 h", 0f, icon = Icons.Filled.Refresh)) + baseHabits.take(4))
            Mock.MOOD -> Habits(listOf(MH("Estado de ánimo", Teal, "", "🙂 Bien", 0.8f, icon = Icons.Filled.SentimentSatisfied)) + baseHabits.take(4))
            Mock.WEIGHT -> Habits(listOf(MH("Peso corporal", Indigo, "", "72.1 kg (-0.6)", 0f, icon = Icons.Filled.MonitorWeight)) + baseHabits.take(4))
            Mock.HEALTH -> Habits(listOf(MH("Pasos", Teal, "4", "6 250 / 10 000 pasos", 0.62f, icon = Icons.Filled.Sync)) + baseHabits.take(4))
            Mock.JOURNAL_HABIT -> Habits(listOf(MH("Diario de gratitud", Lav, "0", "0 / 50 palabras", 0f, icon = Icons.Filled.EditNote)) + baseHabits.take(4))
            Mock.HABIT_WEEK -> HabitWeek()
            Mock.HABIT_FORM -> HabitForm(goal = false)
            Mock.HABIT_GOAL -> HabitForm(goal = true)
            Mock.HABIT_REMINDERS -> HabitReminders()
            Mock.SUGGESTIONS -> Suggestions()
            Mock.HABIT_STATS -> HabitStats()
            Mock.STATS_OVERVIEW -> StatsOverview()
            Mock.DRAWER -> { Home(); Scrim(); Drawer() }
            Mock.TASKS -> Tasks()
            Mock.TASK_SHEET -> { Home(); Scrim(); TaskSheet() }
            Mock.TASK_FORM -> TaskForm()
            Mock.LISTS -> Lists()
            Mock.JOURNAL -> Journal()
            Mock.NOTE -> Note()
            Mock.TIMER -> Timer(countdown = false)
            Mock.COUNTDOWN -> Timer(countdown = true)
            Mock.CALENDAR -> Calendar()
            Mock.CAL_MONTH -> CalMonth()
            Mock.CAL_SCHEDULE -> { Calendar(); Scrim(); ScheduleSheet() }
            Mock.SETTINGS -> Settings()
            Mock.BACKUP -> { Settings(); Scrim(); BackupDialog() }
            Mock.ACHIEVEMENTS -> Achievements()
            Mock.ARCHIVE -> Archive()
            Mock.WIDGET_HABITS -> HomeScreen { WidgetHabits() }
            Mock.WIDGET_TASKS -> HomeScreen { WidgetTasks() }
        }
    }
}

// ---------------------------------------------------------------- piezas

@Composable
private fun T(
    text: String,
    size: TextUnit = 10.sp,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    strike: Boolean = false,
) = Text(
    text, modifier = modifier, fontSize = size, color = color, fontWeight = weight, maxLines = 1,
    overflow = TextOverflow.Ellipsis, lineHeight = size * 1.2f,
    textDecoration = if (strike) TextDecoration.LineThrough else null,
)

@Composable
private fun Ico(icon: ImageVector, tint: Color = Color.White, size: Dp = 15.dp) =
    Icon(icon, null, Modifier.size(size), tint = tint)

@Composable
private fun TopBar(
    title: String,
    left: List<ImageVector> = emptyList(),
    right: List<ImageVector> = emptyList(),
    bg: Color = Color.Black,
    fg: Color = Color.White,
) {
    Box(Modifier.fillMaxWidth().height(38.dp).background(bg).padding(horizontal = 8.dp)) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            left.forEach { Ico(it, fg, 16.dp) }
        }
        T(title, 12.sp, fg, FontWeight.Bold, Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            right.forEach { Ico(it, fg, 16.dp) }
        }
    }
}

@Composable
private fun WeekStrip() {
    val names = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    Row(
        Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        names.forEachIndexed { i, n ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(Color(0xFF333333))
                    .then(if (i == 1) Modifier.border(1.dp, Color(0xFF888888), RoundedCornerShape(5.dp)) else Modifier)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(n, 7.sp, if (i == 1) Teal else Dim)
                T("${14 + i}", 12.sp)
            }
        }
    }
}

@Composable
private fun SectionHead(title: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        T(title, 9.sp, Dim, modifier = Modifier.weight(1f))
        T(count, 9.sp, Dim)
    }
}

@Composable
private fun HabitRow(h: MH) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp).height(34.dp)
            .clip(RoundedCornerShape(7.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(7.dp)),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(h.fraction.coerceIn(0f, 1f)).background(Color.Black))
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(5.dp)).background(h.color).padding(horizontal = 6.dp, vertical = 4.dp)) {
                T(h.name, 9.sp, Color.Black, strike = h.done, modifier = Modifier.width(IntrinsicWidth(h.name)))
            }
            if (h.streak.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF444444), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(h.streak, 8.sp)
                    Ico(Icons.Filled.LocalFireDepartment, if (h.done) Color(0xFFFF8A50) else Color.Gray, 8.dp)
                }
            }
            Spacer(Modifier.weight(1f))
            T(h.label, 8.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
                    .background(if (h.done) h.color else Color.Transparent)
                    .border(1.5.dp, if (h.done) h.color else Color(0xFF555555), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) { Ico(if (h.done) Icons.Filled.Check else h.icon, if (h.done) Color.Black else Color.White, 13.dp) }
        }
    }
}

/** Ancho aproximado para que el nombre no se corte demasiado. */
private fun IntrinsicWidth(name: String): Dp = (name.length * 5 + 4).coerceAtMost(86).dp

@Composable
private fun TaskRow(title: String, due: String, color: Color = Indigo, done: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp).height(30.dp)
            .clip(RoundedCornerShape(7.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(color), contentAlignment = Alignment.Center) {
            Ico(Icons.Filled.TaskAlt, Color.Black, 9.dp)
        }
        Spacer(Modifier.width(6.dp))
        T(title, 9.sp, if (done) Dim else Color.White, modifier = Modifier.weight(1f), strike = done)
        T(due, 8.sp, if (due == "Ayer") Color(0xFFE57373) else Dim)
        Spacer(Modifier.width(5.dp))
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).border(1.5.dp, Color(0xFF555555), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { if (done) Ico(Icons.Filled.Check, Color.White, 12.dp) }
    }
}

@Composable
private fun BoxScope.Fab(icon: ImageVector = Icons.Filled.Add) {
    Box(
        Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 40.dp).size(38.dp).clip(CircleShape).background(Light),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, Color.Black, 18.dp) }
}

@Composable
private fun BoxScope.Scrim() = Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))

@Composable
private fun BoxScope.Dialog(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.align(Alignment.Center).padding(horizontal = 14.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF2B2B2B)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun Field(label: String, value: String = "") {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).border(1.dp, Color(0xFF666666), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        T(label, 7.sp, Dim)
        if (value.isNotEmpty()) T(value, 10.sp)
    }
}

@Composable
private fun Pill(text: String, bg: Color = Color(0xFF2A2A2A), fg: Color = Color.White) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(text, 8.sp, fg)
    }
}

@Composable
private fun FormRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 9.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Pill("$value ▾")
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line))
}

@Composable
private fun SectionBand(title: String) {
    Box(Modifier.fillMaxWidth().background(Color(0xFF2A2A2A)).padding(horizontal = 9.dp, vertical = 5.dp)) { T(title, 9.sp, Dim) }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(7.dp)).background(Card).padding(6.dp)) {
        T(label, 7.sp, Dim)
        T(value, 12.sp, weight = FontWeight.Bold)
    }
}

@Composable
private fun Bars(values: List<Float>, color: Color, height: Dp = 60.dp) {
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { v ->
            Box(Modifier.weight(1f).fillMaxHeight(v.coerceIn(0.05f, 1f)).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)).background(color))
        }
    }
}

// ---------------------------------------------------------------- Hoy / hábitos

@Composable
private fun BoxScope.Home() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Hoy", listOf(Icons.Filled.Menu, Icons.Filled.BarChart), listOf(Icons.Filled.Timer))
        WeekStrip()
        SectionHead("Lista de pendientes", "2")
        TaskRow("Leer el primer capítulo", "Hoy")
        TaskRow("Pagar el internet", "Hoy", Pink)
        SectionHead("Lista de hábitos", "3")
        HabitRow(baseHabits[0]); HabitRow(MH("Paseo matutino", Sun, "29", "5 000 / 5 000 pasos", 1f, done = true)); HabitRow(MH("Meditar", Teal, "4", "5 / 10 min", 0.5f))
    }
    Fab()
}

@Composable
private fun BoxScope.FabMenu() {
    Column(
        Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            Triple("Tarea", "Crea una lista de pendientes", Icons.Filled.TaskAlt),
            Triple("Hábito", "Registra una actividad recurrente", Icons.Filled.Spa),
        ).forEach { (t, s, i) ->
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column { T(t, 10.sp, weight = FontWeight.Bold); T(s, 8.sp, Color(0xFFDDDDDD)) }
                Spacer(Modifier.width(8.dp)); Ico(i)
            }
        }
        Box(Modifier.size(38.dp).clip(CircleShape).background(Light), contentAlignment = Alignment.Center) { Ico(Icons.Filled.Close, Color.Black, 18.dp) }
    }
}

@Composable
private fun BoxScope.Habits(list: List<MH>) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Hábitos", listOf(Icons.Filled.Menu, Icons.Filled.BarChart), listOf(Icons.Filled.Timer))
        WeekStrip()
        Spacer(Modifier.height(4.dp))
        list.forEach { HabitRow(it) }
    }
    Fab()
}

@Composable
private fun BoxScope.HabitDialog() = Dialog {
    T("Leer", 13.sp, weight = FontWeight.Bold)
    T("Progreso: 15 / 30 min", 10.sp)
    Field("Cantidad (min)", "15")
    Field("Nota (opcional)", "Capítulo 3")
    T("Deshacer último registro", 9.sp, Indigo)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { T("Estadísticas", 9.sp, Indigo); T("Editar hábito", 9.sp, Indigo) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        T("Cerrar", 10.sp, Indigo); Spacer(Modifier.width(14.dp)); T("Añadir", 10.sp, Indigo, FontWeight.Bold)
    }
}

@Composable
private fun HabitWeek() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Hábitos", listOf(Icons.Filled.Menu, Icons.Filled.BarChart), listOf(Icons.Filled.Timer))
        WeekStrip()
        Row(Modifier.fillMaxWidth().padding(start = 82.dp, end = 6.dp, top = 6.dp)) {
            listOf("D", "L", "M", "M", "J", "V", "S").forEach { T(it, 8.sp, Dim, modifier = Modifier.weight(1f)) }
        }
        val pattern = listOf(
            listOf(1f, 1f, 0.5f, 1f, 0f, 1f, 0f), listOf(1f, 0.5f, 1f, 1f, 1f, 0f, 0f),
            listOf(1f, 1f, 1f, 1f, 1f, 1f, 0f), listOf(0f, 1f, 0f, 1f, 0.3f, 0f, 0f), listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f),
        )
        baseHabits.forEachIndexed { i, h ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(72.dp).clip(RoundedCornerShape(5.dp)).background(h.color).padding(horizontal = 5.dp, vertical = 4.dp)) {
                    T(h.name, 8.sp, Color.Black)
                }
                Spacer(Modifier.width(4.dp))
                pattern[i].forEach { v ->
                    Box(Modifier.weight(1f).padding(1.5.dp).aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2A2A2A))) {
                        if (v > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(v).align(Alignment.BottomCenter).background(h.color))
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitForm(goal: Boolean) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Leer", listOf(Icons.Filled.Close), listOf(Icons.Filled.Check), bg = Pink, fg = Color.Black)
        Column(Modifier.padding(9.dp)) { Field("Nombre", "Leer") }
        if (!goal) {
            T("Tipo de seguimiento", 9.sp, Dim, modifier = Modifier.padding(horizontal = 9.dp))
            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Recuento" to "Con qué frecuencia completas una actividad",
                    "Dejar" to "Tiempo desde que dejaste un mal hábito",
                    "Estado de ánimo" to "Cómo te sientes cada día",
                    "Peso corporal" to "Tu peso y tu objetivo",
                ).forEachIndexed { i, (a, b) ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(if (i == 0) Color(0xFF3A3A3A) else Card)
                            .then(if (i == 0) Modifier.border(1.dp, Pink, RoundedCornerShape(6.dp)) else Modifier)
                            .padding(6.dp),
                    ) { T(a, 9.sp, weight = FontWeight.Bold); T(b, 7.sp, Dim) }
                }
            }
        }
        FormRow("Objetivo", "👍 Positivo")
        FormRow("Meta", "30 min")
        FormRow("Periodo", "Por día")
        FormRow("Días", "Todos los días")
        if (goal) {
            Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { i, d ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(if (i < 5) Pink else Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center,
                    ) { T(d, 9.sp, if (i < 5) Color.Black else Color.White) }
                }
            }
            FormRow("Unidad", "min")
        }
        Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(Pink, Sky, Sun, Teal, Lav, Indigo, Mint).forEach { c ->
                Box(Modifier.size(18.dp).clip(CircleShape).background(c).then(if (c == Pink) Modifier.border(2.dp, Color.White, CircleShape) else Modifier))
            }
        }
    }
}

@Composable
private fun HabitReminders() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Leer", listOf(Icons.Filled.Close), listOf(Icons.Filled.Check), bg = Pink, fg = Color.Black)
        FormRow("Meta", "30 min")
        FormRow("Días", "Todos los días")
        SectionBand("Recordatorios")
        listOf("07:00" to "Todos los días", "21:30" to "L M M J V").forEach { (h, d) ->
            Row(Modifier.fillMaxWidth().padding(9.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(h, 13.sp, weight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); T(d, 9.sp, Dim, modifier = Modifier.weight(1f))
                Ico(Icons.Filled.Close, Dim, 13.dp)
            }
        }
        T("+ Añadir recordatorio", 10.sp, Pink, FontWeight.Bold, Modifier.padding(9.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(Color(0xFF2A2A2A)).padding(8.dp), contentAlignment = Alignment.Center) { T("Archivar", 10.sp) }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(Color(0xFF2A2A2A)).padding(8.dp), contentAlignment = Alignment.Center) { T("Eliminar", 10.sp, Color(0xFFE57373)) }
        }
        Box(Modifier.fillMaxWidth().padding(9.dp).clip(RoundedCornerShape(7.dp)).background(Light).padding(9.dp), contentAlignment = Alignment.Center) {
            T("Guardar hábito", 10.sp, Color.Black, FontWeight.Bold)
        }
    }
}

@Composable
private fun Suggestions() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Nuevo hábito", listOf(Icons.Filled.Close))
        Box(Modifier.fillMaxWidth().padding(9.dp).clip(RoundedCornerShape(7.dp)).background(Light).padding(9.dp), contentAlignment = Alignment.Center) {
            T("Hábito personalizado  ›", 10.sp, Color.Black, FontWeight.Bold)
        }
        T("Prueba una sugerencia…", 9.sp, Dim, modifier = Modifier.padding(horizontal = 9.dp))
        listOf(
            "Fitness" to listOf("Caminar" to Mint, "Correr" to Teal, "Estirar" to Lav),
            "Salud" to listOf("Beber agua" to Sky, "Dormir 8 h" to Indigo, "Vitaminas" to Sun),
            "Mente" to listOf("Leer" to Pink, "Meditar" to Teal, "Diario" to Lav),
            "Reducir" to listOf("Cafeína" to Color(0xFFBCAAA4), "Pantalla" to Sky),
            "Dejar" to listOf("Fumar" to Color(0xFF90A4AE), "Azúcar" to Pink),
        ).forEach { (g, items) ->
            T(g, 10.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp, top = 8.dp, bottom = 4.dp))
            Row(Modifier.padding(horizontal = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items.forEach { (n, c) -> Pill(n, c, Color.Black) }
            }
        }
    }
}

@Composable
private fun HabitStats() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Leer", listOf(Icons.AutoMirrored.Filled.ArrowBack), listOf(Icons.Filled.Edit), bg = Pink, fg = Color.Black)
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Tile("Racha", "6 días", Modifier.weight(1f)); Tile("Mejor", "12 días", Modifier.weight(1f)); Tile("Éxito", "80 %", Modifier.weight(1f))
        }
        T("Septiembre 2026", 10.sp, weight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp))
        Column(Modifier.padding(8.dp)) {
            repeat(5) { w ->
                Row {
                    repeat(7) { d ->
                        val n = w * 7 + d
                        val c = when {
                            n > 17 -> Color(0xFF242424)
                            n % 5 == 3 -> Pink.copy(alpha = 0.4f)
                            n % 7 == 6 -> Color(0xFF3A3A3A)
                            else -> Pink
                        }
                        Box(Modifier.weight(1f).padding(1.5.dp).aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                }
            }
        }
        T("Minutos por semana", 9.sp, Dim, modifier = Modifier.padding(horizontal = 9.dp))
        Bars(listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 1f, 0.8f), Pink)
    }
}

@Composable
private fun StatsOverview() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Estadísticas", listOf(Icons.AutoMirrored.Filled.ArrowBack))
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Tile("Hábitos hoy", "3 / 5", Modifier.weight(1f)); Tile("Tareas hoy", "4", Modifier.weight(1f)); Tile("Semana", "17", Modifier.weight(1f))
        }
        T("% de hábitos cumplidos", 9.sp, Dim, modifier = Modifier.padding(horizontal = 9.dp))
        Bars(listOf(0.6f, 0.8f, 1f, 0.4f, 0.8f, 0.6f, 1f, 0.8f, 0.6f, 1f), Indigo)
        Spacer(Modifier.height(8.dp))
        baseHabits.take(4).forEachIndexed { i, h ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(80.dp).clip(RoundedCornerShape(5.dp)).background(h.color).padding(5.dp)) { T(h.name, 8.sp, Color.Black) }
                Spacer(Modifier.weight(1f))
                T("${listOf(86, 72, 95, 40)[i]} %", 10.sp, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BoxScope.Drawer() {
    Column(
        Modifier.align(Alignment.CenterStart).fillMaxHeight().width(190.dp).background(Color(0xFF181818)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        T("Listas / Filtros", 9.sp, Dim, modifier = Modifier.padding(4.dp))
        @Composable
        fun item(t: String, c: Color, n: String = "", sel: Boolean = false) = Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (sel) Color(0xFF2A2A2A) else Color.Transparent).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(c))
            Spacer(Modifier.width(7.dp)); T(t, 10.sp, modifier = Modifier.weight(1f)); T(n, 9.sp, Dim)
        }
        item("Vista de hoy", Indigo, "2", true); item("Todas las tareas", Indigo, "7"); item("Todos los hábitos", Indigo, "5")
        T("Mis listas", 8.sp, Dim, modifier = Modifier.padding(4.dp)); item("Casa", Mint); item("Trabajo", Sky)
        T("Filtros", 8.sp, Dim, modifier = Modifier.padding(4.dp)); item("Urgentes", Pink)
        T("Visualización de hábitos", 8.sp, Dim, modifier = Modifier.padding(4.dp))
        item("Compacto", Lav, sel = false); item("Semanal", Lav); item("Mensual", Lav); item("Anual", Lav)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).border(1.dp, Color(0xFF444444), RoundedCornerShape(7.dp)).padding(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Ico(Icons.Filled.Search, size = 12.dp); Spacer(Modifier.width(5.dp)); T("Buscar…", 9.sp, Dim) }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Light).padding(7.dp), contentAlignment = Alignment.Center) {
            T("Gestionar listas / filtros", 9.sp, Color.Black)
        }
        Spacer(Modifier.height(30.dp))
    }
}

// ---------------------------------------------------------------- Tareas

@Composable
private fun BoxScope.Tasks() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Todas las tareas", listOf(Icons.Filled.Menu, Icons.Filled.BarChart), listOf(Icons.Filled.Timer))
        SectionHead("Vencidas", "1"); TaskRow("Pagar la luz", "Ayer", Pink)
        SectionHead("Hoy", "2"); TaskRow("Comprar comida", "19:00", Mint); TaskRow("Ordenar correo", "20:00")
        SectionHead("Mañana", "1"); TaskRow("Cita con el dentista", "10:00", Sky)
        SectionHead("Sin fecha", "1"); TaskRow("Lavar el coche", "", Mint)
        SectionHead("Completadas", "1"); TaskRow("Regar plantas", "Hoy", Lav, done = true)
    }
    Fab()
}

@Composable
private fun BoxScope.TaskSheet() {
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 26.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(Color(0xFF242424)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(30.dp).height(3.dp).clip(CircleShape).background(Color.Gray))
        Field("¿Qué quieres hacer?", "Comprar comida")
        Field("Descripción (opcional)")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Hoy ▾"); Ico(Icons.Filled.Flag, Pink, 14.dp); Pill("Casa ▾")
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(30.dp).clip(CircleShape).background(Light), contentAlignment = Alignment.Center) { Ico(Icons.Filled.Check, Color.Black) }
        }
        T("Más opciones (hora, repetir, aviso)…", 9.sp, Indigo)
    }
}

@Composable
private fun TaskForm() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Tarea", listOf(Icons.Filled.Close), listOf(Icons.Filled.Check))
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Field("¿Qué quieres hacer?", "Comprar comida")
            Field("Descripción (opcional)", "Leche, pan y fruta")
        }
        FormRow("Fecha", "Hoy")
        FormRow("Hora", "19:00")
        FormRow("Duración", "30 min")
        FormRow("Repetir", "Cada semana")
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Prioridad", 9.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            listOf(Color.Gray, Sky, Sun, Pink).forEachIndexed { i, c ->
                Box(
                    Modifier.padding(start = 4.dp).size(22.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (i == 3) Color(0xFF3A3A3A) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) { Ico(Icons.Filled.Flag, c, 13.dp) }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line))
        FormRow("Lista", "Casa")
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Aviso", 9.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.width(30.dp).height(16.dp).clip(CircleShape).background(Indigo)) {
                Box(Modifier.align(Alignment.CenterEnd).padding(2.dp).size(12.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun Lists() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Listas / Filtros", listOf(Icons.AutoMirrored.Filled.ArrowBack))
        SectionBand("Listas")
        listOf("Casa" to Mint, "Trabajo" to Sky, "Compras" to Sun).forEach { (n, c) ->
            Row(Modifier.fillMaxWidth().padding(9.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Icons.Filled.FormatListBulleted, c); Spacer(Modifier.width(8.dp)); T(n, 10.sp)
            }
        }
        T("+ Nueva lista", 10.sp, Indigo, FontWeight.Bold, Modifier.padding(9.dp))
        SectionBand("Filtros")
        listOf("Urgentes · prioridad alta" to Pink, "Esta semana · 7 días" to Lav).forEach { (n, c) ->
            Row(Modifier.fillMaxWidth().padding(9.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(c)); Spacer(Modifier.width(8.dp)); T(n, 10.sp)
            }
        }
        T("+ Nuevo filtro", 10.sp, Indigo, FontWeight.Bold, Modifier.padding(9.dp))
    }
}

// ---------------------------------------------------------------- Diario

@Composable
private fun BoxScope.Journal() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Diario", listOf(Icons.Filled.BookmarkBorder), listOf(Icons.Filled.MoreHoriz))
        Row(Modifier.fillMaxWidth().padding(7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Todas las entradas ▾"); Pill("Etiqueta ▾"); Spacer(Modifier.weight(1f)); Ico(Icons.Filled.Search)
        }
        @Composable
        fun entry(time: String, chip: @Composable RowScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) = Column(
            Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Card).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { chip(); Spacer(Modifier.weight(1f)); T(time, 8.sp, Dim) }
            body()
        }
        entry("22:10", { Pill("Diario de gratitud", Lav, Color.Black); Spacer(Modifier.width(4.dp)); Pill("42 palabras") }) {
            T("Hoy agradezco", 11.sp, weight = FontWeight.Bold); T("• Mi familia", 9.sp); T("☑ Salir a caminar", 9.sp)
        }
        entry("20:05", { Pill("Leer", Pink, Color.Black) }) { T("Leer · 15 min", 9.sp); T("Capítulo 3", 9.sp, Dim) }
        entry("19:40", { Pill("Tarea", Mint, Color.Black) }) { T("✓ Comprar comida", 9.sp) }
        entry("08:00", { Pill("Nota") ; Spacer(Modifier.width(4.dp)); Ico(Icons.Filled.Bookmark, Indigo, 12.dp) }) {
            T("Ideas para el fin de semana", 9.sp)
        }
    }
    Fab()
}

@Composable
private fun Note() {
    Column(Modifier.fillMaxSize()) {
        TopBar("", listOf(Icons.Filled.Close), listOf(Icons.Filled.Delete, Icons.Filled.Check))
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Diario de gratitud ▾", Lav, Color.Black); Pill("15 sep, 22:10"); Spacer(Modifier.weight(1f)); Ico(Icons.Filled.Bookmark, Indigo)
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF222222)).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(Icons.Filled.FormatBold, Icons.Filled.FormatItalic, Icons.Filled.FormatStrikethrough, Icons.Filled.Highlight, Icons.Filled.Title, Icons.Filled.FormatListBulleted, Icons.Filled.CheckBox)
                .forEach { Ico(it, size = 15.dp) }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            T("Hoy agradezco", 15.sp, weight = FontWeight.Bold)
            Row { T("Tres cosas ", 10.sp); T("importantes", 10.sp, weight = FontWeight.Bold); T(" de mi día:", 10.sp) }
            T("• Mi familia", 10.sp)
            T("• Una buena plática", 10.sp)
            Row(verticalAlignment = Alignment.CenterVertically) { Ico(Icons.Filled.CheckBox, Mint, 12.dp); Spacer(Modifier.width(4.dp)); T("Salir a caminar", 10.sp, strike = true) }
            Box(Modifier.background(Sun.copy(alpha = 0.5f)).padding(horizontal = 2.dp)) { T("Mañana: llamar a mamá", 10.sp) }
        }
        Spacer(Modifier.weight(1f))
        T("42 palabras", 9.sp, Dim, modifier = Modifier.padding(10.dp).padding(bottom = 30.dp))
    }
}

// ---------------------------------------------------------------- Temporizador

@Composable
private fun Timer(countdown: Boolean) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("Temporizador", listOf(Icons.Filled.Close))
        Row(Modifier.fillMaxWidth().background(Color.Black)) {
            listOf("Cronómetro", "Cuenta atrás").forEachIndexed { i, t ->
                val sel = (i == 1) == countdown
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(t, 10.sp, if (sel) Color.White else Dim, if (sel) FontWeight.Bold else FontWeight.Normal, Modifier.padding(6.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(2.dp).background(if (sel) Color.White else Color.Transparent))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Concentración", 10.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Pill("Leer ▾", Pink, Color.Black)
        }
        if (!countdown) {
            Box(Modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Light).padding(horizontal = 16.dp, vertical = 8.dp)) {
                T("02:01.25", 24.sp, Color.Black)
            }
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(80.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF2A2A2A)).padding(8.dp), contentAlignment = Alignment.Center) { T("◉  Vuelta", 10.sp) }
                Box(Modifier.width(80.dp).clip(RoundedCornerShape(7.dp)).background(Light).padding(8.dp), contentAlignment = Alignment.Center) { T("❚❚  Pausa", 10.sp, Color.Black) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                T("Vueltas", 10.sp, Dim); T("Eventos", 10.sp, weight = FontWeight.Bold)
            }
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { T("15 sep 2026 22:00", 9.sp, modifier = Modifier.weight(1f)); T("Iniciar ▶", 9.sp, weight = FontWeight.Bold) }
                Row { T("15 sep 2026 22:01", 9.sp, modifier = Modifier.weight(1f)); T("Vuelta ◉", 9.sp, weight = FontWeight.Bold) }
            }
        } else {
            Box(Modifier.padding(10.dp).size(130.dp).clip(CircleShape).border(8.dp, Sun, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { T("25:00", 22.sp); T("restante", 8.sp, Dim) }
            }
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("5", "10", "15", "25", "45").forEach { Pill("$it min", if (it == "25") Sun else Color(0xFF2A2A2A), if (it == "25") Color.Black else Color.White) }
            }
            Box(Modifier.padding(10.dp).width(150.dp).clip(RoundedCornerShape(8.dp)).background(Light).padding(9.dp), contentAlignment = Alignment.Center) {
                T("▶  Iniciar", 11.sp, Color.Black, FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------- Calendario

@Composable
private fun BoxScope.Calendar() {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(38.dp).background(Color.Black).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Calendario", 12.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Pill("Día ▾"); Spacer(Modifier.width(8.dp)); Ico(Icons.Filled.MoreHoriz)
        }
        Row(Modifier.fillMaxWidth().background(Color.Black).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Mar, 15 sep 2026"); Spacer(Modifier.weight(1f)); Pill("‹"); Pill("Hoy", Light, Color.Black); Pill("›")
        }
        Row(Modifier.fillMaxWidth().padding(start = 34.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Lav).padding(3.dp), contentAlignment = Alignment.Center) { T("Regar plantas", 8.sp, Color.Black) }
        }
        val hour = 34.dp
        Box(Modifier.fillMaxWidth()) {
            Column {
                (17..23).forEach { h ->
                    Row(Modifier.height(hour)) {
                        T("$h:00", 7.sp, Dim, modifier = Modifier.width(32.dp).padding(start = 3.dp))
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line))
                    }
                }
            }
            @Composable
            fun ev(start: Float, len: Float, t: String, c: Color) = Box(
                Modifier.padding(start = 34.dp, end = 4.dp, top = hour * start + 1.dp).fillMaxWidth().height(hour * len - 2.dp)
                    .clip(RoundedCornerShape(4.dp)).background(c),
                contentAlignment = Alignment.Center,
            ) { T(t, 8.sp, Color.Black, FontWeight.Bold) }
            ev(0f, 1f, "Paseo diario", Mint)
            ev(2f, 0.6f, "Comprar comida", Lav)
            ev(3f, 0.6f, "Ordenar correo", Indigo)
            ev(4f, 0.8f, "⏱ Leer · 25 min", Color(0xFF90A4AE))
            Box(Modifier.padding(start = 30.dp, top = hour * 5f).fillMaxWidth().height(1.5.dp).background(Color.White))
        }
    }
    Fab()
}

@Composable
private fun CalMonth() {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(38.dp).background(Color.Black).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Calendario", 12.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Pill("Mes ▾"); Spacer(Modifier.width(8.dp)); Ico(Icons.Filled.MoreHoriz)
        }
        Row(Modifier.fillMaxWidth().padding(4.dp)) { listOf("D", "L", "M", "M", "J", "V", "S").forEach { T(it, 8.sp, Dim, modifier = Modifier.weight(1f).padding(start = 3.dp)) } }
        repeat(5) { w ->
            Row(Modifier.fillMaxWidth().height(58.dp)) {
                repeat(7) { d ->
                    val n = w * 7 + d - 1
                    Column(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Line).padding(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T(if (n in 1..30) "$n" else "", 8.sp, if (n == 15) Teal else Color.White)
                        if (n in 1..30) {
                            if (n % 3 == 0) Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)).background(Mint))
                            if (n % 7 == 2) Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)).background(Lav.copy(alpha = if (n > 15) 0.4f else 1f)))
                            if (n % 4 == 1) Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)).background(Indigo))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ScheduleSheet() {
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 26.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(Color(0xFF242424)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        T("Programar tareas", 12.sp, weight = FontWeight.Bold)
        listOf("Lavar el coche" to "Sin fecha", "Llamar al banco" to "Hoy", "Revisar presupuesto" to "Vie 18").forEach { (t, d) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { T(t, 10.sp); T(d, 8.sp, Dim) }
                Pill("Programar", Lav, Color.Black)
            }
        }
    }
}

// ---------------------------------------------------------------- Ajustes

@Composable
private fun Settings() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Ajustes")
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF222222)).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF333333)), contentAlignment = Alignment.Center) { Ico(Icons.Filled.Lock, Indigo) }
                T("Tus datos son solo tuyos", 10.sp, Indigo)
                T("Último respaldo: hoy", 8.sp)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).border(1.dp, Color(0xFF555555), RoundedCornerShape(7.dp)).padding(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Ico(Icons.Filled.Search, size = 12.dp); Spacer(Modifier.width(5.dp)); T("Busca tareas, hábitos…", 9.sp, Dim) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(Light).padding(8.dp), horizontalArrangement = Arrangement.Center) {
                    Ico(Icons.Filled.School, Color.Black, 12.dp); Spacer(Modifier.width(4.dp)); T("Tutorial", 9.sp, Color.Black)
                }
                Row(Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(Color(0xFF2A2A2A)).padding(8.dp), horizontalArrangement = Arrangement.Center) {
                    Ico(Icons.Filled.EmojiEvents, size = 12.dp); Spacer(Modifier.width(4.dp)); T("Logros", 9.sp)
                }
            }
        }
        SectionBand("Apariencia")
        FormRow("Tamaño del texto", "Normal")
        FormRow("Inicio de semana", "Domingo")
        FormRow("Abrir la app en", "Hoy")
        FormRow("Vista de hábitos", "Compacto")
        SectionBand("Respaldo y datos")
        listOf("Crear respaldo", "Restaurar respaldo", "Eliminar datos").forEach {
            Row(Modifier.fillMaxWidth().padding(9.dp, 7.dp)) {
                T(it, 9.sp, if (it == "Eliminar datos") Color(0xFFE57373) else Color.White, FontWeight.Bold, Modifier.weight(1f)); T("›", 10.sp, Dim)
            }
        }
    }
}

@Composable
private fun BoxScope.BackupDialog() = Dialog {
    T("Crear respaldo", 13.sp, weight = FontWeight.Bold)
    Text(
        "Se guarda un archivo con todos tus hábitos, registros, tareas, notas y ajustes.",
        fontSize = 9.sp, color = Color.White, lineHeight = 11.sp,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        T("Proteger con contraseña", 9.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Box(Modifier.width(30.dp).height(16.dp).clip(CircleShape).background(Indigo)) {
            Box(Modifier.align(Alignment.CenterEnd).padding(2.dp).size(12.dp).clip(CircleShape).background(Color.White))
        }
    }
    Field("Contraseña", "••••••••")
    Field("Repite la contraseña", "••••••••")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        T("Cancelar", 10.sp, Indigo); Spacer(Modifier.width(14.dp)); T("Elegir dónde guardar", 10.sp, Indigo, FontWeight.Bold)
    }
}

@Composable
private fun Achievements() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Logros", listOf(Icons.AutoMirrored.Filled.ArrowBack))
        Column(
            Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF222222)).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { T("🏅", 22.sp); T("14 insignias", 13.sp, weight = FontWeight.Bold); T("Desde el 3 de marzo de 2026", 8.sp, Dim) }
        listOf(
            Triple("✅", "Registros de hábitos", "312" to 0.25f),
            Triple("☑️", "Tareas completadas", "87" to 0.75f),
            Triple("✍️", "Palabras escritas", "5 430" to 0.1f),
            Triple("⏱", "Horas de concentración", "12 h 30 min" to 0.2f),
        ).forEach { (e, t, v) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Card).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(e, 14.sp); Spacer(Modifier.width(6.dp))
                    Column { T(t, 9.sp); T(v.first, 12.sp, weight = FontWeight.Bold) }
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0xFF3A3A3A))) {
                    Box(Modifier.fillMaxWidth(v.second).fillMaxHeight().background(Indigo))
                }
            }
        }
    }
}

@Composable
private fun Archive() {
    Column(Modifier.fillMaxSize()) {
        TopBar("Archivo de hábitos", listOf(Icons.AutoMirrored.Filled.ArrowBack))
        Spacer(Modifier.height(6.dp))
        listOf("Guitarra" to Lav, "Inglés" to Sky).forEach { (n, c) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Card).padding(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(n, c, Color.Black); Spacer(Modifier.weight(1f)); T("Restaurar", 9.sp, Indigo); Spacer(Modifier.width(8.dp)); T("Eliminar", 9.sp, Color(0xFFE57373))
            }
        }
    }
}

// ---------------------------------------------------------------- Widgets

@Composable
private fun HomeScreen(widget: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF5A5A5A), Color(0xFF2A2A2A)))).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row { T("12:45", 10.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f)); T("▮▮▮ ◔", 8.sp) }
        widget()
        T("Rutina", 8.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        val apps = listOf(Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DD0E1), Color(0xFF90A4AE), Color(0xFFF06292))
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { c -> Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(c)) }
            }
        }
    }
}

@Composable
private fun WidgetHabits() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C1C)).padding(6.dp)) {
        Row(Modifier.padding(4.dp)) { T("Hábitos de hoy", 10.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f)); T("2/5", 9.sp, Dim) }
        baseHabits.forEach { h ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.5.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2A)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(72.dp).fillMaxHeight().background(h.color).padding(horizontal = 5.dp), contentAlignment = Alignment.CenterStart) {
                    T(h.name, 8.sp, Color.Black)
                }
                T(h.label, 8.sp, modifier = Modifier.weight(1f).padding(horizontal = 5.dp))
                Box(Modifier.size(24.dp).background(if (h.done) h.color else Color(0xFF3A3A3A)), contentAlignment = Alignment.Center) {
                    T(if (h.done) "✓" else "+", 11.sp, if (h.done) Color.Black else Color.White, FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WidgetTasks() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C1C)).padding(6.dp)) {
        Row(Modifier.padding(4.dp)) { T("Tareas", 10.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f)); T("4", 9.sp, Dim) }
        listOf(
            Triple("Preparar comidas de la semana", "Hoy", Pink),
            Triple("Pagar la luz", "Ayer", Indigo),
            Triple("Limpiar la bandeja de entrada", "15:00", Teal),
            Triple("Cita con el dentista", "Hoy 10:00", Sky),
        ).forEach { (t, d, c) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.5.dp).height(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2A)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(6.dp)); Box(Modifier.size(6.dp).clip(CircleShape).background(c))
                T(t, 8.sp, modifier = Modifier.weight(1f).padding(horizontal = 5.dp))
                T(d, 7.sp, if (d == "Ayer") Color(0xFFE57373) else Dim)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(26.dp).background(Color(0xFF3A3A3A)), contentAlignment = Alignment.Center) { T("✓", 11.sp, weight = FontWeight.Bold) }
            }
        }
    }
}
