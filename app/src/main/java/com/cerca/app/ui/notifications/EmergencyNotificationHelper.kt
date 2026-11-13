package com.cerca.app.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cerca.app.MainActivity

object EmergencyNotificationHelper {

    private const val CHANNEL_SOS    = "sos_sent"
    private const val CHANNEL_FAMILY = "family_emergency"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, "Mi alerta SOS", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Confirmación cuando envías una alerta de emergencia"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FAMILY, "Alertas de familiares", NotificationManager.IMPORTANCE_MAX).apply {
                description = "Notificaciones cuando un familiar envía una alerta SOS"
                enableVibration(true)
                enableLights(true)
            }
        )
    }

    /** Notificación local que confirma que tu propio SOS fue enviado */
    fun showSOSSent(context: Context, hasLocation: Boolean, lat: Double, lng: Double) {
        createChannels(context)

        val body = if (hasLocation)
            "Tu alerta fue enviada con tu ubicación a tus familiares."
        else
            "Tu alerta fue enviada a tus familiares (sin ubicación)."

        val openIntent = PendingIntent.getActivity(
            context, 1001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SOS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🆘 Alerta SOS enviada")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)

        // Si hay ubicación, agregar acción para abrir mapa
        if (hasLocation) {
            val mapIntent = PendingIntent.getActivity(
                context, 1002,
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(Mi ubicación)")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_mylocation, "Ver en mapa", mapIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(1001, builder.build())
        } catch (_: SecurityException) {}
    }

    /** Notificación para familiares cuando alguien de la familia envía un SOS */
    fun showFamilyAlert(
        context: Context,
        fromName: String,
        message: String,
        hasLocation: Boolean,
        lat: Double,
        lng: Double
    ) {
        createChannels(context)
        val notifId = System.currentTimeMillis().toInt()

        val title = "🆘 $fromName necesita ayuda"
        val body  = if (hasLocation) "$message\nToca para ver su ubicación." else message

        val openIntent = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_FAMILY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))

        if (hasLocation) {
            val mapIntent = PendingIntent.getActivity(
                context, notifId + 1,
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($fromName)")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_mylocation, "Ver ubicación", mapIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {}
    }
}
