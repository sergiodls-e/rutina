package com.rutina.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE archived = 1 ORDER BY name")
    fun observeArchived(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): Habit?

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observe(id: Long): Flow<Habit?>

    @Insert
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    @Update
    suspend fun updateAll(habits: List<Habit>)

    @Delete
    suspend fun delete(habit: Habit)

    @Query("SELECT * FROM habit_logs ORDER BY timestamp")
    fun observeAllLogs(): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE timestamp >= :from AND timestamp < :to")
    fun observeLogsBetween(from: Long, to: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY timestamp DESC")
    fun observeLogsFor(habitId: Long): Flow<List<HabitLog>>

    @Insert
    suspend fun insertLog(log: HabitLog): Long

    @Update
    suspend fun updateLog(log: HabitLog)

    @Delete
    suspend fun deleteLog(log: HabitLog)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsFor(habitId: Long)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND timestamp >= :from AND timestamp < :to")
    suspend fun deleteLogsBetween(habitId: Long, from: Long, to: Long)

    @Query("SELECT MAX(timestamp) FROM habit_logs WHERE habitId = :habitId")
    suspend fun lastLogTime(habitId: Long): Long?

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId")
    suspend fun countLogs(habitId: Long): Int

    @Query(
        "DELETE FROM habit_logs WHERE id = (SELECT id FROM habit_logs " +
            "WHERE habitId = :habitId AND timestamp >= :from AND timestamp < :to " +
            "ORDER BY timestamp DESC LIMIT 1)"
    )
    suspend fun deleteLatestLog(habitId: Long, from: Long, to: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done, dueEpochDay IS NULL, dueEpochDay, dueMinutes, priority DESC, id")
    fun observeAll(): Flow<List<Task>>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    /** Todas las ocurrencias de una tarea que se repite. */
    @Query("SELECT * FROM tasks WHERE seriesId = :series OR id = :series ORDER BY dueEpochDay DESC, id DESC")
    fun observeSeries(series: Long): Flow<List<Task>>

    @Update
    suspend fun updateAll(tasks: List<Task>)

    @Delete
    suspend fun deleteAll(tasks: List<Task>)

    /** Borra la ocurrencia que se generó al completar una tarea repetida (si sigue pendiente). */
    @Query("DELETE FROM tasks WHERE title = :title AND dueEpochDay = :due AND done = 0 AND createdAt >= :createdAfter")
    suspend fun deleteGeneratedNext(title: String, due: Long, createdAfter: Long)

    @Query("SELECT * FROM task_lists ORDER BY sortOrder, id")
    fun observeLists(): Flow<List<TaskList>>

    @Insert
    suspend fun insertList(list: TaskList): Long

    @Update
    suspend fun updateList(list: TaskList)

    @Delete
    suspend fun deleteList(list: TaskList)

    /** Los hábitos de una lista borrada quedan sin lista. */
    @Query("UPDATE habits SET listId = NULL WHERE listId = :listId")
    suspend fun detachHabits(listId: Long)

    @Query("SELECT * FROM task_filters ORDER BY sortOrder, id")
    fun observeFilters(): Flow<List<TaskFilter>>

    @Insert
    suspend fun insertFilter(filter: TaskFilter): Long

    @Update
    suspend fun updateFilter(filter: TaskFilter)

    @Delete
    suspend fun deleteFilter(filter: TaskFilter)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}

@Dao
interface TimerDao {
    @Insert
    suspend fun insert(session: TimerSession): Long

    @Query("SELECT * FROM timer_sessions ORDER BY start DESC")
    fun observeAll(): Flow<List<TimerSession>>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM habit_reminders ORDER BY minutes")
    fun observeAll(): Flow<List<HabitReminder>>

    @Query("SELECT * FROM habit_reminders WHERE habitId = :habitId ORDER BY minutes")
    suspend fun forHabit(habitId: Long): List<HabitReminder>

    @Query("DELETE FROM habit_reminders WHERE habitId = :habitId")
    suspend fun deleteFor(habitId: Long)

    @Insert
    suspend fun insertAll(list: List<HabitReminder>)
}

/** Lectura y escritura en bloque para respaldos y para borrar datos. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM habits") suspend fun habits(): List<Habit>
    @Query("SELECT * FROM habit_logs") suspend fun habitLogs(): List<HabitLog>
    @Query("SELECT * FROM habit_reminders") suspend fun reminders(): List<HabitReminder>
    @Query("SELECT * FROM task_lists") suspend fun lists(): List<TaskList>
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<Task>
    @Query("SELECT * FROM task_filters") suspend fun filters(): List<TaskFilter>
    @Query("SELECT * FROM notes") suspend fun notes(): List<Note>
    @Query("SELECT * FROM timer_sessions") suspend fun sessions(): List<TimerSession>

    @Insert suspend fun insertHabits(l: List<Habit>)
    @Insert suspend fun insertHabitLogs(l: List<HabitLog>)
    @Insert suspend fun insertReminders(l: List<HabitReminder>)
    @Insert suspend fun insertLists(l: List<TaskList>)
    @Insert suspend fun insertTasks(l: List<Task>)
    @Insert suspend fun insertFilters(l: List<TaskFilter>)
    @Insert suspend fun insertNotes(l: List<Note>)
    @Insert suspend fun insertSessions(l: List<TimerSession>)

    @Query("DELETE FROM habit_reminders") suspend fun clearReminders()
    @Query("DELETE FROM habit_logs") suspend fun clearHabitLogs()
    @Query("DELETE FROM habits") suspend fun clearHabits()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM task_filters") suspend fun clearFilters()
    @Query("DELETE FROM task_lists") suspend fun clearLists()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM timer_sessions") suspend fun clearSessions()
    /** Las notas de hábitos borrados quedan como notas sueltas. */
    @Query("UPDATE notes SET habitId = NULL") suspend fun untagNotes()
}
