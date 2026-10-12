package com.rutina.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.Backup
import com.rutina.app.data.BackupData
import com.rutina.app.data.DataKind
import com.rutina.app.data.Habit
import com.rutina.app.data.InvalidBackupException
import com.rutina.app.data.Prefs
import com.rutina.app.data.WrongPasswordException
import com.rutina.app.domain.Achievements
import com.rutina.app.domain.AchievementsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Paso del flujo de restaurar un respaldo. */
sealed interface RestoreStep {
    data object Idle : RestoreStep
    data object Working : RestoreStep
    /** El archivo está cifrado: pedir contraseña. */
    data class NeedsPassword(val text: String, val wrong: Boolean = false) : RestoreStep
    /** Listo para confirmar. */
    data class Ready(val data: BackupData) : RestoreStep
    data class Error(val message: String) : RestoreStep
    data object Done : RestoreStep
}

class SettingsViewModel(private val db: AppDatabase, val prefs: Prefs) : ViewModel() {

    val archivedCount: StateFlow<Int> = db.habits().observeArchived().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _busy = MutableStateFlow(false)
    /** Cifrando/escribiendo un respaldo (el cifrado tarda un par de segundos). */
    val busy: StateFlow<Boolean> = _busy

    private val _restore = MutableStateFlow<RestoreStep>(RestoreStep.Idle)
    val restore: StateFlow<RestoreStep> = _restore

    /** Escribe el respaldo completo en el archivo elegido (puede ser una carpeta de Drive). */
    fun exportTo(resolver: ContentResolver, uri: Uri, password: String?, onDone: (String?) -> Unit) = viewModelScope.launch {
        _busy.value = true
        val error = withContext(Dispatchers.Default) {
            runCatching {
                val json = Backup.toJson(Backup.collect(db, prefs))
                val text = if (password.isNullOrEmpty()) json else Backup.encrypt(json, password)
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("sin acceso")
                }
            }.exceptionOrNull()
        }
        _busy.value = false
        if (error == null) prefs.markBackup()
        onDone(if (error == null) null else "No se pudo guardar el respaldo.")
    }

    /** Prepara el respaldo como copia temporal compartible (para mandarlo directo a Drive). */
    fun shareBackup(ctx: android.content.Context, password: String?, onReady: (Uri?) -> Unit) = viewModelScope.launch {
        _busy.value = true
        val uri = withContext(Dispatchers.Default) {
            runCatching {
                val json = Backup.toJson(Backup.collect(db, prefs))
                val text = if (password.isNullOrEmpty()) json else Backup.encrypt(json, password)
                val name = "rutina-respaldo-${java.time.LocalDate.now()}${if (password.isNullOrEmpty()) "" else "-cifrado"}.json"
                withContext(Dispatchers.IO) { com.rutina.app.backup.AutoBackup.shareCopy(ctx.applicationContext, text, name) }
            }.getOrNull()
        }
        _busy.value = false
        if (uri != null) prefs.markBackup()
        onReady(uri)
    }

    fun readBackup(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _restore.value = RestoreStep.Working
        _restore.value = withContext(Dispatchers.IO) {
            runCatching {
                val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("sin acceso")
                if (Backup.isEncrypted(text)) {
                    // Primero intenta con tu contraseña de respaldo guardada.
                    val saved = prefs.backupPassword()
                    val plain = saved?.let { pw -> runCatching { Backup.decrypt(text, pw) }.getOrNull() }
                    if (plain != null) RestoreStep.Ready(Backup.parse(plain)) else RestoreStep.NeedsPassword(text)
                } else RestoreStep.Ready(Backup.parse(text))
            }.getOrElse { e ->
                RestoreStep.Error((e as? InvalidBackupException)?.message ?: "No se pudo leer el archivo.")
            }
        }
    }

    fun unlock(text: String, password: String) = viewModelScope.launch {
        _restore.value = RestoreStep.Working
        _restore.value = withContext(Dispatchers.Default) {
            try {
                RestoreStep.Ready(Backup.parse(Backup.decrypt(text, password)))
            } catch (e: WrongPasswordException) {
                RestoreStep.NeedsPassword(text, wrong = true)
            } catch (e: InvalidBackupException) {
                RestoreStep.Error(e.message ?: "Respaldo inválido.")
            } catch (e: Exception) {
                RestoreStep.Error("No se pudo descifrar el archivo.")
            }
        }
    }

    fun confirmRestore(data: BackupData) = viewModelScope.launch {
        _restore.value = RestoreStep.Working
        _restore.value = runCatching { Backup.restore(db, prefs, data) }
            .fold({ RestoreStep.Done }, { RestoreStep.Error("No se pudo restaurar. Tus datos actuales no cambiaron.") })
    }

    fun dismissRestore() { _restore.value = RestoreStep.Idle }

    // ------------------------------------------------------------ respaldo automático

    /** Guarda la carpeta elegida y conserva el permiso de escritura aunque reinicies el teléfono. */
    fun setFolder(ctx: android.content.Context, uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, flags) }
        prefs.autoBackupFolder.value?.let { old ->
            if (old != uri.toString()) runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        prefs.setAutoBackupFolder(uri.toString())
        com.rutina.app.backup.AutoBackup.schedule(ctx)
    }

    fun setAutoDays(ctx: android.content.Context, days: Int) {
        prefs.setAutoBackupDays(days)
        com.rutina.app.backup.AutoBackup.schedule(ctx)
    }

    /** Respaldo automático inmediato (para probar la carpeta). */
    fun backupNow(ctx: android.content.Context, onDone: (com.rutina.app.backup.AutoBackup.Outcome) -> Unit) = viewModelScope.launch {
        _busy.value = true
        val r = com.rutina.app.backup.AutoBackup.run(ctx.applicationContext)
        _busy.value = false
        onDone(r)
    }

    fun deleteData(kinds: Set<DataKind>, onDone: () -> Unit) = viewModelScope.launch {
        Backup.delete(db, kinds)
        onDone()
    }
}

class ArchiveViewModel(private val db: AppDatabase) : ViewModel() {
    val habits: StateFlow<List<Habit>?> = db.habits().observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun restore(h: Habit) = viewModelScope.launch { db.habits().update(h.copy(archived = false)) }

    fun delete(h: Habit) = viewModelScope.launch {
        db.reminders().deleteFor(h.id)
        db.habits().deleteLogsFor(h.id)
        db.habits().delete(h)
    }
}

class AchievementsViewModel(db: AppDatabase, prefs: Prefs) : ViewModel() {
    private val sources = combine(
        db.habits().observeAll(), db.habits().observeAllLogs(), db.notes().observeAll(), db.tasks().observeAll(), db.timers().observeAll(),
    ) { h, l, n, t, s -> Achievements.compute(h, l, n, t, s, prefs.weekStart.value) }

    val state: StateFlow<AchievementsData?> = sources.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
