package com.rutina.app.backup

import com.rutina.app.common.privateOnLockScreen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rutina.app.R
import com.rutina.app.RutinaApp
import com.rutina.app.data.Backup
import com.rutina.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda la contraseña de respaldo cifrada con una llave del Keystore de Android
 * (vive en el hardware de seguridad del teléfono y no se puede extraer).
 */
object SecretStore {
    private const val ALIAS = "rutina_backup_password"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String? = runCatching {
        val (iv, ct) = stored.split(":").let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()
}

/** Opciones de frecuencia del respaldo automático (días; 0 = apagado). */
val AutoBackupOptions = listOf(0 to "Desactivado", 1 to "Cada día", 7 to "Cada semana", 15 to "Cada 15 días", 30 to "Cada 30 días")

fun autoBackupLabel(days: Int) = AutoBackupOptions.firstOrNull { it.first == days }?.second ?: "Cada $days días"

/** Respaldo automático a una carpeta elegida por ti, cifrado con tu contraseña. */
object AutoBackup {
    private const val WORK = "auto_backup"
    private const val WORK_NOW = "auto_backup_now"
    private const val CHANNEL = "backup_v2"
    private const val KEEP = 5
    const val DRIVE = "com.google.android.apps.docs"

    sealed interface Outcome {
        /** [shareUri]: copia temporal que se puede mandar a Drive. */
        data class Ok(val uri: Uri, val name: String, val shareUri: Uri) : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Programa (o cancela) la revisión diaria según los ajustes. */
    fun schedule(ctx: Context) {
        val prefs = (ctx.applicationContext as RutinaApp).prefs
        val wm = WorkManager.getInstance(ctx)
        if (prefs.autoBackupDays.value > 0 && prefs.autoBackupFolder.value != null) {
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    /** Al abrir la app: si ya toca respaldar y la tarea diaria no ha corrido, hazlo ahora. */
    fun catchUp(ctx: Context) {
        val prefs = (ctx.applicationContext as RutinaApp).prefs
        if (!isDue(prefs)) return
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            WORK_NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<AutoBackupWorker>().build(),
        )
    }

    fun isDue(prefs: Prefs, now: Long = System.currentTimeMillis()): Boolean {
        val days = prefs.autoBackupDays.value
        if (days <= 0 || prefs.autoBackupFolder.value == null) return false
        // Una hora de margen para que "cada día" no se salte por minutos.
        return now - prefs.lastBackup.value >= days * 86_400_000L - 3_600_000L
    }

    /** Nombre legible de la carpeta elegida. */
    fun folderName(ctx: Context, tree: String?): String? = tree?.let {
        runCatching {
            val uri = Uri.parse(it)
            val doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "Carpeta elegida"
    }

    /** Crea el respaldo en la carpeta, borra los automáticos más viejos y avisa. */
    suspend fun run(ctx: Context): Outcome = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext as RutinaApp
        val prefs = app.prefs
        val tree = prefs.autoBackupFolder.value?.let(Uri::parse)
            ?: return@withContext Outcome.Failed("Elige una carpeta para los respaldos automáticos.")
        val canWrite = ctx.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!canWrite) return@withContext Outcome.Failed("Rutina perdió acceso a la carpeta de respaldos. Elígela de nuevo en Ajustes.")
        val password = prefs.backupPassword()
        runCatching {
            val json = Backup.toJson(Backup.collect(app.db, prefs))
            val text = if (password.isNullOrEmpty()) json else Backup.encrypt(json, password)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "rutina-auto-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".json"
            val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/json", name)
                ?: error("no se pudo crear")
            ctx.contentResolver.openOutputStream(doc)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("sin acceso")
            prune(ctx, tree)
            prefs.markBackup()
            Outcome.Ok(doc, name, shareCopy(ctx, text, name))
        }.getOrElse { Outcome.Failed("No se pudo guardar el respaldo automático.") }
    }

    private fun prune(ctx: Context, tree: Uri) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>() // nombre, id
        ctx.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                if (n.startsWith("rutina-auto-")) found += n to c.getString(1)
            }
        }
        found.sortedByDescending { it.first }.drop(KEEP).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    /**
     * Guarda una copia temporal en la caché privada y devuelve un enlace (FileProvider) para compartirla.
     * Solo la app que elijas (Drive) puede leerla, y solo cuando tú la mandas.
     */
    fun shareCopy(ctx: Context, text: String, name: String): Uri {
        val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = java.io.File(dir, name)
        f.writeText(text, Charsets.UTF_8)
        return androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    /**
     * Borra las copias temporales que se usaron para compartir.
     * Las cifradas duran 24 h (para que el botón del aviso siga sirviendo); las demás, 10 minutos.
     */
    fun cleanShareCache(ctx: Context, now: Long = System.currentTimeMillis()) {
        val dir = java.io.File(ctx.cacheDir, "share")
        dir.listFiles()?.forEach { f ->
            val encrypted = f.name.endsWith(".json") && runCatching { Backup.isEncrypted(f.readText()) }.getOrDefault(false)
            val maxAge = if (encrypted) 24 * 3_600_000L else 10 * 60_000L
            if (now - f.lastModified() > maxAge) runCatching { f.delete() }
        }
    }

    // ------------------------------------------------------------ avisos

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Respaldos", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null) }
            )
        }
    }

    private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Intent para mandar el archivo a Drive (o a la app que elijas si no tienes Drive). */
    fun shareIntent(ctx: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("respaldo", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val drive = Intent(send).setPackage(DRIVE)
        val hasDrive = ctx.packageManager.queryIntentActivities(drive, 0).isNotEmpty()
        return if (hasDrive) drive else Intent.createChooser(send, "Guardar respaldo en…").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun notify(ctx: Context, outcome: Outcome) {
        if (!canNotify(ctx)) return
        channel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val b = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_check).setAutoCancel(true).setSilent(true)
            .privateOnLockScreen(ctx, CHANNEL, R.drawable.ic_stat_check, "Aviso de respaldo")
        when (outcome) {
            is Outcome.Ok -> {
                val share = PendingIntent.getActivity(
                    ctx, 41, shareIntent(ctx, outcome.shareUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                b.setContentTitle("Respaldo listo")
                    .setContentText("Toca para subirlo a Drive · ${outcome.name}")
                    .setContentIntent(share)
                    .addAction(0, "Subir a Drive", share)
            }
            is Outcome.Failed -> b.setContentTitle("Respaldo automático pendiente").setContentText(outcome.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(outcome.message))
        }
        runCatching { nm.notify(4100, b.build()) }
    }
}

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = (applicationContext as RutinaApp).prefs
        if (!AutoBackup.isDue(prefs)) return Result.success()
        val outcome = AutoBackup.run(applicationContext)
        AutoBackup.notify(applicationContext, outcome)
        return Result.success()
    }
}
