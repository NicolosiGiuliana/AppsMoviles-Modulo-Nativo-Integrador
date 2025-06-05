package com.example.trabajointegradornativo.placeholder

import android.content.Context

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 */
object PlaceholderContent {

    /**
     * An array of sample (placeholder) items.
     */
    val ITEMS: MutableList<PlaceholderItem> = ArrayList()

    /**
     * A map of sample (placeholder) items, by ID.
     */
    val ITEM_MAP: MutableMap<String, PlaceholderItem> = HashMap()

    fun initializeContent(context: Context) {
        // Limpiar listas previas para evitar duplicados al reinicializar
        ITEMS.clear()
        ITEM_MAP.clear()

        // Acceder a las preferencias del usuario
        val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userObjective = sharedPreferences.getString("user_objective", "")

        // Obtener los desafíos basados en el objetivo
        val challenges = getChallengesForObjective(userObjective)
        challenges.forEachIndexed { index, challenge ->
            addItem(PlaceholderItem(index.toString(), challenge.title, challenge.details))
        }
    }

    // Metodo para obtener desafíos basados en el objetivo
    private fun getChallengesForObjective(objective: String?): List<Challenge> {
        return when (objective) {
            "Bienestar digital" -> listOf(
                Challenge("1", "Reducir uso de redes sociales", "Limita tu tiempo a 30 minutos diarios."),
                Challenge("2", "Desconexión digital", "Apaga tus dispositivos durante 2 horas al día.")
            )
            "Salud física" -> listOf(
                Challenge("1", "Caminar 10,000 pasos", "Mantente activo caminando diariamente."),
                Challenge("2", "Hidratación adecuada", "Bebe al menos 2 litros de agua al día.")
            )
            else -> listOf(
                Challenge("1", "Desafío genérico", "Completa cualquier tarea relacionada con tus objetivos.")
            )
        }
    }

    // Clase de datos para desafíos
    data class Challenge(val id: String, val title: String, val details: String)

    private fun addItem(item: PlaceholderItem) {
        ITEMS.add(item)
        ITEM_MAP[item.id] = item
    }

    /**
     * A placeholder item representing a piece of content.
     */
    data class PlaceholderItem(val id: String, val content: String, val details: String) {
        override fun toString(): String = content
    }
}
