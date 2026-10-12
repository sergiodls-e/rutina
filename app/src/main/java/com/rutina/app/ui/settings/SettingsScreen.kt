package com.rutina.app.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.backup.AutoBackup
import com.rutina.app.backup.AutoBackupOptions
import com.rutina.app.backup.autoBackupLabel
import com.rutina.app.data.CalView
import com.rutina.app.data.DataKind
import com.rutina.app.data.HabitDisplay
import com.rutina.app.data.StartTab
import com.rutina.app.data.TextSize
import com.rutina.app.data.WeekStarts
import com.rutina.app.data.weekStartLabel
import com.rutina.app.ui.common.Dropdown
import com.rutina.app.ui.common.HealthConnectCard
import com.rutina.app.ui.common.rememberHealthConnector
import com.rutina.app.ui.theme.Accent
import com.rutina.app.widget.HabitsWidgetReceiver
import com.rutina.app.widget.TasksWidgetReceiver
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val es = Locale("es", "MX")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onSearch: () -> Unit,
    onTutorial: () -> Unit,
    onAchievements: () -> Unit,
    onArchive: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = vm.prefs
    val weekStart by prefs.weekStart.collectAsState()
    val textSize by prefs.textSize.collectAsState()
    val startTab by prefs.startTab.collectAsState()
    val habitDisplay by prefs.habitDisplay.collectAsState()
    val calView by prefs.calendarView.collectAsState()
    val lastBackup by prefs.lastBackup.collectAsState()
    val archived by vm.archivedCount.collectAsState()
    val busy by vm.busy.collectAsState()
    val restore by vm.restore.collectAsState()
    val health = rememberHealthConnector()

    var askExport by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf(false) }
    var revealPw by remember { mutableStateOf(false) }
    var askRemovePw by remember { mutableStateOf(false) }
    var pendingDays by remember { mutableStateOf(0) }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    var offerDrive by remember { mutableStateOf<android.net.Uri?>(null) }
    val askNotif = com.rutina.app.ui.common.rememberNotificationPermission()
    fun openDrive(uri: android.net.Uri) {
        runCatching { ctx.startActivity(AutoBackup.shareIntent(ctx, uri)) }
            .onFailure { toast("No encontré Google Drive ni otra app para guardar el archivo.") }
    }
    val hasPw by prefs.hasBackupPassword.collectAsState()
    val autoDays by prefs.autoBackupDays.collectAsState()
    val folder by prefs.autoBackupFolder.collectAsState()
    val smartInput by prefs.smartInput.collectAsState()
    val folderName = remember(folder) { AutoBackup.folderName(ctx, folder) }

    // Ver, cambiar o quitar la contraseña pide tu PIN, patrón o huella.
    var afterAuth by remember { mutableStateOf<(() -> Unit)?>(null) }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val next = afterAuth
        afterAuth = null
        if (r.resultCode == android.app.Activity.RESULT_OK) next?.invoke() else toast("No se confirmó tu identidad")
    }
    fun withAuth(action: () -> Unit) {
        val km = ctx.getSystemService(android.app.KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val intent = if (km?.isDeviceSecure == true) {
            km.createConfirmDeviceCredentialIntent("Confirma que eres tú", "Para ver o cambiar tu contraseña de respaldo")
        } else null
        if (intent == null) {
            toast("Tu teléfono no tiene bloqueo de pantalla. Activa un PIN o huella para proteger tu contraseña.")
            action()
        } else {
            afterAuth = action
            authLauncher.launch(intent)
        }
    }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(ctx.contentResolver, uri, exportPassword) { err ->
            toast(err ?: "Respaldo guardado ✓")
        }
        exportPassword = null
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.setFolder(ctx, uri)
            if (pendingDays > 0) { vm.setAutoDays(ctx, pendingDays); askNotif() }
        }
        pendingDays = 0
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readBackup(ctx.contentResolver, uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrivacyCard(lastBackup)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF555555), RoundedCornerShape(12.dp))
                            .clickable(onClick = onSearch).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Busca tareas, hábitos…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("Tutorial", Icons.Filled.School, light = true, Modifier.weight(1f), onTutorial)
                        BigButton("Logros", Icons.Filled.EmojiEvents, light = false, Modifier.weight(1f), onAchievements)
                    }
                }
            }

            item { Section("Apariencia", Icons.Filled.Palette) }
            item {
                SettingRow("Tamaño del texto") {
                    Dropdown(textSize.label, TextSize.entries.map { it.label to it }) { prefs.setTextSize(it) }
                }
            }
            item {
                SettingRow("Inicio de semana", "Para hábitos semanales, estadísticas, calendario y widgets") {
                    Dropdown(weekStartLabel(weekStart), WeekStarts.map { weekStartLabel(it) to it }) { prefs.setWeekStart(it) }
                }
            }
            item {
                SettingRow("Abrir la app en") {
                    Dropdown(startTab.label, StartTab.entries.map { it.label to it }) { prefs.setStartTab(it) }
                }
            }
            item {
                SettingRow("Vista de hábitos") {
                    Dropdown(habitDisplay.label, HabitDisplay.entries.map { it.label to it }) { prefs.setHabitDisplay(it) }
                }
            }
            item {
                SettingRow("Vista del calendario") {
                    Dropdown(calView.label, CalView.entries.map { it.label to it }) { prefs.setCalendarView(it) }
                }
            }

            item { Section("Widgets", Icons.Filled.Widgets) }
            item {
                SettingRow("Hábitos de hoy", "Suma progreso sin abrir la app") {
                    ActionPill("Añadir") { pinWidget(ctx, HabitsWidgetReceiver::class.java) }
                }
            }
            item {
                SettingRow("Tareas", "Completa tareas desde la pantalla de inicio") {
                    ActionPill("Añadir") { pinWidget(ctx, TasksWidgetReceiver::class.java) }
                }
            }
            item {
                SettingRow("Informe del hábito", "Un hábito con su historial") {
                    ActionPill("Añadir") { pinWidget(ctx, com.rutina.app.widget.HabitReportWidgetReceiver::class.java) }
                }
            }
            item {
                SettingRow("Nota del diario", "Escribe una nota con un toque") {
                    ActionPill("Añadir") { pinWidget(ctx, com.rutina.app.widget.JournalWidgetReceiver::class.java) }
                }
            }
            item {
                Text(
                    "Mantén pulsado un widget en tu pantalla de inicio para elegir qué lista, filtro o hábito muestra.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp),
                )
            }

            item { Section("Health Connect", Icons.Filled.FavoriteBorder) }
            item { HealthConnectCard(health) }

            item { Section("Hábitos", Icons.Filled.Archive) }
            item {
                LinkRow("Archivo de hábitos", if (archived == 0) "Vacío" else "$archived ${if (archived == 1) "hábito" else "hábitos"}", onArchive)
            }
            item { LinkRow("Logros de toda la vida", "Totales, récords y rachas", onAchievements) }

            item { Section("Tareas", Icons.Filled.TaskAlt) }
            item {
                SettingRow("Entrada inteligente", "Detecta \"mañana 7pm\", \"cada lunes\"… al escribir el título") {
                    Switch(smartInput, { prefs.setSmartInput(it) })
                }
            }

            item { Section("Respaldo automático", Icons.Filled.Lock) }
            item {
                SettingRow(
                    "Contraseña de respaldo",
                    if (hasPw) "Guardada cifrada en este teléfono. Se usa en todos tus respaldos." else "Sin contraseña: los respaldos no se cifran",
                ) { ActionPill(if (hasPw) "Cambiar" else "Establecer") { if (hasPw) { withAuth { askPassword = true } } else { askPassword = true } } }
            }
            if (hasPw) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { withAuth { revealPw = true } }) { Text("Ver contraseña") }
                    TextButton(onClick = { withAuth { askRemovePw = true } }) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            item {
                SettingRow("Frecuencia", "Se revisa una vez al día, aunque no abras la app") {
                    Dropdown(autoBackupLabel(autoDays), AutoBackupOptions.map { it.second to it.first }) { d ->
                        when {
                            d == 0 -> vm.setAutoDays(ctx, 0)
                            !hasPw -> { pendingDays = d; askPassword = true; toast("Primero establece tu contraseña de respaldo") }
                            folder == null -> { pendingDays = d; pickFolder.launch(null) }
                            else -> { vm.setAutoDays(ctx, d); askNotif() }
                        }
                    }
                }
            }
            item {
                SettingRow("Carpeta", folderName ?: "Sin elegir") {
                    ActionPill(if (folder == null) "Elegir" else "Cambiar") { pickFolder.launch(null) }
                }
            }
            if (folder != null) item {
                LinkRow(
                    "Respaldar ahora",
                    "Guarda uno en la carpeta (se conservan los últimos 5)",
                    {
                        vm.backupNow(ctx) { r ->
                            when (r) {
                                is AutoBackup.Outcome.Ok -> offerDrive = r.shareUri
                                is AutoBackup.Outcome.Failed -> toast(r.message)
                            }
                        }
                    },
                    icon = Icons.Filled.Backup,
                )
            }
            item {
                Text(
                    "Rutina no puede subir a Drive por sí sola (no tiene Internet). Al terminar cada respaldo te llega un aviso con " +
                        "el botón \"Subir a Drive\": un toque y listo.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 10.dp),
                )
            }

            item { Section("Respaldo y datos", Icons.Filled.Backup) }
            item {
                LinkRow(
                    "Crear respaldo",
                    "Un archivo con todo. Guárdalo en Drive o en otro lugar seguro.",
                    { askExport = true },
                    icon = Icons.Filled.Backup,
                )
            }
            item {
                LinkRow(
                    "Restaurar respaldo",
                    "Para un teléfono nuevo o recuperar tus datos",
                    { openDoc.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                    icon = Icons.Filled.Restore,
                )
            }
            item {
                LinkRow(
                    "Eliminar datos",
                    "Borra hábitos, tareas, notas o sesiones de este teléfono",
                    { askDelete = true },
                    icon = Icons.Filled.DeleteForever,
                    danger = true,
                )
            }

            item { Section("Acerca de", Icons.Filled.Info) }
            item {
                val version = remember {
                    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rutina $version", fontWeight = FontWeight.Bold)
                    Text(
                        "Sin cuentas, sin anuncios y sin Internet. Hecha a tu medida.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Guardando respaldo…") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        )
    }

    if (askPassword) {
        PasswordDialog(
            changing = hasPw,
            onDismiss = { askPassword = false; pendingDays = 0 },
            onSave = { pw ->
                askPassword = false
                prefs.setBackupPassword(pw)
                toast("Contraseña guardada. Apúntala en un lugar seguro.")
                if (pendingDays > 0) {
                    if (folder == null) pickFolder.launch(null) else { vm.setAutoDays(ctx, pendingDays); pendingDays = 0 }
                }
            },
        )
    }

    if (revealPw) {
        var shown by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { revealPw = false },
            title = { Text("Contraseña de respaldo") },
            text = {
                if (shown == null) Text("¿Mostrarla en pantalla? Asegúrate de que nadie más la vea.")
                else androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(shown ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                if (shown == null) TextButton(onClick = { shown = prefs.backupPassword() ?: "(no se pudo leer)" }) { Text("Mostrar") }
                else TextButton(onClick = { revealPw = false }) { Text("Listo") }
            },
            dismissButton = { if (shown == null) TextButton(onClick = { revealPw = false }) { Text("Cancelar") } },
        )
    }

    if (askRemovePw) {
        AlertDialog(
            onDismissRequest = { askRemovePw = false },
            title = { Text("¿Quitar la contraseña?") },
            text = {
                Text(
                    "Los próximos respaldos se guardarán sin cifrar y el respaldo automático se desactivará. " +
                        "Los respaldos que ya creaste siguen necesitando esa contraseña.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.setBackupPassword(null); vm.setAutoDays(ctx, 0); askRemovePw = false
                }) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askRemovePw = false }) { Text("Cancelar") } },
        )
    }

    if (askExport) {
        ExportDialog(
            hasSaved = hasPw,
            onDismiss = { askExport = false },
            onDrive = { typed, useSaved ->
                askExport = false
                val pw = if (useSaved) prefs.backupPassword() else typed
                vm.shareBackup(ctx, pw) { uri -> if (uri == null) toast("No se pudo preparar el respaldo.") else openDrive(uri) }
            },
            onConfirm = { typed, useSaved ->
                askExport = false
                val pw = if (useSaved) prefs.backupPassword() else typed
                exportPassword = pw
                val suffix = if (pw.isNullOrEmpty()) "" else "-cifrado"
                createDoc.launch("rutina-respaldo-${LocalDate.now()}$suffix.json")
            },
        )
    }

    if (askDelete) {
        DeleteDialog(
            onDismiss = { askDelete = false },
            onConfirm = { kinds ->
                askDelete = false
                vm.deleteData(kinds) { toast("Datos eliminados") }
            },
        )
    }

    offerDrive?.let { uri ->
        AlertDialog(
            onDismissRequest = { offerDrive = null },
            title = { Text("Respaldo guardado ✓") },
            text = { Text("Quedó en tu carpeta de respaldos. ¿Quieres subir una copia a Google Drive ahora?") },
            confirmButton = { TextButton(onClick = { offerDrive = null; openDrive(uri) }) { Text("Subir a Drive") } },
            dismissButton = { TextButton(onClick = { offerDrive = null }) { Text("Ahora no") } },
        )
    }

    RestoreDialogs(restore, vm)
}

