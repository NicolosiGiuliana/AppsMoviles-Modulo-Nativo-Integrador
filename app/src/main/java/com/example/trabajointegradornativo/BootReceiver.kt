package com.example.trabajointegradornativo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    // Recibe eventos del sistema al actualizar la app y reprograma las notificaciones si es necesario.
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (Intent.ACTION_BOOT_COMPLETED == action ||
            Intent.ACTION_MY_PACKAGE_REPLACED == action ||
            Intent.ACTION_PACKAGE_REPLACED == action
        ) {

            reprogramarNotificaciones(context)
        }
    }

    // Restaura la programación de notificaciones si estaban activas.
    private fun reprogramarNotificaciones(context: Context) {
        val prefs = context.getSharedPreferences("desafio_prefs", Context.MODE_PRIVATE)

        val notificacionesHabilitadas = prefs.getBoolean("notificaciones_habilitadas", false)

        if (notificacionesHabilitadas) {
            val hora = prefs.getInt("hora_notificacion", 9)
            val minuto = prefs.getInt("minuto_notificacion", 0)

            val helper = NotificationHelper(context)
            helper.programarNotificacionDiaria(hora, minuto)
        }
    }
}