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

    fun programarNotificacionDiaria(hour: Int, minute: Int) {

        with(sharedPreferences.edit()) {
            putBoolean("notificaciones_habilitadas", true)
            putInt("hora_notificacion", hour)
            putInt("minuto_notificacion", minute)
            apply()
        }

        cancelarNotificacionDiaria()

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

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
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

        with(sharedPreferences.edit()) {
            putBoolean("notificaciones_habilitadas", false)
            apply()
        }
    }
}