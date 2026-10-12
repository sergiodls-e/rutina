package com.rutina.app

import android.app.Application
import com.rutina.app.data.AppDatabase

class RutinaApp : Application() {
    /** Única instancia de la base de datos local (inyección manual, sin librerías extra). */
    val db: AppDatabase by lazy { AppDatabase.create(this) }
}
