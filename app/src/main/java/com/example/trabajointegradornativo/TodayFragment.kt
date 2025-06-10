package com.example.trabajointegradornativo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class TodayFragment : Fragment() {

    // Variables para rastrear estados
    private var waterCompleted = false
    private var meditationCompleted = true
    private var exerciseCompleted = true
    private var readingCompleted = false
    private var notesCompleted = true

    // Variables para rastrear si las cards están expandidas
    private var exerciseCardExpanded = true
    private var readingCardExpanded = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.action_to_today_fragment, container, false)

        setupClickListeners(view)
        updateProgressSummary(view)
        setupBottomNavigation(view)

        return view
    }

    private fun setupClickListeners(view: View) {
        // Listener para "Beber 2L de agua"
        setupSimpleHabitClickListener(view, R.id.activities_container, "Beber 2L de agua") { completed ->
            waterCompleted = completed
            updateProgressSummary(view)
        }

        // Listener para "Meditar 10 min"
        setupSimpleHabitClickListener(view, R.id.activities_container, "Meditar 10 min") { completed ->
            meditationCompleted = completed
            updateProgressSummary(view)
        }

        // Listener para card de ejercicio
        setupExerciseCardListener(view)

        // Listener para card de lectura
        setupReadingCardListener(view)
    }

    private fun setupSimpleHabitClickListener(view: View, containerId: Int, habitText: String, onToggle: (Boolean) -> Unit) {
        val container = view.findViewById<LinearLayout>(containerId)

        // Buscar el LinearLayout que contiene el hábito específico
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout) {
                val textView = child.findViewById<TextView>(android.R.id.text1)
                    ?: child.getChildAt(1) as? TextView

                if (textView?.text == habitText) {
                    val checkIcon = child.getChildAt(0) as ImageView

                    child.setOnClickListener {
                        val isCurrentlyCompleted = when (habitText) {
                            "Beber 2L de agua" -> waterCompleted
                            "Meditar 10 min" -> meditationCompleted
                            else -> false
                        }

                        val newState = !isCurrentlyCompleted
                        onToggle(newState)

                        // Actualizar el ícono
                        if (newState) {
                            checkIcon.setImageResource(R.drawable.ic_check_green)
                            checkIcon.setColorFilter(resources.getColor(android.R.color.holo_green_dark))
                        } else {
                            checkIcon.setImageResource(R.drawable.ic_circle_empty)
                            checkIcon.setColorFilter(resources.getColor(android.R.color.darker_gray))
                        }
                    }
                    break
                }
            }
        }
    }

    private fun setupExerciseCardListener(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.activities_container)

        // Buscar la CardView del ejercicio
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is CardView) {
                val cardLayout = child.getChildAt(0) as LinearLayout
                val titleText = cardLayout.getChildAt(0) as TextView

                if (titleText.text.toString().contains("30 Días de Ejercicio")) {
                    val exerciseLayout = cardLayout.getChildAt(1) as LinearLayout
                    val photoLayout = cardLayout.getChildAt(2)
                    val commentLayout = cardLayout.getChildAt(3)
                    val exerciseIcon = exerciseLayout.getChildAt(0) as ImageView

                    // Click en el título para expandir/contraer
                    titleText.setOnClickListener {
                        exerciseCardExpanded = !exerciseCardExpanded

                        if (exerciseCardExpanded) {
                            exerciseLayout.visibility = View.VISIBLE
                            photoLayout.visibility = View.VISIBLE
                            commentLayout.visibility = View.VISIBLE
                        } else {
                            exerciseLayout.visibility = View.GONE
                            photoLayout.visibility = View.GONE
                            commentLayout.visibility = View.GONE
                        }
                    }

                    // Click en el ejercicio para marcar/desmarcar
                    exerciseLayout.setOnClickListener {
                        exerciseCompleted = !exerciseCompleted

                        if (exerciseCompleted) {
                            exerciseIcon.setImageResource(R.drawable.ic_check_green)
                            exerciseIcon.setColorFilter(resources.getColor(android.R.color.holo_green_dark))
                        } else {
                            exerciseIcon.setImageResource(R.drawable.ic_circle_empty)
                            exerciseIcon.setColorFilter(resources.getColor(android.R.color.darker_gray))
                        }

                        updateProgressSummary(view)
                    }
                    break
                }
            }
        }
    }

    private fun setupReadingCardListener(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.activities_container)

        // Buscar la CardView de lectura
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is CardView) {
                val cardLayout = child.getChildAt(0) as LinearLayout
                val titleText = cardLayout.getChildAt(0) as TextView

                if (titleText.text.toString().contains("Lectura Diaria")) {
                    val readingLayout = cardLayout.getChildAt(1) as LinearLayout
                    val notesLayout = cardLayout.getChildAt(2) as LinearLayout
                    val readingIcon = readingLayout.getChildAt(0) as ImageView
                    val notesIcon = notesLayout.getChildAt(0) as ImageView

                    // Click en el título para expandir/contraer
                    titleText.setOnClickListener {
                        readingCardExpanded = !readingCardExpanded

                        if (readingCardExpanded) {
                            readingLayout.visibility = View.VISIBLE
                            notesLayout.visibility = View.VISIBLE
                        } else {
                            readingLayout.visibility = View.GONE
                            notesLayout.visibility = View.GONE
                        }
                    }

                    // Click en "Leer 20 páginas"
                    readingLayout.setOnClickListener {
                        readingCompleted = !readingCompleted

                        if (readingCompleted) {
                            readingIcon.setImageResource(R.drawable.ic_check_green)
                            readingIcon.setColorFilter(resources.getColor(android.R.color.holo_green_dark))
                        } else {
                            readingIcon.setImageResource(R.drawable.ic_circle_empty)
                            readingIcon.setColorFilter(resources.getColor(android.R.color.darker_gray))
                        }

                        updateProgressSummary(view)
                    }

                    // Click en "Tomar notas"
                    notesLayout.setOnClickListener {
                        notesCompleted = !notesCompleted

                        if (notesCompleted) {
                            notesIcon.setImageResource(R.drawable.ic_check_green)
                            notesIcon.setColorFilter(resources.getColor(android.R.color.holo_green_dark))
                        } else {
                            notesIcon.setImageResource(R.drawable.ic_circle_empty)
                            notesIcon.setColorFilter(resources.getColor(android.R.color.darker_gray))
                        }

                        updateProgressSummary(view)
                    }
                    break
                }
            }
        }
    }

    private fun updateProgressSummary(view: View) {
        val progressText = view.findViewById<TextView>(R.id.progress_summary)
        val completedTasks = listOf(waterCompleted, meditationCompleted, exerciseCompleted, readingCompleted, notesCompleted)
        val completedCount = completedTasks.count { it }
        val totalTasks = completedTasks.size

        progressText.text = "$completedCount/$totalTasks completados"
    }

    private fun setupBottomNavigation(view: View) {
        // Home
        val homeLayout = view.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            navigateToHome()
        }

        // Hoy (ya estamos aquí)
        val todayLayout = view.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            // Ya estamos en Hoy, solo actualizar colores
            updateBottomNavigationColors(view, "today")
        }

        // Configuración (deshabilitado por ahora)
        val settingsLayout = view.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(2) as? LinearLayout
        settingsLayout?.setOnClickListener {
            Toast.makeText(context, "Configuración próximamente", Toast.LENGTH_SHORT).show()
        }

        // Establecer colores iniciales
        updateBottomNavigationColors(view, "today")
    }

    private fun navigateToHome() {
        try {
            findNavController().navigate(R.id.action_todayFragment_to_itemListFragment)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al navegar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBottomNavigationColors(view: View, activeTab: String) {
        val bottomNav = view.findViewById<LinearLayout>(R.id.bottom_navigation)

        // Home
        val homeLayout = bottomNav?.getChildAt(0) as? LinearLayout
        val homeIcon = homeLayout?.getChildAt(0) as? ImageView
        val homeText = homeLayout?.getChildAt(1) as? TextView

        // Hoy
        val todayLayout = bottomNav?.getChildAt(1) as? LinearLayout
        val todayIcon = todayLayout?.getChildAt(0) as? ImageView
        val todayText = todayLayout?.getChildAt(1) as? TextView

        // Configuración
        val settingsLayout = bottomNav?.getChildAt(2) as? LinearLayout
        val settingsIcon = settingsLayout?.getChildAt(0) as? ImageView
        val settingsText = settingsLayout?.getChildAt(1) as? TextView

        // Colores
        val activeColor = ContextCompat.getColor(requireContext(), R.color.primary_green)
        val inactiveColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        val disabledColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        when (activeTab) {
            "home" -> {
                homeIcon?.setColorFilter(activeColor)
                homeText?.setTextColor(activeColor)
                todayIcon?.setColorFilter(inactiveColor)
                todayText?.setTextColor(inactiveColor)
            }
            "today" -> {
                homeIcon?.setColorFilter(inactiveColor)
                homeText?.setTextColor(inactiveColor)
                todayIcon?.setColorFilter(activeColor)
                todayText?.setTextColor(activeColor)
            }
        }

        // Configuración siempre deshabilitada
        settingsIcon?.setColorFilter(disabledColor)
        settingsText?.setTextColor(disabledColor)
    }
}