package com.rutina.app.data

import com.rutina.app.domain.TaskLogic
import java.time.LocalDate

/** Completar / omitir / reabrir tareas, cuidando las que se repiten. */
object TaskOps {

    /** Marca como hecha; si se repite, crea la siguiente ocurrencia. */
    suspend fun complete(db: AppDatabase, task: Task) = close(db, task, skipped = false)

    /** "No lo haré": se cierra sin contar como completada; si se repite, sigue con la siguiente. */
    suspend fun skip(db: AppDatabase, task: Task) = close(db, task, skipped = true)

    private suspend fun close(db: AppDatabase, task: Task, skipped: Boolean) {
        val now = System.currentTimeMillis()
        val series = task.series
        db.tasks().update(task.copy(done = true, skipped = skipped, doneAt = now, seriesId = series))
        TaskLogic.nextDue(task)?.let { next ->
            db.tasks().insert(
                task.copy(
                    id = 0, done = false, skipped = false, doneAt = null, dueEpochDay = next.toEpochDay(),
                    createdAt = now, seriesId = series,
                )
            )
        }
    }

    /**
     * Regresa a pendiente. Si se repetía, borra la siguiente ocurrencia que se creó
     * al cerrarla (si no la has tocado), para no dejar duplicados.
     */
    suspend fun reopen(db: AppDatabase, task: Task) {
        val doneAt = task.doneAt
        db.tasks().update(task.copy(done = false, skipped = false, doneAt = null))
        val next = TaskLogic.nextDue(task) ?: return
        if (doneAt != null) db.tasks().deleteGeneratedNext(task.title, next.toEpochDay(), doneAt)
    }

    suspend fun toggle(db: AppDatabase, task: Task) = if (task.done) reopen(db, task) else complete(db, task)

    /** Mueve tareas a otro día conservando su hora. */
    suspend fun moveTo(db: AppDatabase, tasks: List<Task>, day: LocalDate?) {
        tasks.forEach { t ->
            db.tasks().update(
                if (day == null) t.copy(dueEpochDay = null, dueMinutes = null, notify = false, repeat = RepeatRule.NEVER)
                else t.copy(dueEpochDay = day.toEpochDay())
            )
        }
    }
}
