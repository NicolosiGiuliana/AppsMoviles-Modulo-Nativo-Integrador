package com.example.trabajointegradornativo

import android.content.Context

data class DesafioPublico(
    var id: String = "",
    val activo: Boolean = true,
    val autorId: String = "",
    val autorNombre: String = "",
    val completado: Boolean = false,
    val completados: Int = 0,
    val creadoPor: String = "",
    val desafioOriginalId: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val diaActual: Int = 1,
    val dias: Int = 30,
    val esPublico: Boolean = true,
    val estado: String = ESTADO_ACTIVO,
    val etiquetas: List<String> = emptyList(),
    val habitos: List<Habito> = emptyList(),
    val fechaCreacion: com.google.firebase.Timestamp? = null
) {
    companion object {

        const val ESTADO_ACTIVO = "activo"
        const val ESTADO_PAUSADO = "pausado"
        const val ESTADO_COMPLETADO = "completado"
        const val ESTADO_CANCELADO = "cancelado"


        const val DIAS_MINIMOS = 1
        const val DIAS_MAXIMOS = 365
        const val DIA_INICIAL = 1
        const val DIAS_DEFAULT = 30
    }

    constructor() : this(
        id = "",
        activo = true,
        autorId = "",
        autorNombre = "",
        completado = false,
        completados = 0,
        creadoPor = "",
        desafioOriginalId = "",
        nombre = "",
        descripcion = "",
        diaActual = DIA_INICIAL,
        dias = DIAS_DEFAULT,
        esPublico = true,
        estado = ESTADO_ACTIVO,
        etiquetas = emptyList(),
        habitos = emptyList(),
        fechaCreacion = null
    )

    fun getEstadoDisplayName(context: Context): String {
        return when (estado) {
            ESTADO_ACTIVO -> context.getString(R.string.challenge_state_active)
            ESTADO_PAUSADO -> context.getString(R.string.challenge_state_paused)
            ESTADO_COMPLETADO -> context.getString(R.string.challenge_state_completed)
            ESTADO_CANCELADO -> context.getString(R.string.challenge_state_cancelled)
            else -> context.getString(R.string.challenge_state_unknown)
        }
    }

    fun isValid(): Boolean {
        return nombre.isNotBlank() &&
                descripcion.isNotBlank() &&
                dias in DIAS_MINIMOS..DIAS_MAXIMOS &&
                diaActual in DIA_INICIAL..dias &&
                habitos.isNotEmpty()
    }

    fun isActivo(): Boolean = estado == ESTADO_ACTIVO
    fun isCompletado(): Boolean = estado == ESTADO_COMPLETADO
    fun isPausado(): Boolean = estado == ESTADO_PAUSADO
    fun isCancelado(): Boolean = estado == ESTADO_CANCELADO


    fun getProgreso(): Float {
        return if (dias > 0) (diaActual.toFloat() / dias.toFloat()) else 0f
    }

    fun getProgresoPercentage(): Int {
        return (getProgreso() * 100).toInt()
    }

    fun activar(): DesafioPublico = copy(estado = ESTADO_ACTIVO, activo = true)
    fun pausar(): DesafioPublico = copy(estado = ESTADO_PAUSADO, activo = false)
    fun completar(): DesafioPublico =
        copy(estado = ESTADO_COMPLETADO, completado = true, activo = false)

    fun cancelar(): DesafioPublico = copy(estado = ESTADO_CANCELADO, activo = false)

    fun avanzarDia(): DesafioPublico {
        val nuevoDia = if (diaActual < dias) diaActual + 1 else diaActual
        val nuevoEstado = if (nuevoDia >= dias) ESTADO_COMPLETADO else estado
        val nuevoCompletado = nuevoDia >= dias

        return copy(
            diaActual = nuevoDia,
            estado = nuevoEstado,
            completado = nuevoCompletado,
            activo = !nuevoCompletado
        )
    }
}