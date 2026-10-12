package com.rutina.app.timer

import com.rutina.app.common.privateOnLockScreen

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
import com.rutina.app.data.HabitLog
import com.rutina.app.data.TimerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class TimerMode { STOPWATCH, COUNTDOWN }

enum class TimerEventKind(val label: String) { START("Iniciar"), PAUSE("Pausa"), RESUME("Reanudar") }

data class TimerEvent(val time: Long, val kind: TimerEventKind)

/**
 * Estado del temporizador. Se calcula con la hora del reloj, así que sigue
 * siendo correcto aunque cierres la app o el teléfono la detenga.
 */
data class TimerState(
    val mode: TimerMode = TimerMode.STOPWATCH,
    val running: Boolean = false,
    /** Inicio de la sesión; null = sin sesión activa. */
    val startedAt: Long? = null,
    /** Último inicio/reanudación mientras corre. */
    val resumedAt: Long? = null,
    /** Tiempo acumulado antes de [resumedAt]. */
    val accumulated: Long = 0L,
    val durationMs: Long = 15 * 60_000L,
    val habitId: Long? = null,
    val habitName: String? = null,
    /** Tiempo total al marcar cada vuelta. */
    val laps: List<Long> = emptyList(),
    val events: List<TimerEvent> = emptyList(),
) {
    val active: Boolean get() = startedAt != null

    fun elapsed(now: Long): Long =
        accumulated + if (running && resumedAt != null) (now - resumedAt).coerceAtLeast(0) else 0

    fun remaining(now: Long): Long = (durationMs - elapsed(now)).coerceAtLeast(0)
}

data class FinishedInfo(val durationMs: Long, val habitName: String?)

/** Un solo temporizador para toda la app (pantalla, notificación y alarma lo comparten). */
class TimerController(private val app: Context, private val db: AppDatabase) {

