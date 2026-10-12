package com.rutina.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rutina.app.MainActivity
import com.rutina.app.R
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.GoalType
import com.rutina.app.data.Habit
import com.rutina.app.data.HabitReminder
import com.rutina.app.data.Task
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.HabitStats
import com.rutina.app.domain.TaskLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Programa UNA sola alarma: la del próximo aviso (tarea o recordatorio de hábito).
 * Al sonar, muestra lo que toque y programa la siguiente. Así no quedan alarmas
 * huérfanas al borrar o cambiar cosas: cada cambio recalcula todo.
 */
class ReminderScheduler(private val app: Context, private val db: AppDatabase) {

    private val prefs = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val alarm: AlarmManager = app.getSystemService(AlarmManager::class.java)
    private val nm: NotificationManager = app.getSystemService(NotificationManager::class.java)
    private val mutex = Mutex()

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Recordatorios", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos de tareas y recordatorios de hábitos"
                enableVibration(true)
            }
        )
    }

    private data class Occ(val at: Long, val task: Task? = null, val reminder: HabitReminder? = null, val habit: Habit? = null)

    private data class Data(val tasks: List<Task>, val reminders: List<HabitReminder>, val habits: Map<Long, Habit>)

    private suspend fun load() = Data(
        db.tasks().observeAll().first(),
        db.reminders().observeAll().first(),
        db.habits().observeActive().first().associateBy { it.id },
    )

    /** Avisos con hora en (after, until]. Para cada recordatorio solo su siguiente ocurrencia. */
    private fun occurrences(d: Data, after: Long, until: Long): List<Occ> {
        val zone = HabitLogic.zone
        val out = mutableListOf<Occ>()
        for (t in d.tasks) {
            if (!t.notify || t.done) continue
            val day = t.dueEpochDay ?: continue
            val min = t.dueMinutes ?: continue
            val at = LocalDate.ofEpochDay(day).atStartOfDay(zone).plusMinutes(min.toLong()).toInstant().toEpochMilli()
            if (at in (after + 1)..until) out += Occ(at, task = t)
        }
        for (r in d.reminders) {
            val h = d.habits[r.habitId] ?: continue
            val start = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
            for (i in 0..7) {
                val day = start.plusDays(i.toLong())
                if (r.days and (1 shl (day.dayOfWeek.value - 1)) == 0) continue
                val at = day.atStartOfDay(zone).plusMinutes(r.minutes.toLong()).toInstant().toEpochMilli()
                if (at > after) {
                    if (at <= until) out += Occ(at, reminder = r, habit = h)
                    break
                }
            }
        }
        return out
    }

    /** Recalcula y programa la próxima alarma. Llamar cuando cambian tareas o recordatorios. */
    suspend fun reschedule() = mutex.withLock {
        val now = System.currentTimeMillis()
        val last = maxOf(prefs.getLong(KEY_LAST, 0L), now - 60_000)
        prefs.edit().putLong(KEY_LAST, last).apply()
        val next = occurrences(load(), last, Long.MAX_VALUE).minOfOrNull { it.at }
        val pi = fireIntent()
        if (next == null) {
            alarm.cancel(pi)
            return@withLock
        }
        val exact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        try {
            if (exact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    /** La alarma sonó: muestra todo lo que tocaba y programa la siguiente. */
    suspend fun fire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val last = maxOf(prefs.getLong(KEY_LAST, 0L), now - 10 * 60_000)
            val until = now + 5_000
            val data = load()
            for (o in occurrences(data, last, until)) {
                when {
                    o.task != null -> notifyTask(o.task)
                    o.reminder != null && o.habit != null -> notifyHabit(o.reminder, o.habit)
                }
            }
            prefs.edit().putLong(KEY_LAST, until).apply()
        }
        reschedule()
    }

    // ------------------------------------------------------------ notificaciones

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(code: Int): PendingIntent = PendingIntent.getActivity(
        app, code, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String, id: Long, code: Int): PendingIntent = PendingIntent.getBroadcast(
        app, code,
        Intent(app, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notifyTask(t: Task) {
        if (!canNotify()) return
        val id = taskNotifId(t.id)
        val text = listOfNotNull(TaskLogic.dueLabel(t), t.description.takeIf { it.isNotBlank() }).joinToString(" · ")
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(t.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(id))
            .addAction(0, "Completar", action(ACTION_TASK_DONE, t.id, id))
            .build()
        try { nm.notify(id, n) } catch (_: SecurityException) { }
    }

    private suspend fun notifyHabit(r: HabitReminder, h: Habit) {
        if (!canNotify()) return
        val today = LocalDate.now()
        val logs = db.habits().observeLogsFor(h.id).first()
        val progress = HabitLogic.progress(h, logs, today, com.rutina.app.RutinaApp.instance.prefs.weekStart.value)
        // Si ya cumpliste la meta positiva de hoy, no hace falta recordarlo.
        val alreadyDone = when (h.type) {
            TrackingType.COUNT, TrackingType.HEALTH -> h.goalType == GoalType.POSITIVE && HabitLogic.isMet(h, progress)
            TrackingType.MOOD, TrackingType.WEIGHT -> com.rutina.app.domain.loggedOn(logs, today)
            else -> false
        }
        if (alreadyDone) return
        val text = when (h.type) {
            TrackingType.QUIT -> "Llevas ${HabitLogic.formatShort(HabitLogic.quitNow(h) - (h.quitSince ?: h.createdAt))} sin recaer. ¡Sigue así!"
            TrackingType.JOURNAL -> "Escribe unas palabras en tu diario."
            TrackingType.MOOD -> "¿Cómo te sientes hoy? Registra tu estado de ánimo."
            TrackingType.WEIGHT -> "Es buen momento para registrar tu peso."
            else -> {
                val streak = HabitStats.summary(h, logs, com.rutina.app.RutinaApp.instance.prefs.weekStart.value, today).current
                "Hoy: ${HabitLogic.progressLabel(h, progress)}" + if (streak > 0) " · racha de $streak 🔥" else ""
            }
        }
        val id = habitNotifId(r.id)
        val b = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(h.name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(id))
        if (h.type == TrackingType.COUNT) b.addAction(0, "+" + HabitLogic.amountLabel(h.step, h), action(ACTION_HABIT_ADD, h.id, id))
        try { nm.notify(id, b.build()) } catch (_: SecurityException) { }
    }

    private fun fireIntent(): PendingIntent = PendingIntent.getBroadcast(
        app, 1, Intent(app, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun cancelNotification(id: Int) = nm.cancel(id)

    companion object {
        const val CHANNEL = "reminders"
        const val ACTION_FIRE = "com.rutina.app.reminders.FIRE"
        const val ACTION_TASK_DONE = "com.rutina.app.reminders.TASK_DONE"
        const val ACTION_HABIT_ADD = "com.rutina.app.reminders.HABIT_ADD"
        const val EXTRA_ID = "id"
        private const val KEY_LAST = "last"

        fun taskNotifId(taskId: Long) = 10_000 + (taskId % 1_000_000).toInt()
        fun habitNotifId(reminderId: Long) = 2_000_000 + (reminderId % 1_000_000).toInt()
    }
}
