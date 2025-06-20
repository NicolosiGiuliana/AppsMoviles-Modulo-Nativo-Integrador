package com.example.trabajointegradornativo

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
    val estado: String = "activo",
    val etiquetas: List<String> = emptyList(),
    val fechaCreacion: com.google.firebase.Timestamp? = null
)
{
    // Constructor vacío requerido por Firebase
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
        diaActual = 1,
        dias = 30,
        esPublico = true,
        estado = "activo",
        etiquetas = emptyList(),
        fechaCreacion = null
    )
}