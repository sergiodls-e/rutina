package com.rutina.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rutina.app.RutinaApp
import com.rutina.app.data.HabitLog
import com.rutina.app.data.TaskOps
import com.rutina.app.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarma de recordatorios y botones de sus notificaciones. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as RutinaApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
                when (intent.action) {
                    ReminderScheduler.ACTION_FIRE -> app.reminders.fire()
                    ReminderScheduler.ACTION_TASK_DONE -> {
                        app.db.tasks().get(id)?.let { if (!it.done) TaskOps.complete(app.db, it) }
                        app.reminders.cancelNotification(ReminderScheduler.taskNotifId(id))
                        Widgets.refresh(app)
                    }
                    ReminderScheduler.ACTION_HABIT_ADD -> {
                        val step = app.db.habits().get(id)?.step ?: 1.0
                        app.db.habits().insertLog(HabitLog(habitId = id, timestamp = System.currentTimeMillis(), value = step))
                        // El id de la notificación depende del recordatorio: se cierran las de ese hábito.
                        app.db.reminders().forHabit(id).forEach {
                            app.reminders.cancelNotification(ReminderScheduler.habitNotifId(it.id))
                        }
                        Widgets.refresh(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Al encender el teléfono, actualizar la app o cambiar la hora: volver a programar avisos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as RutinaApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.timer // reprograma la cuenta atrás si estaba corriendo
                app.reminders.reschedule()
                Widgets.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }
}
