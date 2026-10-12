package com.rutina.app.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.RutinaApp
import com.rutina.app.health.HealthStatus
import kotlinx.coroutines.launch

/** Estado de Health Connect y acciones para conectarlo o sincronizar. */
class HealthConnector(val status: HealthStatus, val connect: () -> Unit, val sync: () -> Unit)

@Composable
fun rememberHealthConnector(): HealthConnector {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as RutinaApp
    val scope = rememberCoroutineScope()
    val status by app.health.status.collectAsState()
    LaunchedEffect(Unit) { app.health.refreshStatus() }
    val launcher = rememberLauncherForActivityResult(app.health.permissionContract()) {
        scope.launch { app.health.refreshStatus(); app.health.syncAll() }
    }
    return HealthConnector(
        status = status,
        connect = {
            when (status) {
                HealthStatus.NO_PERMISSION, HealthStatus.READY -> launcher.launch(app.health.permissions)
                else -> runCatching { ctx.startActivity(app.health.installIntent()) }
            }
        },
        sync = { scope.launch { app.health.syncAll() } },
    )
}

/** Tarjeta con el estado de la conexión y el botón para conectar. */
@Composable
fun HealthConnectCard(h: HealthConnector, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (h.status) {
                HealthStatus.READY -> "✓ Health Connect conectado. Los datos se actualizan al abrir la app."
                HealthStatus.NO_PERMISSION -> "Da permiso de lectura para traer tus datos de Health Connect."
                HealthStatus.NEEDS_UPDATE -> "Necesitas actualizar Health Connect."
                HealthStatus.UNAVAILABLE -> "Health Connect no está disponible en este teléfono. Puedes instalarlo desde la tienda."
            },
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Solo lectura. Los datos se guardan únicamente en este teléfono.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = h.connect) {
            Text(
                when (h.status) {
                    HealthStatus.READY -> "Revisar permisos"
                    HealthStatus.NO_PERMISSION -> "Conectar Health Connect"
                    else -> "Instalar Health Connect"
                }
            )
        }
    }
}
