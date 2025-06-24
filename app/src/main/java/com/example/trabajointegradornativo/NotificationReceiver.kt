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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class NotificationReceiver : BroadcastReceiver() {

    companion object {
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

        obtenerHabitosDelDia(context, userId, fechaHoy) { totalHabitos, habitosCompletados ->
            if (totalHabitos > 0) {
                val habitosFaltantes = totalHabitos - habitosCompletados
                if (habitosFaltantes > 0) {
                    val notificacion = generarNotificacion(context, habitosFaltantes)
                    enviarNotificacion(context, notificacion.titulo, notificacion.mensaje)
                }
            }
        }
    }

    private fun obtenerHabitosDelDia(
        context: Context,
        userId: String,
        fechaHoy: String,
        callback: (totalHabitos: Int, habitosCompletados: Int) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()


        db.collection("usuarios")
            .document(userId)
            .collection("desafios")
            .whereEqualTo("estado", "activo")
            .get()
            .addOnSuccessListener { desafiosSnapshot ->

                if (desafiosSnapshot.isEmpty) {
                    callback(0, 0)
                    return@addOnSuccessListener
                }

                var totalHabitos = 0
                var habitosCompletados = 0
                var desafiosProcesados = 0
                val totalDesafios = desafiosSnapshot.size()

                for (desafioDoc in desafiosSnapshot) {
                    val desafioId = desafioDoc.id


                    db.collection("usuarios")
                        .document(userId)
                        .collection("desafios")
                        .document(desafioId)
                        .collection("dias")
                        .whereEqualTo("fechaRealizacion", fechaHoy)
                        .get()
                        .addOnSuccessListener { diasSnapshot ->

                            if (diasSnapshot.isEmpty) {

                                val habitosDelDesafio =
                                    (desafioDoc.get("habitos") as? List<*>)?.size ?: 0
                                totalHabitos += habitosDelDesafio
                            } else {

                                for (diaDoc in diasSnapshot) {
                                    val habitosArray =
                                        diaDoc.get("habitos") as? List<Map<String, Any>>

                                    if (habitosArray != null) {
                                        totalHabitos += habitosArray.size

                                        val completadosEnEsteDia = habitosArray.count { habito ->
                                            habito["completado"] as? Boolean == true
                                        }
                                        habitosCompletados += completadosEnEsteDia

                                    }
                                }
                            }

                            desafiosProcesados++


                            if (desafiosProcesados == totalDesafios) {
                                callback(totalHabitos, habitosCompletados)
                            }
                        }
                        .addOnFailureListener { error ->
                            desafiosProcesados++

                            if (desafiosProcesados == totalDesafios) {
                                callback(totalHabitos, habitosCompletados)
                            }
                        }
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, context.getString(R.string.log_error_obtener_desafios), error)
                enviarNotificacionGenerica(context)
            }
    }

    private fun generarNotificacion(context: Context, habitosFaltantes: Int): NotificacionData {
        val fraseMotivacional = NotificationHelper.obtenerFraseMotivacional(context)

        return when (habitosFaltantes) {
            1 -> NotificacionData(
                titulo = context.getString(R.string.notificacion_titulo_un_habito),
                mensaje = context.getString(
                    R.string.notificacion_mensaje_un_habito,
                    fraseMotivacional
                )
            )

            in 2..3 -> NotificacionData(
                titulo = context.getString(
                    R.string.notificacion_titulo_pocos_habitos,
                    habitosFaltantes
                ),
                mensaje = context.getString(
                    R.string.notificacion_mensaje_buen_camino,
                    fraseMotivacional
                )
            )

            in 4..6 -> NotificacionData(
                titulo = context.getString(
                    R.string.notificacion_titulo_pocos_habitos,
                    habitosFaltantes
                ),
                mensaje = context.getString(
                    R.string.notificacion_mensaje_retomar_ritmo,
                    fraseMotivacional
                )
            )

            else -> NotificacionData(
                titulo = context.getString(
                    R.string.notificacion_titulo_pocos_habitos,
                    habitosFaltantes
                ),
                mensaje = context.getString(
                    R.string.notificacion_mensaje_decision_intentar,
                    fraseMotivacional
                )
            )
        }
    }

    private fun enviarNotificacionGenerica(context: Context) {
        enviarNotificacion(
            context,
            context.getString(R.string.notificacion_titulo_generica),
            NotificationHelper.obtenerFraseMotivacional(context)
        )
    }

    private fun enviarNotificacion(context: Context, titulo: String, mensaje: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

        val builder = NotificationCompat.Builder(
            context,
            context.getString(R.string.channel_id_desafio_recordatorios)
        )
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
        } catch (e: Exception) {
            Log.e(TAG, context.getString(R.string.log_error_reprogramar_notificacion), e)
        }
    }

    data class NotificacionData(
        val titulo: String,
        val mensaje: String
    )
}