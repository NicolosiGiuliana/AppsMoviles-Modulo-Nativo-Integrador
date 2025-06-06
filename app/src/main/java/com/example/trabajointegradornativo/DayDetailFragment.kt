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

        return view
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