    private val prefs = app.getSharedPreferences("timer", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nm: NotificationManager = app.getSystemService(NotificationManager::class.java)
    private val alarm: AlarmManager = app.getSystemService(AlarmManager::class.java)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<TimerState> = _state

    private val _finished = MutableStateFlow<FinishedInfo?>(null)
    /** Última cuenta atrás completada (para avisar en pantalla). */
    val finished: StateFlow<FinishedInfo?> = _finished

    init {
        createChannels()
        val s = _state.value
        val now = System.currentTimeMillis()
        if (s.mode == TimerMode.COUNTDOWN && s.running && s.remaining(now) == 0L) {
            onCountdownFinished()
        } else if (s.active) {
            if (s.running && s.mode == TimerMode.COUNTDOWN) scheduleAlarm(s)
            refreshNotification()
        }
    }

    // ------------------------------------------------------------ acciones

    fun setMode(m: TimerMode): Boolean {
        val s = _state.value
        if (s.active) return s.mode == m
        set(s.copy(mode = m))
        return true
    }

    fun setDuration(ms: Long) {
        val s = _state.value
        if (!s.active) set(s.copy(durationMs = ms.coerceAtLeast(0)))
    }

    fun setHabit(id: Long?, name: String?) {
        set(_state.value.copy(habitId = id, habitName = name))
        if (_state.value.active) refreshNotification()
    }

    fun start() {
        val now = System.currentTimeMillis()
        val s = _state.value
        val next = when {
            !s.active -> {
                if (s.mode == TimerMode.COUNTDOWN && s.durationMs < 1000) return
                s.copy(
                    running = true, startedAt = now, resumedAt = now, accumulated = 0,
                    laps = emptyList(), events = listOf(TimerEvent(now, TimerEventKind.START)),
                )
            }
            !s.running -> s.copy(running = true, resumedAt = now, events = s.events + TimerEvent(now, TimerEventKind.RESUME))
            else -> return
        }
        set(next)
        if (next.mode == TimerMode.COUNTDOWN) scheduleAlarm(next)
        refreshNotification()
    }

    fun pause() {
        val now = System.currentTimeMillis()
        val s = _state.value
        if (!s.running) return
        set(
            s.copy(
                running = false, accumulated = s.elapsed(now), resumedAt = null,
                events = s.events + TimerEvent(now, TimerEventKind.PAUSE),
            )
        )
        cancelAlarm()
        refreshNotification()
    }

    fun lap() {
        val now = System.currentTimeMillis()
        val s = _state.value
        if (s.running) set(s.copy(laps = s.laps + s.elapsed(now)))
    }

    /** Termina la sesión. Con [save], se guarda y, si hay hábito, se registra el tiempo. */
    fun stop(save: Boolean): Job? {
        val now = System.currentTimeMillis()
        val s = _state.value
        if (!s.active) return null
        reset(s)
        return if (save) record(s, now) else null
    }

    fun onCountdownFinished(): Job? {
        val now = System.currentTimeMillis()
        val s = _state.value
        if (s.mode != TimerMode.COUNTDOWN || !s.running) return null
        if (s.remaining(now) > 1500) {
            // La alarma llegó antes de tiempo (alarma no exacta): reprogramar.
            scheduleAlarm(s)
            return null
        }
        reset(s)
        _finished.value = FinishedInfo(s.durationMs, s.habitName)
        notifyDone(s)
        return record(s, now)
    }

    fun clearFinished() { _finished.value = null }

    // ------------------------------------------------------------ internos

    private fun set(s: TimerState) {
        _state.value = s
        save(s)
    }

    private fun reset(s: TimerState) {
        set(TimerState(mode = s.mode, durationMs = s.durationMs, habitId = s.habitId, habitName = s.habitName))
        cancelAlarm()
        nm.cancel(ID_RUNNING)
    }

    private fun record(s: TimerState, end: Long): Job = scope.launch {
        val active = if (s.mode == TimerMode.COUNTDOWN) s.elapsed(end).coerceAtMost(s.durationMs) else s.elapsed(end)
        if (active < 1000) return@launch
        db.timers().insert(
            TimerSession(
                habitId = s.habitId, start = s.startedAt ?: end, end = end,
                activeMillis = active, countdown = s.mode == TimerMode.COUNTDOWN,
            )
        )
        val id = s.habitId ?: return@launch
        val h = db.habits().get(id) ?: return@launch
        db.habits().insertLog(
            HabitLog(habitId = id, timestamp = end, value = amountFor(h.unit, active), note = "⏱ ${formatDuration(active)}")
        )
    }

    // ------------------------------------------------------------ notificaciones

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannels() {
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, "Temporizador en curso", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Temporizador terminado", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
        )
    }

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        app, 0,
        Intent(app, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TIMER, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        app, code, Intent(app, TimerAlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Notificación fija con el tiempo corriendo y botones de Pausa / Detener. */
    private fun refreshNotification() {
        val s = _state.value
        if (!s.active) { nm.cancel(ID_RUNNING); return }
        if (!canNotify()) return
        val now = System.currentTimeMillis()
        val countdown = s.mode == TimerMode.COUNTDOWN
        val title = (if (countdown) "Cuenta atrás" else "Cronómetro") + (s.habitName?.let { " · $it" } ?: "")
        val b = NotificationCompat.Builder(app, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openIntent())
            .privateOnLockScreen(app, CH_RUNNING, R.drawable.ic_stat_timer, "Temporizador en curso")
        if (s.running) {
            b.setUsesChronometer(true)
                .setShowWhen(true)
                .setChronometerCountDown(countdown)
                .setWhen(if (countdown) now + s.remaining(now) else now - s.elapsed(now))
                .addAction(0, "Pausa", actionIntent(ACTION_PAUSE, 1))
        } else {
            b.setShowWhen(false)
                .setContentText("En pausa · ${formatClock(if (countdown) s.remaining(now) else s.elapsed(now))}")
                .addAction(0, "Reanudar", actionIntent(ACTION_RESUME, 2))
        }
        b.addAction(0, "Detener y guardar", actionIntent(ACTION_STOP, 3))
        try { nm.notify(ID_RUNNING, b.build()) } catch (_: SecurityException) { }
    }

    private fun notifyDone(s: TimerState) {
        if (!canNotify()) return
        val text = "${formatDuration(s.durationMs)} de concentración" + (s.habitName?.let { " · registrado en $it" } ?: "")
        val n = NotificationCompat.Builder(app, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("¡Tiempo cumplido!")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openIntent())
            .privateOnLockScreen(app, CH_DONE, R.drawable.ic_stat_timer, "¡Tiempo cumplido!")
            .build()
        try { nm.notify(ID_DONE, n) } catch (_: SecurityException) { }
    }

    // ------------------------------------------------------------ alarma

    private fun finishIntent(): PendingIntent = actionIntent(ACTION_FINISH, 10)

    private fun scheduleAlarm(s: TimerState) {
        val now = System.currentTimeMillis()
        val at = now + s.remaining(now)
        val pi = finishIntent()
        val exact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        try {
            if (exact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm() = alarm.cancel(finishIntent())

    // ------------------------------------------------------------ guardar estado

    private fun save(s: TimerState) {
        val o = JSONObject()
            .put("mode", s.mode.name)
            .put("running", s.running)
            .put("startedAt", s.startedAt ?: -1L)
            .put("resumedAt", s.resumedAt ?: -1L)
            .put("acc", s.accumulated)
            .put("dur", s.durationMs)
            .put("habit", s.habitId ?: -1L)
            .put("habitName", s.habitName ?: "")
            .put("laps", JSONArray(s.laps))
            .put("events", JSONArray(s.events.map { JSONObject().put("t", it.time).put("k", it.kind.name) }))
        prefs.edit().putString("state", o.toString()).apply()
    }

    private fun load(): TimerState = runCatching {
        val o = JSONObject(prefs.getString("state", null) ?: return TimerState())
        val laps = o.getJSONArray("laps").let { a -> List(a.length()) { a.getLong(it) } }
        val events = o.getJSONArray("events").let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { TimerEvent(it.getLong("t"), TimerEventKind.valueOf(it.getString("k"))) } }
        }
        TimerState(
            mode = TimerMode.valueOf(o.getString("mode")),
            running = o.getBoolean("running"),
            startedAt = o.getLong("startedAt").takeIf { it >= 0 },
            resumedAt = o.getLong("resumedAt").takeIf { it >= 0 },
            accumulated = o.getLong("acc"),
            durationMs = o.getLong("dur"),
            habitId = o.getLong("habit").takeIf { it > 0 },
            habitName = o.getString("habitName").ifBlank { null },
            laps = laps,
            events = events,
        )
    }.getOrDefault(TimerState())

    companion object {
        const val ACTION_FINISH = "com.rutina.app.timer.FINISH"
        const val ACTION_PAUSE = "com.rutina.app.timer.PAUSE"
        const val ACTION_RESUME = "com.rutina.app.timer.RESUME"
        const val ACTION_STOP = "com.rutina.app.timer.STOP"
        private const val CH_RUNNING = "timer_running"
        private const val CH_DONE = "timer_done"
        private const val ID_RUNNING = 1001
        private const val ID_DONE = 1002

        /** Convierte el tiempo a la unidad del hábito: minutos, horas o "1 vez". */
        fun amountFor(unit: String, ms: Long): Double = when (unit) {
            "min" -> Math.round(ms / 6000.0) / 10.0
            "horas" -> Math.round(ms / 36000.0) / 100.0
            else -> 1.0
        }

        /** "1 h 05 min", "25 min", "3 min 20 s", "45 s". */
        fun formatDuration(ms: Long): String {
            val s = ms / 1000
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return when {
                h > 0 -> "$h h %02d min".format(m)
                m >= 10 || (m > 0 && sec == 0L) -> "$m min"
                m > 0 -> "$m min $sec s"
                else -> "$sec s"
            }
        }

        /** "05:30" o "1:05:30". */
        fun formatClock(ms: Long): String {
            val s = (ms + 999) / 1000
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
        }

        /** "00:12.34" o "1:00:12.34" (cronómetro con centésimas). */
        fun formatStopwatch(ms: Long): String {
            val cs = (ms / 10) % 100
            val s = ms / 1000
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, sec, cs) else "%02d:%02d.%02d".format(m, sec, cs)
        }
    }
}

/** Elige un hábito y, si no hay sesión en curso, aplica su temporizador preferido. */
fun TimerController.useHabit(h: com.rutina.app.data.Habit) {
    setHabit(h.id, h.name)
    if (state.value.active) return
    h.timerMode?.let { m -> runCatching { setMode(TimerMode.valueOf(m)) } }
    h.timerMinutes?.let { min -> if (min > 0) setDuration(min * 60_000L) }
}