// ---------------------------------------------------------------- piezas

@Composable
private fun PrivacyCard(lastBackup: Long) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF222222)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
        Text("Tus datos son solo tuyos", color = Accent, fontWeight = FontWeight.SemiBold)
        Text(
            "Rutina no tiene permiso de Internet: todo se guarda únicamente en este teléfono.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            "Último respaldo: " + backupAge(lastBackup),
            fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = if (lastBackup == 0L || daysSince(lastBackup) > 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun daysSince(ms: Long): Long =
    ChronoUnit.DAYS.between(Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), LocalDate.now())

private fun backupAge(ms: Long): String {
    if (ms == 0L) return "nunca"
    val d = daysSince(ms)
    val date = Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", es))
    return when (d) {
        0L -> "hoy"
        1L -> "ayer"
        else -> "hace $d días ($date)"
    }
}

@Composable
private fun BigButton(label: String, icon: ImageVector, light: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (light) Color(0xFFD6D6D6) else Color(0xFF2A2A2A))
            .clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (light) Color.Black else Color.White
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = c, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Section(title: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF2A2A2A)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingRow(label: String, hint: String? = null, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            if (hint != null) Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ActionPill(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
    }
}

@Composable
private fun LinkRow(
    label: String,
    hint: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified)
            Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

private fun pinWidget(ctx: Context, receiver: Class<*>) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val ok = mgr.isRequestPinAppWidgetSupported &&
        runCatching { mgr.requestPinAppWidget(ComponentName(ctx, receiver), null, null) }.getOrDefault(false)
    if (!ok) {
        Toast.makeText(
            ctx,
            "Tu pantalla de inicio no permite añadirlo desde aquí. Mantén pulsada la pantalla de inicio → Widgets → Rutina.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

// ---------------------------------------------------------------- diálogos

@Composable
private fun PasswordDialog(changing: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (changing) "Cambiar contraseña de respaldo" else "Contraseña de respaldo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Se guarda cifrada con el chip de seguridad del teléfono y se usa en todos tus respaldos (manuales y automáticos).",
                    fontSize = 14.sp,
                )
                OutlinedTextField(
                    pw, { pw = it }, label = { Text("Contraseña (mín. ${com.rutina.app.data.Backup.MIN_PASSWORD})") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    pw2, { pw2 = it }, label = { Text("Repite la contraseña") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = pw2.isNotEmpty() && pw != pw2,
                )
                Text(
                    "Apúntala en un lugar seguro: en un teléfono nuevo la necesitarás para restaurar." +
                        if (changing) " Los respaldos anteriores siguen usando la contraseña vieja." else "",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(pw) }, enabled = pw.length >= com.rutina.app.data.Backup.MIN_PASSWORD && pw == pw2) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ExportDialog(
    hasSaved: Boolean,
    onDismiss: () -> Unit,
    onDrive: (String?, Boolean) -> Unit,
    onConfirm: (String?, Boolean) -> Unit,
) {
    var useSaved by remember { mutableStateOf(hasSaved) }
    var protect by remember { mutableStateOf(false) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val valid = useSaved || !protect || (pw.length >= com.rutina.app.data.Backup.MIN_PASSWORD && pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear respaldo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Se crea un archivo con todos tus hábitos, registros, tareas, notas y ajustes. " +
                        "\"Subir a Drive\" abre Google Drive con el archivo listo; \"Guardar en una carpeta\" lo deja en el teléfono.",
                    fontSize = 14.sp,
                )
                if (hasSaved) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cifrar con tu contraseña de respaldo", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(useSaved, { useSaved = it })
                }
                if (!useSaved) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hasSaved) "Usar otra contraseña" else "Proteger con contraseña", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(protect, { protect = it })
                }
                if (!useSaved && protect) {
                    OutlinedTextField(
                        pw, { pw = it }, label = { Text("Contraseña (mín. ${com.rutina.app.data.Backup.MIN_PASSWORD})") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    OutlinedTextField(
                        pw2, { pw2 = it }, label = { Text("Repite la contraseña") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = pw2.isNotEmpty() && pw != pw2,
                    )
                    Text(
                        "Cifrado AES‑256. Si olvidas la contraseña, nadie podrá abrir el respaldo (ni tú).",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onDrive(if (!useSaved && protect) pw else null, useSaved) }, enabled = valid) { Text("Subir a Drive") }
                TextButton(onClick = { onConfirm(if (!useSaved && protect) pw else null, useSaved) }, enabled = valid) { Text("Guardar en una carpeta") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun DeleteDialog(onDismiss: () -> Unit, onConfirm: (Set<DataKind>) -> Unit) {
    var picked by remember { mutableStateOf(emptySet<DataKind>()) }
    var sure by remember { mutableStateOf(false) }
    if (!sure) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Eliminar datos") },
            text = {
                Column {
                    Text("Elige qué borrar de este teléfono:", fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    DataKind.entries.forEach { k ->
                        Row(
                            Modifier.fillMaxWidth().clickable { picked = if (k in picked) picked - k else picked + k },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(k in picked, { picked = if (k in picked) picked - k else picked + k })
                            Column {
                                Text(k.label, fontWeight = FontWeight.Medium)
                                Text(k.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    TextButton(onClick = { picked = DataKind.entries.toSet() }) { Text("Seleccionar todo") }
                }
            },
            confirmButton = {
                TextButton(onClick = { sure = true }, enabled = picked.isNotEmpty()) {
                    Text("Continuar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("¿Seguro?") },
            text = {
                Text(
                    "Se borrará: " + picked.sortedBy { it.ordinal }.joinToString { it.label.lowercase() } +
                        ".\n\nNo se puede deshacer. Si no tienes un respaldo reciente, crea uno primero.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(picked) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RestoreDialogs(step: RestoreStep, vm: SettingsViewModel) {
    when (step) {
        RestoreStep.Idle -> Unit
        RestoreStep.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Un momento…") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        )
        is RestoreStep.NeedsPassword -> {
            var pw by remember(step) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = vm::dismissRestore,
                title = { Text("Respaldo protegido") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Escribe la contraseña con la que lo guardaste.", fontSize = 14.sp)
                        OutlinedTextField(
                            pw, { pw = it }, label = { Text("Contraseña") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = step.wrong,
                            supportingText = { if (step.wrong) Text("Contraseña incorrecta") },
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { vm.unlock(step.text, pw) }, enabled = pw.isNotEmpty()) { Text("Abrir") } },
                dismissButton = { TextButton(onClick = vm::dismissRestore) { Text("Cancelar") } },
            )
        }
        is RestoreStep.Ready -> {
            val d = step.data
            val date = if (d.exportedAt > 0) {
                Instant.ofEpochMilli(d.exportedAt).atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d 'de' MMMM yyyy, HH:mm", es))
            } else "fecha desconocida"
            AlertDialog(
                onDismissRequest = vm::dismissRestore,
                title = { Text("¿Restaurar este respaldo?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Creado el $date", fontWeight = FontWeight.Medium)
                        Text(d.summary, fontSize = 14.sp)
                        Text(
                            "Todos los datos que hay ahora en este teléfono se reemplazarán por los del respaldo.",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { vm.confirmRestore(d) }) { Text("Restaurar") } },
                dismissButton = { TextButton(onClick = vm::dismissRestore) { Text("Cancelar") } },
            )
        }
        is RestoreStep.Error -> AlertDialog(
            onDismissRequest = vm::dismissRestore,
            title = { Text("No se pudo restaurar") },
            text = { Text(step.message) },
            confirmButton = { TextButton(onClick = vm::dismissRestore) { Text("Aceptar") } },
        )
        RestoreStep.Done -> AlertDialog(
            onDismissRequest = vm::dismissRestore,
            title = { Text("¡Listo!") },
            text = { Text("Tus datos se restauraron correctamente.") },
            confirmButton = { TextButton(onClick = vm::dismissRestore) { Text("Aceptar") } },
        )
    }
}
