package com.cerca.app.ui.notifications

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cerca.app.viewmodel.MedicationViewModel

/**
 * Reprograma todas las alarmas activas cuando el dispositivo se reinicia,
 * ya que Android borra las alarmas pendientes al apagar el teléfono.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "Boot detectado — reprogramando alarmas de medicamentos")
        try {
            val vm = MedicationViewModel(context.applicationContext as Application)
            vm.rescheduleAll()
        } catch (e: Exception) {
            Log.w("BootReceiver", "Error al reprogramar: ${e.message}")
        }
    }
}
