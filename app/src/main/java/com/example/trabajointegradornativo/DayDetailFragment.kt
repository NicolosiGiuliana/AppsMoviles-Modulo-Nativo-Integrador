package com.example.trabajointegradornativo

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DayDetailFragment : Fragment() {

    companion object {
        const val ARG_DAY_NUMBER = "day_number"
    }

    private var dayNumber = 1
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var desafio: ItemListFragment.Desafio
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var habitosDesafio = mutableListOf<Map<String, Any>>()
    private var habitosCompletados = mutableMapOf<Int, Boolean>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_day_detail, container, false)

        arguments?.let {
            dayNumber = it.getInt(ARG_DAY_NUMBER, 1)
            desafio = it.getParcelable("desafio")
                ?: throw IllegalStateException(getString(R.string.error_unknown, "Desafio no encontrado en los argumentos"))
        }

        sharedPrefs = requireContext().getSharedPreferences("habit_states", Context.MODE_PRIVATE)

        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "${desafio.nombre}"

        val dayTextView = view.findViewById<TextView>(R.id.day_detail_text)
        dayTextView.text = getString(R.string.day_number, dayNumber)

        cargarHabitosDesafio(view)

        val completeButton = view.findViewById<Button>(R.id.complete_day_button)
        completeButton.setOnClickListener {
            completarDia()
        }

        view.post {
            setupBottomNavigation()
        }
        return view
    }

    private fun cargarHabitosDesafio(view: View) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .document("dia_$dayNumber")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val habitos = document.get("habitos") as? List<Map<String, Any>> ?: emptyList()
                    habitosDesafio.clear()
                    habitosDesafio.addAll(habitos)

                    Log.d("DayDetailFragment", "Hábitos cargados desde día específico: ${habitosDesafio.size}")

                    cargarEstadoHabitos(view)
                } else {
                    Log.d("DayDetailFragment", "Día no existe, creando hábitos base")
                    crearHabitosBaseDia(view)
                }
            }
            .addOnFailureListener { e ->
                Log.e("DayDetailFragment", "Error al cargar hábitos del día: ${e.message}")
                crearHabitosBaseDia(view)
            }
    }

    private fun crearHabitosBaseDia(view: View) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val habitosBase = document.get("habitos") as? List<Map<String, Any>> ?: emptyList()

                    val habitosParaGuardar = habitosBase.map { habito ->
                        hashMapOf(
                            "nombre" to (habito["nombre"] as? String ?: ""),
                            "completado" to false
                        )
                    }

                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafio.id)
                        .collection("dias")
                        .document("dia_$dayNumber")
                        .set(hashMapOf(
                            "dia" to dayNumber,
                            "habitos" to habitosParaGuardar,
                            "fecha_creacion" to com.google.firebase.Timestamp.now()
                        ))
                        .addOnSuccessListener {
                            Log.d("DayDetailFragment", "Día creado con hábitos base")
                            cargarHabitosDesafio(view)
                        }
                        .addOnFailureListener { e ->
                            Log.e("DayDetailFragment", "Error al crear día: ${e.message}")
                            Toast.makeText(context, getString(R.string.error_saving_data, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.e("DayDetailFragment", "Documento del desafío no encontrado")
                    Toast.makeText(context, getString(R.string.challenge_without_name), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("DayDetailFragment", "Error al cargar desafío base: ${e.message}")
                Toast.makeText(context, getString(R.string.error_saving_data, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarEstadoHabitos(view: View) {
        habitosCompletados.clear()

        habitosDesafio.forEachIndexed { index, habito ->
            val completado = habito["completado"] as? Boolean ?: false
            habitosCompletados[index] = completado
        }

        Log.d("DayDetailFragment", "Estados de hábitos cargados: $habitosCompletados")

        mostrarHabitos(view)
    }

    private fun mostrarHabitos(view: View) {
        val rootLayout = view.findViewById<LinearLayout>(R.id.day_detail_container)

        val childrenToRemove = mutableListOf<View>()
        for (i in 0 until rootLayout.childCount) {
            val child = rootLayout.getChildAt(i)
            if (child.tag == "habito_item") {
                childrenToRemove.add(child)
            }
        }
        childrenToRemove.forEach { rootLayout.removeView(it) }

        val inflater = LayoutInflater.from(requireContext())

        habitosDesafio.forEachIndexed { index, habito ->
            val habitoView = inflater.inflate(R.layout.habito_item, rootLayout, false)
            habitoView.tag = "habito_item"

            val nombreHabito = habitoView.findViewById<TextView>(R.id.habito_nombre)
            val iconoHabito = habitoView.findViewById<ImageView>(R.id.habito_icono)

            nombreHabito.text = habito["nombre"] as? String ?: getString(R.string.habit_name_default)

            val completado = habitosCompletados[index] ?: false
            iconoHabito.setImageResource(
                if (completado) R.drawable.ic_check_green else R.drawable.ic_circle_empty
            )

            habitoView.setOnClickListener {
                val nuevoEstado = !habitosCompletados.getOrDefault(index, false)
                habitosCompletados[index] = nuevoEstado

                iconoHabito.setImageResource(
                    if (nuevoEstado) R.drawable.ic_check_green else R.drawable.ic_circle_empty
                )

                guardarEstadoHabito(index, nuevoEstado)
            }

            val buttonIndex = rootLayout.childCount - 1
            rootLayout.addView(habitoView, buttonIndex)
        }
    }

    private fun guardarEstadoHabito(index: Int, completado: Boolean) {
        val uid = auth.currentUser?.uid ?: return

        habitosCompletados[index] = completado

        val habitosParaGuardar = habitosDesafio.mapIndexed { i, habito ->
            hashMapOf(
                "nombre" to (habito["nombre"] as? String ?: ""),
                "completado" to habitosCompletados.getOrDefault(i, false)
            )
        }

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .document("dia_$dayNumber")
            .update(
                "habitos", habitosParaGuardar,
                "fecha_actualizacion", com.google.firebase.Timestamp.now()
            )
            .addOnFailureListener { e ->
                Log.e("DayDetailFragment", "Error al guardar hábito: ${e.message}")
                Toast.makeText(context, getString(R.string.error_saving_progress, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
    }

    private fun completarDia() {
        val uid = auth.currentUser?.uid ?: return

        val todosCompletados = habitosDesafio.indices.all { index ->
            habitosCompletados.getOrDefault(index, false)
        }

        if (!todosCompletados) {
            Toast.makeText(context, getString(R.string.mark_completed_habits), Toast.LENGTH_LONG).show()
            return
        }

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias_completados")
            .document("dia_$dayNumber")
            .set(hashMapOf(
                "dia" to dayNumber,
                "completado" to true,
                "fecha_completado" to com.google.firebase.Timestamp.now()
            ))
            .addOnSuccessListener {
                val dayCompletedMessage = "${getString(R.string.day_number, dayNumber)} ${getString(R.string.completed)}!"
                Toast.makeText(context, dayCompletedMessage, Toast.LENGTH_SHORT).show()

                actualizarContadorDiasCompletados()

                findNavController().popBackStack()
            }
            .addOnFailureListener { e ->
                Log.e("DayDetailFragment", "Error al completar día: ${e.message}")
                Toast.makeText(context, getString(R.string.error_saving_progress, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarContadorDiasCompletados() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias_completados")
            .get()
            .addOnSuccessListener { result ->
                val totalCompletados = result.size()

                firestore.collection("usuarios")
                    .document(uid)
                    .collection("desafios")
                    .document(desafio.id)
                    .update("completados", totalCompletados)
                    .addOnFailureListener { e ->
                        Log.e("DayDetailFragment", "Error al actualizar contador: ${e.message}")
                    }
            }
    }

    private fun setupBottomNavigation() {
        val view = requireView()

        val homeLayout = view.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            navigateToHome()
        }

        val todayLayout = view.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            updateBottomNavigationColors("today")
        }

        val settingsLayout = view.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(2) as? LinearLayout
        settingsLayout?.setOnClickListener {
            Toast.makeText(context, getString(R.string.settings_coming_soon), Toast.LENGTH_SHORT).show()
        }

        updateBottomNavigationColors("today")
    }

    private fun navigateToHome() {
        try {
            findNavController().navigate(R.id.action_dayDetailFragment_to_itemListFragment)
        } catch (e: Exception) {
            try {
                findNavController().popBackStack(R.id.itemListFragment, false)
            } catch (ex: Exception) {
                Log.e("Navigation", "Error al navegar: ${ex.message}", ex)
                Toast.makeText(context, getString(R.string.error_navigating_home, ex.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateBottomNavigationColors(activeTab: String) {
        val view = requireView()
        val bottomNav = view.findViewById<LinearLayout>(R.id.bottom_navigation)

        val homeLayout = bottomNav?.getChildAt(0) as? LinearLayout
        val homeIcon = homeLayout?.getChildAt(0) as? ImageView
        val homeText = homeLayout?.getChildAt(1) as? TextView

        val todayLayout = bottomNav?.getChildAt(1) as? LinearLayout
        val todayIcon = todayLayout?.getChildAt(0) as? ImageView
        val todayText = todayLayout?.getChildAt(1) as? TextView

        val settingsLayout = bottomNav?.getChildAt(2) as? LinearLayout
        val settingsIcon = settingsLayout?.getChildAt(0) as? ImageView
        val settingsText = settingsLayout?.getChildAt(1) as? TextView

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

        settingsIcon?.setColorFilter(disabledColor)
        settingsText?.setTextColor(disabledColor)
    }
}