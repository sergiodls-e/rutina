package com.rutina.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.data.Habit
import com.rutina.app.domain.Achievements
import com.rutina.app.domain.Milestone
import com.rutina.app.ui.theme.Accent
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es = Locale("es", "MX")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTop(title: String, onClose: () -> Unit) {
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) } },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
    )
}

// ---------------------------------------------------------------- Archivo de hábitos

@Composable
fun ArchiveScreen(vm: ArchiveViewModel, onClose: () -> Unit) {
    val habits by vm.habits.collectAsState()
    var toDelete by remember { mutableStateOf<Habit?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTop("Archivo de hábitos", onClose) },
    ) { pad ->
        val list = habits
        when {
            list == null -> Box(Modifier.padding(pad).fillMaxSize())
            list.isEmpty() -> Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay hábitos archivados", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        "Al archivar un hábito (desde Editar hábito) se oculta de Hoy pero conserva todo su historial. Aparecerá aquí.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            else -> LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { h ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF222222)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(Color(h.color)).padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text(h.name, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.restore(h) }) { Text("Restaurar") }
                        TextButton(onClick = { toDelete = h }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    toDelete?.let { h ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar \"${h.name}\"?") },
            text = { Text("Se borrarán para siempre el hábito y todo su historial.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(h); toDelete = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------- Logros

@Composable
fun AchievementsScreen(vm: AchievementsViewModel, onClose: () -> Unit) {
    val data by vm.state.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTop("Logros", onClose) },
    ) { pad ->
        val d = data
        if (d == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF222222)).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🏅", fontSize = 40.sp)
                    Text("${d.badges} ${if (d.badges == 1) "insignia" else "insignias"}", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(
                        d.since?.let { "Desde el " + it.format(DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", es)) }
                            ?: "Empieza a registrar para ganar tus primeras insignias",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    )
                }
            }
            item { Header("Totales de actividad") }
            items(d.milestones, key = { it.key }) { MilestoneCard(it) }

            if (d.records.isNotEmpty()) {
                item { Header("Récords") }
                items(d.records, key = { it.title }) { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF222222)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.Medium)
                            Text(r.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(r.value, fontWeight = FontWeight.Bold, color = Accent)
                    }
                }
            }

            if (d.streaks.isNotEmpty()) {
                item { Header("Mejores rachas") }
                items(d.streaks, key = { "s" + it.habit.id }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF222222)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(Color(s.habit.color)).padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(s.habit.name, color = Color.Black, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFFF8A50))
                                Text("${s.best} ${s.unit}", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "Actual: ${s.current}" + if (s.habit.archived) " · archivado" else "",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MilestoneCard(m: Milestone) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF222222))
            .clickable { open = !open }.padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, fontWeight = FontWeight.Medium)
                Text(m.display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Text("${m.reached.size}/${m.thresholds.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { m.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Accent, trackColor = Color(0xFF3A3A3A),
        )
        Text(
            m.next?.let { "Siguiente hito: ${Achievements.thresholdLabel(m, it)}" } ?: "¡Todos los hitos conseguidos!",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(m.detail, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.thresholds.forEach { t ->
                        val got = m.value >= t
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (got) Accent else Color(0xFF333333))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                (if (got) "✓ " else "") + Achievements.thresholdLabel(m, t),
                                fontSize = 12.sp, color = if (got) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
