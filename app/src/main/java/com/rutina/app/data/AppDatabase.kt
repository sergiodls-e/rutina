package com.rutina.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Habit::class, HabitLog::class, Task::class, TaskList::class, Note::class, TimerSession::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habits(): HabitDao
    abstract fun tasks(): TaskDao
    abstract fun notes(): NoteDao
    abstract fun timers(): TimerDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rutina.db").build()
    }
}
