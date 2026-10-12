package com.rutina.app.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.launch

/**
 * Guías paso a paso: primero el índice de secciones; al elegir una guía se abre
 * el carrusel con la vista previa del teléfono y los tres pasos.
 */
@Composable
fun TutorialScreen(onClose: () -> Unit) {
    // -1 = índice; si no, página del carrusel.
    var page by rememberSaveable { mutableStateOf(-1) }
    if (page < 0) {
        TutorialIndex(onClose = onClose, onOpen = { page = it })
    } else {
        BackHandler { page = -1 }
        GuidePager(start = page, onClose = onClose, onIndex = { page = -1 })
    }
}

// ---------------------------------------------------------------- Índice

@Composable
private fun TutorialIndex(onClose: () -> Unit, onOpen: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().background(Color(0xFF151515))) {
        Row(
            Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
            Text("Tutorial", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(4.dp, 0.dp, 4.dp, 6.dp)) {
                    Text("Aprende a usar Rutina", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Elige un tema o empieza desde el principio. Desliza o usa ‹ › para avanzar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFD6D6D6))
                        .clickable { onOpen(0) }.padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { Text("Empezar desde el principio", color = Color.Black, fontWeight = FontWeight.Medium) }
            }
            items(Tutorial.sections, key = { it.name }) { s ->
                val open = expanded == s.name
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(s.panel)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = if (open) "" else s.name }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                            Text("${s.guides.size} guías", fontSize = 13.sp, color = Color(0xFFCCCCCC))
                        }
                        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Color.White)
                    }
                    if (open) {
                        val first = Tutorial.firstPageOf(s)
                        s.guides.forEachIndexed { i, gd ->
                            HorizontalDivider(color = Color.Black.copy(alpha = 0.25f))
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpen(first + i) }.padding(16.dp, 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${i + 1}", color = s.color, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                Text(gd.title, color = Color.White, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFCCCCCC))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Carrusel

@Composable
private fun GuidePager(start: Int, onClose: () -> Unit, onIndex: () -> Unit) {
    val pages = Tutorial.pages
    val pager = rememberPagerState(initialPage = start.coerceIn(0, pages.lastIndex), pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val cur = pages[pager.currentPage]

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().background(Color(0xFF151515))) {
        // Encabezado
        Row(
            Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(cur.guide.title, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${cur.section.name} · ${cur.index + 1} de ${cur.section.guides.size}",
                    fontSize = 15.sp, color = Color(0xFFDDDDDD),
                )
            }
            IconButton(onClick = onIndex) { Icon(Icons.Filled.ViewList, "Índice", tint = Color.White) }
        }

        // Vista previa (desliza para cambiar de guía)
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().background(Color(0xFF262626))) { i ->
            PhonePreview(pages[i].guide)
        }

        // Panel inferior
        Column(Modifier.fillMaxWidth().background(Color(0xFF161616)).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavBox(Icons.Filled.ChevronLeft, "Anterior", enabled = pager.currentPage > 0) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(cur.section.panel).clickable(onClick = onIndex),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(cur.guide.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            Text("${cur.section.name} · ${cur.index + 1} de ${cur.section.guides.size}", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                        }
                        Icon(Icons.Filled.ViewList, null, tint = cur.section.color)
                    }
                    // Avance dentro de la sección
                    val f = (cur.index + 1f) / cur.section.guides.size
                    Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF555555))) {
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(cur.section.color))
                    }
                }
                Spacer(Modifier.width(10.dp))
                NavBox(Icons.Filled.ChevronRight, "Siguiente", enabled = pager.currentPage < pages.lastIndex) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }
            Spacer(Modifier.height(8.dp))
            cur.guide.steps.forEachIndexed { i, step ->
                HorizontalDivider(color = Color(0xFF333333))
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(cur.section.panel),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", color = cur.section.color, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(14.dp))
                    Text(step, fontSize = 15.sp, color = Color(0xFFEEEEEE), lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun NavBox(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 56.dp, height = 56.dp).clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2E2E2E))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (enabled) Color.White else Color(0xFF666666)) }
}

/** Marco de teléfono con la pantalla de ejemplo y el punto donde "tocar". */
@Composable
private fun PhonePreview(guide: Guide) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 20.dp), contentAlignment = Alignment.TopCenter) {
        val w = 250.dp
        val h = maxHeight + 40.dp // el teléfono "sigue" por debajo del panel
        Box(
            Modifier.width(w).height(h)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xFF3A3A3A))
                .padding(6.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF151515)),
        ) {
            MockScreen(guide.mock)
            guide.tap?.let { t ->
                val innerW = w - 12.dp
                val visibleH = maxHeight - 26.dp
                Box(
                    Modifier
                        .offset(x = innerW * t.x - 18.dp, y = visibleH * t.y - 18.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xCCB0B0C0))
                        .border(1.5.dp, Color(0xFFE0E0FF), CircleShape),
                )
            }
        }
    }
}

@Composable
internal fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray, textAlign = TextAlign.Center)
    }
}
