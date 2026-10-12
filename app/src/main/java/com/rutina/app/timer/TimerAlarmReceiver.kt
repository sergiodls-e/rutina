package com.rutina.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rutina.app.RutinaApp

/** Recibe el fin de la cuenta atrás y los botones de la notificación. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = (context.applicationContext as RutinaApp).timer
        val job = when (intent.action) {
            TimerController.ACTION_FINISH -> timer.onCountdownFinished()
            TimerController.ACTION_PAUSE -> { timer.pause(); null }
            TimerController.ACTION_RESUME -> { timer.start(); null }
            TimerController.ACTION_STOP -> timer.stop(save = true)
            else -> null
        }
        // Mantiene vivo el proceso hasta que se guarde el registro en la base de datos.
        if (job != null) {
            val pending = goAsync()
            job.invokeOnCompletion { pending.finish() }
        }
    }
}
