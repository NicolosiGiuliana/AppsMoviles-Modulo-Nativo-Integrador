package com.example.trabajointegradornativo

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.trabajointegradornativo.MainActivity.App.Companion.context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "desafio_recordatorios"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "RECORDATORIO_HABITOS") {
            verificarHabitosYNotificar(context)
        }
    }

    private fun verificarHabitosYNotificar(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return

        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        obtenerHabitosDelDia(userId, fechaHoy) { totalHabitos, habitosCompletados ->
            if (totalHabitos > 0) {
                val habitosFaltantes = totalHabitos - habitosCompletados
                if (habitosFaltantes > 0) {
                    val notificacion = generarNotificacion(habitosFaltantes)
                    enviarNotificacion(context, notificacion.titulo, notificacion.mensaje)
                }
            }
        }
    }

    private fun obtenerHabitosDelDia(
        userId: String,
        fechaHoy: String,
        callback: (totalHabitos: Int, habitosCompletados: Int) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()

        // Obtener desafíos activos
        db.collection("usuarios").document(userId).collection("desafios")
            .whereEqualTo("estado", "activo")
            .get()
            .addOnSuccessListener { desafiosSnapshot ->

                if (desafiosSnapshot.isEmpty) {
                    Log.d(TAG, "No hay desafíos activos")
                    callback(0, 0)
                    return@addOnSuccessListener
                }

                var totalHabitos = 0
                var habitosCompletados = 0
                var desafiosProcesados = 0
                val totalDesafios = desafiosSnapshot.size()

                Log.d(TAG, "Desafíos activos encontrados: $totalDesafios")

                for (desafioDoc in desafiosSnapshot) {
                    val desafioId = desafioDoc.id

                    // Obtener el progreso del día específico en la subcolección 'dias'
                    db.collection("usuarios").document(userId)
                        .collection("desafios").document(desafioId)
                        .collection("dias")
                        .whereEqualTo("fechaRealizacion", fechaHoy)
                        .get()
                        .addOnSuccessListener { diasSnapshot ->

                            if (diasSnapshot.isEmpty) {
                                // Si no hay progreso para hoy, contar todos los hábitos como pendientes
                                val habitosDelDesafio = (desafioDoc.get("habitos") as? List<*>)?.size ?: 0
                                totalHabitos += habitosDelDesafio
                                Log.d(TAG, "Desafío $desafioId: Sin progreso hoy, $habitosDelDesafio hábitos pendientes")
                            } else {
                                // Procesar el progreso del día
                                for (diaDoc in diasSnapshot) {
                                    val habitosArray = diaDoc.get("habitos") as? List<Map<String, Any>>

                                    if (habitosArray != null) {
                                        totalHabitos += habitosArray.size

                                        val completadosEnEsteDia = habitosArray.count { habito ->
                                            habito["completado"] as? Boolean == true
                                        }

                                        habitosCompletados += completadosEnEsteDia

                                        Log.d(TAG, "Desafío $desafioId - Día ${diaDoc.id}: ${habitosArray.size} hábitos, $completadosEnEsteDia completados")
                                    }
                                }
                            }

                            desafiosProcesados++

                            // Cuando hayamos procesado todos los desafíos, ejecutar callback
                            if (desafiosProcesados == totalDesafios) {
                                Log.d(TAG, "Resumen final - Total: $totalHabitos, Completados: $habitosCompletados")
                                callback(totalHabitos, habitosCompletados)
                            }
                        }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "Error al obtener días del desafío $desafioId", error)
                            desafiosProcesados++

                            if (desafiosProcesados == totalDesafios) {
                                Log.d(TAG, "Resumen final (con errores) - Total: $totalHabitos, Completados: $habitosCompletados")
                                callback(totalHabitos, habitosCompletados)
                            }
                        }
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Error al obtener desafíos", error)
                enviarNotificacionGenerica(context)
            }
    }

    private fun generarNotificacion(habitosFaltantes: Int): NotificacionData {
        return when (habitosFaltantes) {
            1 -> NotificacionData(
                titulo = "¡Te falta 1 hábito!",
                mensaje = "Solo un pequeño paso más para completar tu día. ${NotificationHelper.obtenerFraseMotivacional()}"
            )
            in 2..3 -> NotificacionData(
                titulo = "¡Te faltan $habitosFaltantes hábitos!",
                mensaje = "Ya vas por buen camino, continúa así. ${NotificationHelper.obtenerFraseMotivacional()}"
            )
            in 4..6 -> NotificacionData(
                titulo = "¡Te faltan $habitosFaltantes hábitos!",
                mensaje = "Es momento de retomar el ritmo. ${NotificationHelper.obtenerFraseMotivacional()}"
            )
            else -> NotificacionData(
                titulo = "¡Te faltan $habitosFaltantes hábitos!",
                mensaje = "Cada gran logro comienza con la decisión de intentarlo. ${NotificationHelper.obtenerFraseMotivacional()}"
            )
        }
    }

    private fun enviarNotificacionGenerica(context: Context) {
        enviarNotificacion(
            context,
            "¡Hora de revisar tus hábitos!",
            NotificationHelper.obtenerFraseMotivacional()
        )
    }

    private fun enviarNotificacion(context: Context, titulo: String, mensaje: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // CAMBIAR: Dirigir directamente a ItemDetailHostActivity
        val intent = Intent(context, ItemDetailHostActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "today_fragment")
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))

        notificationManager.notify(NOTIFICATION_ID, builder.build())
        reprogramarNotificacion(context)
    }

    private fun reprogramarNotificacion(context: Context) {
        val prefs = context.getSharedPreferences("desafio_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notificaciones_habilitadas", false)) return

        val hora = prefs.getInt("hora_notificacion", 9)
        val minuto = prefs.getInt("minuto_notificacion", 0)

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
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hora)
            set(Calendar.MINUTE, minuto)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Notificación reprogramada para: ${calendar.time}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al reprogramar notificación", e)
        }
    }

    data class NotificacionData(
        val titulo: String,
        val mensaje: String
    )
}