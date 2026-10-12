package com.rutina.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.ui.theme.RutinaTheme

/** Explica qué hace Rutina con tus datos de salud (Health Connect la exige). */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RutinaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Tus datos de salud", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Rutina solo LEE de Health Connect los pasos, la distancia, las calorías activas, " +
                                "los pisos subidos y los minutos de ejercicio, para mostrarlos en tus hábitos."
                        )
                        Text(
                            "Los totales por día se guardan únicamente en la base de datos de este teléfono. " +
                                "La app no tiene permiso de Internet: no puede enviar tus datos a ningún servidor, " +
                                "ni a nosotros ni a terceros."
                        )
                        Text("Rutina nunca escribe ni borra datos en Health Connect.")
                        Text(
                            "Puedes quitar el permiso cuando quieras desde Health Connect. " +
                                "Si borras un hábito de salud, se borran también sus registros guardados."
                        )
                        Button(onClick = { finish() }) { Text("Entendido") }
                    }
                }
            }
        }
    }
}
