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

    @Query("SELECT * FROM task_lists ORDER BY sortOrder, id")
    fun observeLists(): Flow<List<TaskList>>

    @Insert
    suspend fun insertList(list: TaskList): Long

    @Update
    suspend fun updateList(list: TaskList)

    @Delete
    suspend fun deleteList(list: TaskList)

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
