package com.rutina.app

import android.app.Application
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.Prefs
import com.rutina.app.health.HealthSync
import com.rutina.app.reminders.ReminderScheduler
import com.rutina.app.timer.TimerController
import com.rutina.app.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RutinaApp : Application() {
    companion object {
        /** La app en ejecución (para leer ajustes desde código sin contexto). */
        lateinit var instance: RutinaApp
            private set
    }

    /** Única instancia de la base de datos local (inyección manual, sin librerías extra). */
    val db: AppDatabase by lazy { AppDatabase.create(this) }

    /** Preferencias de la app (vista de hábitos, etc.). */
    val prefs: Prefs by lazy { Prefs(this) }

    /** Temporizador compartido por la pantalla, la notificación y la alarma. */
    val timer: TimerController by lazy { TimerController(this, db) }

    /** Lectura de Health Connect (pasos, distancia, etc.). */
    val health: HealthSync by lazy { HealthSync(this, db) }

    /** Avisos de tareas y recordatorios de hábitos. */
    val reminders: ReminderScheduler by lazy { ReminderScheduler(this, db) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        runCatching { com.rutina.app.backup.AutoBackup.schedule(this) }
        // Cada vez que cambian tus datos: reprogramar avisos y refrescar widgets.
        appScope.launch {
            combine(
                listOf<kotlinx.coroutines.flow.Flow<Any?>>(
                    db.tasks().observeAll(),
                    db.reminders().observeAll(),
                    db.habits().observeAll(),
                    db.habits().observeAllLogs(),
                    db.notes().observeAll(),
                    prefs.weekStart,
                )
            ) { Unit }.collectLatest {
                delay(800)
                runCatching { reminders.reschedule() }
                Widgets.refresh(this@RutinaApp)
            }
        }
    }
}
