package com.rutina.app.data

import android.util.Base64
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Todo lo que contiene un respaldo. */
data class BackupData(
    val exportedAt: Long,
    val habits: List<Habit>,
    val habitLogs: List<HabitLog>,
    val reminders: List<HabitReminder>,
    val lists: List<TaskList>,
    val tasks: List<Task>,
    val filters: List<TaskFilter>,
    val notes: List<Note>,
    val sessions: List<TimerSession>,
    val settings: Map<String, String>,
) {
    val summary: String
        get() = "${habits.size} hábitos · ${habitLogs.size} registros · ${tasks.size} tareas · " +
            "${notes.size} notas · ${sessions.size} sesiones"
}

/** Qué se puede borrar desde Ajustes. */
enum class DataKind(val label: String, val detail: String) {
    HABITS("Hábitos", "hábitos, sus registros y recordatorios"),
    TASKS("Tareas", "tareas, listas y filtros"),
    NOTES("Notas del diario", "todas las notas"),
    TIMER("Sesiones del temporizador", "historial de sesiones"),
}

class WrongPasswordException : Exception("Contraseña incorrecta")
class InvalidBackupException(msg: String) : Exception(msg)

/**
 * Respaldo completo en un archivo JSON legible (o cifrado con contraseña).
 * Solo usa librerías incluidas en Android: no hace falta Internet.
 */
object Backup {
    private const val APP = "Rutina"
    private const val FORMAT = 1
    private const val ITERATIONS = 150_000

    // ------------------------------------------------------------ exportar

    suspend fun collect(db: AppDatabase, prefs: Prefs): BackupData = withContext(Dispatchers.IO) {
        val b = db.backup()
        BackupData(
            exportedAt = System.currentTimeMillis(),
            habits = b.habits(),
            habitLogs = b.habitLogs(),
            reminders = b.reminders(),
            lists = b.lists(),
            tasks = b.tasks(),
            filters = b.filters(),
            notes = b.notes(),
            sessions = b.sessions(),
            settings = prefs.exportMap(),
        )
    }

    fun toJson(d: BackupData): String = JSONObject().apply {
        put("app", APP)
        put("format", FORMAT)
        put("exportedAt", d.exportedAt)
        put("habits", arr(d.habits) { h ->
            put("id", h.id); put("name", h.name); put("color", h.color); put("type", h.type.name)
            put("goalType", h.goalType.name); put("target", h.target); put("unit", h.unit)
            put("period", h.period.name); put("scheduleType", h.scheduleType.name)
            put("scheduleDays", h.scheduleDays); put("daysPerWeek", h.daysPerWeek)
            putNullable("quitSince", h.quitSince); put("archived", h.archived)
            put("sortOrder", h.sortOrder); put("createdAt", h.createdAt)
        })
        put("habitLogs", arr(d.habitLogs) { l ->
            put("id", l.id); put("habitId", l.habitId); put("timestamp", l.timestamp)
            put("value", l.value); putNullable("note", l.note)
        })
        put("reminders", arr(d.reminders) { r ->
            put("id", r.id); put("habitId", r.habitId); put("minutes", r.minutes); put("days", r.days)
        })
        put("lists", arr(d.lists) { l ->
            put("id", l.id); put("name", l.name); put("color", l.color); put("sortOrder", l.sortOrder); put("icon", l.icon)
        })
        put("tasks", arr(d.tasks) { t ->
            put("id", t.id); put("title", t.title); put("description", t.description)
            putNullable("dueEpochDay", t.dueEpochDay); putNullable("dueMinutes", t.dueMinutes)
            put("repeat", t.repeat.name); put("notify", t.notify); put("durationMin", t.durationMin)
            put("priority", t.priority); putNullable("listId", t.listId); put("done", t.done)
            putNullable("doneAt", t.doneAt); put("createdAt", t.createdAt)
            put("skipped", t.skipped); put("repeatEvery", t.repeatEvery); put("repeatDays", t.repeatDays)
            putNullable("repeatUntil", t.repeatUntil); putNullable("seriesId", t.seriesId); put("sortOrder", t.sortOrder)
        })
        put("filters", arr(d.filters) { f ->
            put("id", f.id); put("name", f.name); put("color", f.color); put("dateRange", f.dateRange.name)
            put("minPriority", f.minPriority); putNullable("listId", f.listId)
            put("includeDone", f.includeDone); put("sortOrder", f.sortOrder); put("text", f.text)
        })
        put("notes", arr(d.notes) { n ->
            put("id", n.id); put("timestamp", n.timestamp); put("content", n.content)
            putNullable("habitId", n.habitId); putNullable("taskId", n.taskId); put("starred", n.starred)
        })
        put("sessions", arr(d.sessions) { s ->
            put("id", s.id); putNullable("habitId", s.habitId); put("start", s.start); put("end", s.end)
            put("activeMillis", s.activeMillis); put("countdown", s.countdown)
        })
        put("settings", JSONObject(d.settings as Map<*, *>))
    }.toString(1)

