package com.example.trabajointegradornativo

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import java.util.*
import kotlin.random.Random

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "desafio_recordatorios"
        private const val CHANNEL_NAME = "Recordatorios de Desafío"
        private const val CHANNEL_DESCRIPTION = "Notificaciones para recordar completar hábitos diarios"
        private const val NOTIFICATION_ID = 1001

        // Frases motivacionales
        private val FRASES_MOTIVACIONALES = arrayOf(
            "¡Cada día es una nueva oportunidad!",
            "¡Tú puedes lograrlo!",
            "¡Un paso más hacia tu objetivo!",
            "¡La constancia es la clave del éxito!",
            "¡Hoy es el día perfecto para brillar!",
            "¡Tus hábitos definen tu futuro!",
            "¡Cada pequeño progreso cuenta!",
            "¡Mantén el ritmo, vas por buen camino!",
            "¡La disciplina te llevará lejos!",
            "¡Hoy también puedes ser extraordinario!"
        )

        /**
         * Obtiene una frase motivacional aleatoria
         */
        fun obtenerFraseMotivacional(): String {
            return FRASES_MOTIVACIONALES[Random.nextInt(FRASES_MOTIVACIONALES.size)]
        }
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("desafio_prefs", Context.MODE_PRIVATE)

    init {
        createNotificationChannel()
    }

    /**
     * Crea el canal de notificaciones (necesario para Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Programa la notificación diaria
     * @param hour Hora del día (0-23)
     * @param minute Minuto de la hora (0-59)
     */
    fun programarNotificacionDiaria(hour: Int, minute: Int) {
        // Guardar la configuración
        with(sharedPreferences.edit()) {
            putBoolean("notificaciones_habilitadas", true)
            putInt("hora_notificacion", hour)
            putInt("minuto_notificacion", minute)
            apply()
        }

        // Cancelar alarma anterior si existe
        cancelarNotificacionDiaria()

        // Programar nueva alarma
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "RECORDATORIO_HABITOS"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Configurar el tiempo para la notificación
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Si la hora ya pasó hoy, programar para mañana
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            // Programar alarma exacta
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ - verificar permisos
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        // Usar alarma inexacta si no hay permisos
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            android.util.Log.d("NotificationHelper", "Notificación programada para: ${calendar.time}")
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error al programar notificación", e)
            throw e
        }
    }

    /**
     * Cancela la notificación diaria programada
     */
    fun cancelarNotificacionDiaria() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "RECORDATORIO_HABITOS"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        // Actualizar SharedPreferences
        with(sharedPreferences.edit()) {
            putBoolean("notificaciones_habilitadas", false)
            apply()
        }
    }

//    /**
//     * Verifica si las notificaciones están habilitadas
//     */
//    fun sonNotificacionesHabilitadas(): Boolean {
//        return sharedPreferences.getBoolean("notificaciones_habilitadas", false)
//    }
//
//    /**
//     * Obtiene la hora configurada para las notificaciones
//     */
//    fun getHoraNotificacion(): Int {
//        return sharedPreferences.getInt("hora_notificacion", 9) // Por defecto 9 AM
//    }
//
//    /**
//     * Obtiene el minuto configurado para las notificaciones
//     */
//    fun getMinutoNotificacion(): Int {
//        return sharedPreferences.getInt("minuto_notificacion", 0) // Por defecto :00
//    }
}