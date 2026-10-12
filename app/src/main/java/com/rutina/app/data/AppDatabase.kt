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
        Note::class, TimerSession::class, TaskFilter::class, HabitReminder::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habits(): HabitDao
    abstract fun tasks(): TaskDao
    abstract fun notes(): NoteDao
    abstract fun timers(): TimerDao
    abstract fun reminders(): ReminderDao
    abstract fun backup(): BackupDao

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

        /** v4 → v5: recordatorios de hábitos. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_reminders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habitId` INTEGER NOT NULL, " +
                        "`minutes` INTEGER NOT NULL, " +
                        "`days` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_reminders_habitId` ON `habit_reminders` (`habitId`)")
            }
        }

        /** v5 → v6: tareas omitidas, repetición personalizada, series, orden manual, íconos de lista y filtro por texto. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `skipped` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatEvery` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatDays` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatUntil` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `seriesId` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `task_lists` ADD COLUMN `icon` TEXT NOT NULL DEFAULT 'list'")
                db.execSQL("ALTER TABLE `task_filters` ADD COLUMN `text` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v6 → v7: listas/prioridad de hábitos, unidades en plural, incremento, pausa y temporizador por hábito. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `listId` INTEGER")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `unitPlural` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `step` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `pausedAt` INTEGER")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `timerMode` TEXT")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `timerMinutes` INTEGER")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rutina.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