    // ------------------------------------------------------------ leer

    fun isEncrypted(text: String): Boolean =
        runCatching { JSONObject(text).optBoolean("encrypted", false) }.getOrDefault(false)

    fun parse(text: String): BackupData {
        val o = runCatching { JSONObject(text) }.getOrElse { throw InvalidBackupException("El archivo no es un respaldo de Rutina.") }
        if (o.optString("app") != APP || !o.has("habits")) throw InvalidBackupException("El archivo no es un respaldo de Rutina.")
        if (o.optInt("format", 0) > FORMAT) throw InvalidBackupException("Este respaldo es de una versión más nueva de Rutina. Actualiza la app.")
        return try {
            BackupData(
                exportedAt = o.optLong("exportedAt", 0L),
                habits = list(o, "habits") { j ->
                    Habit(
                        id = j.getLong("id"), name = j.getString("name"), color = j.getInt("color"),
                        type = enumOr(j.optString("type"), TrackingType.COUNT),
                        goalType = enumOr(j.optString("goalType"), GoalType.POSITIVE),
                        target = j.optDouble("target", 1.0), unit = j.optString("unit", "vez"),
                        period = enumOr(j.optString("period"), GoalPeriod.DAY),
                        scheduleType = enumOr(j.optString("scheduleType"), ScheduleType.ALL_DAYS),
                        scheduleDays = j.optInt("scheduleDays", 0b1111111), daysPerWeek = j.optInt("daysPerWeek", 7),
                        quitSince = j.longOrNull("quitSince"), archived = j.optBoolean("archived", false),
                        sortOrder = j.optInt("sortOrder", 0), createdAt = j.optLong("createdAt", System.currentTimeMillis()),
                    )
                },
                habitLogs = list(o, "habitLogs") { j ->
                    HabitLog(
                        id = j.getLong("id"), habitId = j.getLong("habitId"), timestamp = j.getLong("timestamp"),
                        value = j.optDouble("value", 1.0), note = j.stringOrNull("note"),
                    )
                },
                reminders = list(o, "reminders") { j ->
                    HabitReminder(id = j.getLong("id"), habitId = j.getLong("habitId"), minutes = j.getInt("minutes"), days = j.optInt("days", 0b1111111))
                },
                lists = list(o, "lists") { j ->
                    TaskList(
                        id = j.getLong("id"), name = j.getString("name"), color = j.getInt("color"),
                        sortOrder = j.optInt("sortOrder", 0), icon = j.optString("icon", "list"),
                    )
                },
                tasks = list(o, "tasks") { j ->
                    Task(
                        id = j.getLong("id"), title = j.getString("title"), description = j.optString("description", ""),
                        dueEpochDay = j.longOrNull("dueEpochDay"), dueMinutes = j.longOrNull("dueMinutes")?.toInt(),
                        repeat = enumOr(j.optString("repeat"), RepeatRule.NEVER), notify = j.optBoolean("notify", false),
                        durationMin = j.optInt("durationMin", 30), priority = j.optInt("priority", 0),
                        listId = j.longOrNull("listId"), done = j.optBoolean("done", false), doneAt = j.longOrNull("doneAt"),
                        createdAt = j.optLong("createdAt", System.currentTimeMillis()),
                        skipped = j.optBoolean("skipped", false), repeatEvery = j.optInt("repeatEvery", 1).coerceAtLeast(1),
                        repeatDays = j.optInt("repeatDays", 0), repeatUntil = j.longOrNull("repeatUntil"),
                        seriesId = j.longOrNull("seriesId"), sortOrder = j.optInt("sortOrder", 0),
                    )
                },
                filters = list(o, "filters") { j ->
                    TaskFilter(
                        id = j.getLong("id"), name = j.getString("name"), color = j.getInt("color"),
                        dateRange = enumOr(j.optString("dateRange"), DateRange.ANY), minPriority = j.optInt("minPriority", 0),
                        listId = j.longOrNull("listId"), includeDone = j.optBoolean("includeDone", false), sortOrder = j.optInt("sortOrder", 0),
                        text = j.optString("text", ""),
                    )
                },
                notes = list(o, "notes") { j ->
                    Note(
                        id = j.getLong("id"), timestamp = j.getLong("timestamp"), content = j.optString("content", ""),
                        habitId = j.longOrNull("habitId"), taskId = j.longOrNull("taskId"), starred = j.optBoolean("starred", false),
                    )
                },
                sessions = list(o, "sessions") { j ->
                    TimerSession(
                        id = j.getLong("id"), habitId = j.longOrNull("habitId"), start = j.getLong("start"), end = j.getLong("end"),
                        activeMillis = j.optLong("activeMillis", 0L), countdown = j.optBoolean("countdown", false),
                    )
                },
                settings = o.optJSONObject("settings")?.let { s -> s.keys().asSequence().associateWith { s.optString(it) } }.orEmpty(),
            )
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: Exception) {
            throw InvalidBackupException("El respaldo está dañado o incompleto.")
        }
    }

