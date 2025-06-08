package com.example.trabajointegradornativo

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.core.content.ContextCompat
import android.widget.*

class DayDetailFragment : Fragment() {

    companion object {
        const val ARG_DAY_NUMBER = "day_number"
    }

    private var dayNumber = 1
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_day_detail, container, false)

        dayNumber = arguments?.getInt(ARG_DAY_NUMBER) ?: 1
        sharedPrefs = requireContext().getSharedPreferences("habit_states", Context.MODE_PRIVATE)

        val dayTextView = view.findViewById<TextView>(R.id.day_detail_text)
        dayTextView.text = "Día $dayNumber"

        val rootLayout = view.findViewById<LinearLayout>(R.id.day_detail_container)

        // Recorremos los hábitos
        var habitIndex = 0
        for (i in 0 until rootLayout.childCount) {
            val child = rootLayout.getChildAt(i)

            if (child is LinearLayout && child.orientation == LinearLayout.HORIZONTAL) {
                val icon = findFirstImageView(child)
                if (icon != null) {
                    val key = getHabitKey(dayNumber, habitIndex)
                    val isChecked = sharedPrefs.getBoolean(key, false)

                    // Mostrar el ícono correcto según el estado guardado
                    icon.setImageResource(
                        if (isChecked) R.drawable.ic_check_green else R.drawable.ic_circle_empty
                    )

                    // Manejar el click
                    child.setOnClickListener {
                        val newState = toggleHabitIcon(icon)
                        sharedPrefs.edit().putBoolean(key, newState).apply()
                    }

                    habitIndex++
                }
            }
        }

        val completeButton = view.findViewById<Button>(R.id.complete_day_button)
        completeButton.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("completed_days", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("day_completed_$dayNumber", true).apply()

            // Volver automáticamente a ItemDetailFragment
            findNavController().popBackStack()
        }

        view.post {
            setupBottomNavigation()
        }
        return view
    }

    private fun setupBottomNavigation() {
        val view = requireView()

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
            updateBottomNavigationColors("today")
        }

        // Configuración (deshabilitado por ahora)
        val settingsLayout = view.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(2) as? LinearLayout
        settingsLayout?.setOnClickListener {
            Toast.makeText(context, "Configuración próximamente", Toast.LENGTH_SHORT).show()
        }

        // Establecer colores iniciales
        updateBottomNavigationColors("today")
    }

    private fun navigateToHome() {
        try {
            findNavController().navigate(R.id.action_dayDetailFragment_to_itemListFragment)
        } catch (e: Exception) {
            // Alternativa: usar popBackStack para volver
            try {
                findNavController().popBackStack(R.id.itemListFragment, false)
            } catch (ex: Exception) {
                android.util.Log.e("Navigation", "Error al navegar: ${ex.message}", ex)
                Toast.makeText(context, "Error al volver a Home", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateBottomNavigationColors(activeTab: String) {
        val view = requireView()
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

    private fun findFirstImageView(layout: LinearLayout): ImageView? {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is ImageView) return child
        }
        return null
    }

    private fun toggleHabitIcon(icon: ImageView): Boolean {
        val checkedDrawable = requireContext().getDrawable(R.drawable.ic_check_green)?.constantState
        val currentState = icon.drawable.constantState

        return if (currentState == checkedDrawable) {
            icon.setImageResource(R.drawable.ic_circle_empty)
            false
        } else {
            icon.setImageResource(R.drawable.ic_check_green)
            true
        }
    }

    private fun getHabitKey(day: Int, index: Int): String {
        return "habit_dia${day}_$index"
    }
}

