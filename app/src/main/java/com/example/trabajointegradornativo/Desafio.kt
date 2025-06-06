package com.example.trabajointegradornativo

data class Desafio(
    val nombre: String = "",
    val descripcion: String = "",
    val dias: Int = 0,
    val creadoPor: String = "",
    val id: String = "" // opcional, útil si querés almacenar el ID de Firestore
)