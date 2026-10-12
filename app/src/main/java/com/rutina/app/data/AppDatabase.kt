package com.rutina.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Habit::class, HabitLog::class, Task::class, TaskList::class,
        Note::class, TimerSession::class, TaskFilter::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habits(): HabitDao
    abstract fun tasks(): TaskDao
    abstract fun notes(): NoteDao
    abstract fun timers(): TimerDao

    companion object {
        /** v1 → v2: agrega la tabla de filtros de tareas. Conserva todos tus datos. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_filters` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`dateRange` TEXT NOT NULL, " +
                        "`minPriority` INTEGER NOT NULL, " +
                        "`listId` INTEGER, " +
                        "`includeDone` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
            }
        }

        /** v2 → v3: duración de las tareas para el calendario. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `durationMin` INTEGER NOT NULL DEFAULT 30")
            }
        }

        /**
         * v3 → v4: limpieza. Si un hábito se convirtió a "Dejar", sus registros viejos de
         * Recuento se contaban como recaídas. Las recaídas reales siempre llevan nota.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM `habit_logs` WHERE `note` IS NULL AND `habitId` IN " +
                        "(SELECT `id` FROM `habits` WHERE `type` = 'QUIT')"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rutina.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