    // ------------------------------------------------------------ restaurar

    /** Reemplaza TODOS los datos de la app por los del respaldo, en un solo paso (todo o nada). */
    suspend fun restore(db: AppDatabase, prefs: Prefs, d: BackupData) = withContext(Dispatchers.IO) {
        val habitIds = d.habits.map { it.id }.toSet()
        val listIds = d.lists.map { it.id }.toSet()
        db.withTransaction {
            val b = db.backup()
            clearAll(b)
            b.insertHabits(d.habits)
            b.insertHabitLogs(d.habitLogs.filter { it.habitId in habitIds })
            b.insertReminders(d.reminders.filter { it.habitId in habitIds })
            b.insertLists(d.lists)
            b.insertTasks(d.tasks.map { if (it.listId != null && it.listId !in listIds) it.copy(listId = null) else it })
            b.insertFilters(d.filters)
            b.insertNotes(d.notes)
            b.insertSessions(d.sessions)
        }
        prefs.importMap(d.settings)
    }

    private suspend fun clearAll(b: BackupDao) {
        b.clearReminders(); b.clearHabitLogs(); b.clearHabits()
        b.clearTasks(); b.clearFilters(); b.clearLists()
        b.clearNotes(); b.clearSessions()
    }

    /** Borra las categorías elegidas. */
    suspend fun delete(db: AppDatabase, kinds: Set<DataKind>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val b = db.backup()
            if (DataKind.HABITS in kinds) {
                b.clearReminders(); b.clearHabitLogs(); b.clearHabits(); b.untagNotes()
            }
            if (DataKind.TASKS in kinds) { b.clearTasks(); b.clearFilters(); b.clearLists() }
            if (DataKind.NOTES in kinds) b.clearNotes()
            if (DataKind.TIMER in kinds) b.clearSessions()
        }
    }

    // ------------------------------------------------------------ cifrado

    /** Cifra con AES-256-GCM; la clave sale de la contraseña con PBKDF2 (150 000 vueltas). */
    fun encrypt(plain: String, password: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val data = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("app", APP)
            put("encrypted", true)
            put("cipher", "AES-256-GCM")
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", ITERATIONS)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("data", b64(data))
        }.toString(1)
    }

    fun decrypt(text: String, password: String): String {
        val o = JSONObject(text)
        val salt = unb64(o.getString("salt"))
        val iv = unb64(o.getString("iv"))
        val data = unb64(o.getString("data"))
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(password, salt, o.optInt("iterations", ITERATIONS)), GCMParameterSpec(128, iv))
        return try {
            String(c.doFinal(data), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: javax.crypto.BadPaddingException) {
            throw WrongPasswordException()
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    // ------------------------------------------------------------ utilidades JSON

    private fun <T> arr(items: List<T>, fill: JSONObject.(T) -> Unit) = JSONArray().apply {
        items.forEach { put(JSONObject().apply { fill(it) }) }
    }

    private fun JSONObject.putNullable(k: String, v: Any?) { put(k, v ?: JSONObject.NULL) }

    private fun JSONObject.longOrNull(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.stringOrNull(k: String): String? = if (isNull(k)) null else getString(k)

    private fun <T> list(o: JSONObject, key: String, map: (JSONObject) -> T): List<T> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { map(a.getJSONObject(it)) }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(default)
}
