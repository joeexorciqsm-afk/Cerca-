package com.cerca.app.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver que dispara la notificación cuando el AlarmManager lo activa.
 */
class MedicationReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MED_NAME = "extra_med_name"
        const val EXTRA_MED_DOSE = "extra_med_dose"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_MED_ID   = "extra_med_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val name    = intent.getStringExtra(EXTRA_MED_NAME) ?: "Medicamento"
        val dose    = intent.getStringExtra(EXTRA_MED_DOSE) ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)

        MedicationNotificationHelper.createChannel(context)
        MedicationNotificationHelper.showNotification(context, name, dose, notifId)
    }
}
