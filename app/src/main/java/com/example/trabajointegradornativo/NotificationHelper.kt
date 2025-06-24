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
        private const val NOTIFICATION_ID = 1001

        @JvmStatic
        private val FRASES_MOTIVACIONALES_IDS = arrayOf(
            R.string.frase_motivacional_1,
            R.string.frase_motivacional_2,
            R.string.frase_motivacional_3,
            R.string.frase_motivacional_4,
            R.string.frase_motivacional_5,
            R.string.frase_motivacional_6,
            R.string.frase_motivacional_7,
            R.string.frase_motivacional_8,
            R.string.frase_motivacional_9,
            R.string.frase_motivacional_10
        )

        fun obtenerFraseMotivacional(context: Context): String {
            val randomIndex = Random.nextInt(FRASES_MOTIVACIONALES_IDS.size)
            return context.getString(FRASES_MOTIVACIONALES_IDS[randomIndex])
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
                context.getString(R.string.channel_id_desafio_recordatorios),
                context.getString(R.string.channel_name_desafio_recordatorios),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_description_desafio_recordatorios)
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

            android.util.Log.d(
                "NotificationHelper",
                context.getString(R.string.log_notification_scheduled, calendar.time.toString())
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "NotificationHelper",
                context.getString(R.string.log_notification_error),
                e
            )
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