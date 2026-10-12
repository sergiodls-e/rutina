package com.rutina.app.data

import com.rutina.app.domain.TaskLogic

/** Completar / reabrir tareas, cuidando las que se repiten. */
object TaskOps {

    /** Marca como hecha; si se repite, crea la siguiente ocurrencia. */
    suspend fun complete(db: AppDatabase, task: Task) {
        val now = System.currentTimeMillis()
        db.tasks().update(task.copy(done = true, doneAt = now))
        TaskLogic.nextDue(task)?.let { next ->
            db.tasks().insert(
                task.copy(id = 0, done = false, doneAt = null, dueEpochDay = next.toEpochDay(), createdAt = now)
            )
        }
    }

    /**
     * Regresa a pendiente. Si se repetía, borra la siguiente ocurrencia que se creó
     * al completarla (si no la has tocado), para no dejar duplicados.
     */
    suspend fun reopen(db: AppDatabase, task: Task) {
        val doneAt = task.doneAt
        db.tasks().update(task.copy(done = false, doneAt = null))
        val next = TaskLogic.nextDue(task) ?: return
        if (doneAt != null) db.tasks().deleteGeneratedNext(task.title, next.toEpochDay(), doneAt)
    }

    suspend fun toggle(db: AppDatabase, task: Task) = if (task.done) reopen(db, task) else complete(db, task)
}
