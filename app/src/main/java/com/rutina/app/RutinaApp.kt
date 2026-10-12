package com.rutina.app

import android.app.Application
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.Prefs
import com.rutina.app.timer.TimerController

class RutinaApp : Application() {
    /** Única instancia de la base de datos local (inyección manual, sin librerías extra). */
    val db: AppDatabase by lazy { AppDatabase.create(this) }

    /** Preferencias de la app (vista de hábitos, etc.). */
    val prefs: Prefs by lazy { Prefs(this) }

    /** Temporizador compartido por la pantalla, la notificación y la alarma. */
    val timer: TimerController by lazy { TimerController(this, db) }
}
