package com.rutina.app.common

import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * En la pantalla bloqueada solo se ve "Rutina · [texto genérico]".
 * El nombre del hábito, la tarea o la nota aparece hasta que desbloqueas.
 */
fun NotificationCompat.Builder.privateOnLockScreen(
    ctx: Context, channel: String, icon: Int, genericText: String,
): NotificationCompat.Builder = setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
    NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(icon)
        .setContentTitle("Rutina")
        .setContentText(genericText)
        .build()
)
