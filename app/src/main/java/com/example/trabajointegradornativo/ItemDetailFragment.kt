package com.example.trabajointegradornativo

import android.content.ClipData
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.trabajointegradornativo.databinding.FragmentItemDetailBinding
import com.example.trabajointegradornativo.placeholder.PlaceholderContent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

class ItemDetailFragment : Fragment() {

    private var item: PlaceholderContent.PlaceholderItem? = null
    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var desafio: ItemListFragment.Desafio

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var diasCompletados = mutableSetOf<Int>()
    private var isUpdatingContent = false

    private val dragListener = View.OnDragListener { _, event ->
        if (event.action == DragEvent.ACTION_DROP) {
            val clipDataItem: ClipData.Item = event.clipData.getItemAt(0)
            val dragData = clipDataItem.text
            item = PlaceholderContent.ITEM_MAP[dragData]
            updateContent()
        }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        // Configurar listener para detectar cuando vuelves de la pantalla de edición
        setFragmentResultListener("desafio_editado") { _, bundle ->
            val desafioEditado = bundle.getBoolean("cambios_realizados", false)
            if (desafioEditado) {
                // Recargar los datos cuando hay cambios
                cargarDiasCompletados()
            }
        }

        arguments?.let {
            desafio = it.getParcelable("desafio") ?: throw IllegalStateException("Desafio no encontrado en los argumentos")
            if (it.containsKey(ARG_ITEM_ID)) {
                val id = it.getString(ARG_ITEM_ID)
                Log.d("ItemDetailFragment", "ARG_ITEM_ID recibido: $id")

                item = PlaceholderContent.ITEM_MAP[id]
                if (item != null) {
                    Log.d("ItemDetailFragment", "Item encontrado: ${item!!.content}")
                } else {
                    Log.d("ItemDetailFragment", "No se encontró item con ID: $id en ITEM_MAP")
                }
            } else {
                Log.d("ItemDetailFragment", "ARG_ITEM_ID no recibido")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        val rootView = binding.root
        rootView.setOnDragListener(dragListener)

        Log.d("ItemDetailFragment", "onCreateView: Llamando a updateContent")
        cargarDiasCompletados()
        setupBottomNavigation()

        // Configuración del botón para editar desafío
        binding.btnEditChallenge!!.setOnClickListener {
            val bundle = Bundle().apply {
                putParcelable("desafio", desafio) // Pasar datos del desafío si es necesario
            }
            findNavController().navigate(R.id.action_itemDetailFragment_to_editDesafioFragment, bundle)
        }
        return rootView
    }


    private fun cargarDiasCompletados() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .whereEqualTo("completado", true)
            .get()
            .addOnSuccessListener { result ->
                val diasCompletadosAnterior = diasCompletados.size
                diasCompletados.clear()

                for (document in result) {
                    val dia = document.getLong("dia")?.toInt() ?: 0
                    if (dia > 0) {
                        diasCompletados.add(dia)
                    }
                }

                val diasCompletadosNuevo = diasCompletados.size
                Log.d("ItemDetailFragment", "Días completados: $diasCompletadosAnterior -> $diasCompletadosNuevo")

                // Solo actualizar si no estamos ya actualizando y si hay cambios
                if (!isUpdatingContent) {
                    updateContent()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ItemDetailFragment", "Error al cargar días completados: ${e.message}")
                if (!isUpdatingContent) {
                    updateContent()
                }
            }
    }

    private fun updateContent() {
        // Evitar múltiples actualizaciones simultáneas
        if (isUpdatingContent) {
            Log.d("ItemDetailFragment", "Ya se está actualizando el contenido, saltando...")
            return
        }

        isUpdatingContent = true
        val uid = auth.currentUser?.uid ?: return

        // Obtener la información actualizada del desafío desde Firestore
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val nombre = document.getString("nombre") ?: "Sin nombre"
                    val descripcion = document.getString("descripcion") ?: "Sin descripción"
                    val dias = document.getLong("dias")?.toInt() ?: 0
                    val diaActual = document.getLong("diaActual")?.toInt() ?: 1
                    val estado = document.getString("estado") ?: "Indefinido"
                    val completados = diasCompletados.size

                    val etiquetas = document.get("etiquetas") as? List<String> ?: emptyList()
                    val habitosBase = document.get("habitos") as? List<Map<String, Any>> ?: emptyList()
                    val totalHabitos = habitosBase.size

                    // Obtener los hábitos del día actual con su estado
                    obtenerHabitosDiaActual(uid, desafio.id) { habitosDelDia ->
                        // Configurar UI con los datos de Firestore
                        actualizarUI(
                            nombre = nombre,
                            descripcion = descripcion,
                            dias = dias,
                            diaActual = diaActual,
                            completados = completados,
                            totalHabitos = totalHabitos,
                            estado = estado,
                            habitos = habitosDelDia
                        )

                        actualizarInformacionGeneral(nombre, descripcion, dias, diaActual, completados, estado)
                        mostrarEtiquetas(etiquetas)

                        // Liberar el flag
                        isUpdatingContent = false
                    }
                } else {
                    isUpdatingContent = false
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error al obtener los datos del desafío", exception)
                Toast.makeText(context, "Error al cargar el desafío", Toast.LENGTH_SHORT).show()
                isUpdatingContent = false
            }
    }

    private fun actualizarInformacionGeneral(
        nombre: String,
        descripcion: String,
        dias: Int,
        diaActual: Int,
        completados: Int,
        estado: String
    ) {
        // Actualizar título del desafío
        binding.root.findViewById<TextView>(R.id.challenge_title)?.text = nombre

        // Actualizar descripción
        binding.root.findViewById<TextView>(R.id.challenge_description)?.text = descripcion

        // Actualizar progreso
        binding.root.findViewById<TextView>(R.id.progress_subtitle)?.text = "Día $diaActual de $dias"

        binding.root.findViewById<TextView>(R.id.challenge_duration)?.text = "$dias días"

        // Actualizar barra de progreso
        val progressBar = binding.root.findViewById<ProgressBar>(R.id.circular_progress)
        progressBar?.let {
            it.max = dias
            it.progress = completados
            Log.d("ItemDetailFragment", "Progress bar actualizada: $completados/$dias")
        }

        // Actualizar porcentaje
        val porcentaje = if (dias > 0) (completados * 100) / dias else 0
        binding.root.findViewById<TextView>(R.id.progress_text)?.text = "$porcentaje%"

        Log.d("ItemDetailFragment", "Porcentaje actualizado: $porcentaje% ($completados/$dias días completados)")
    }
    private fun verificarCambiosEnDias() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .whereEqualTo("completado", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ItemDetailFragment", "Error al escuchar cambios", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val nuevosCompletados = mutableSetOf<Int>()
                    for (document in snapshots) {
                        val dia = document.getLong("dia")?.toInt() ?: 0
                        if (dia > 0) {
                            nuevosCompletados.add(dia)
                        }
                    }

                    // Solo actualizar si hay cambios
                    if (nuevosCompletados != diasCompletados) {
                        Log.d("ItemDetailFragment", "Cambios detectados via listener: ${diasCompletados.size} -> ${nuevosCompletados.size}")
                        diasCompletados = nuevosCompletados
                        updateContent()
                    }
                }
            }
    }


    private fun obtenerDiaActual(uid: String, desafioId: String, callback: (String?) -> Unit) {
        val fechaHoy = LocalDate.now().toString() // Fecha actual en formato "yyyy-MM-dd"

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .whereEqualTo("fechaRealizacion", fechaHoy)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    callback(result.documents[0].id) // Retorna el ID del día actual
                } else {
                    callback(null) // No se encontró el día actual
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al obtener el día actual", e)
                callback(null)
            }
    }

    private fun verificarEstadoDia(uid: String, desafioId: String, diaId: String, habitos: List<Map<String, Any>>) {
        val todosCompletados = habitos.all { (it["completado"] as? Boolean) == true }

        // Primero verificar el estado anterior del día
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .document(diaId)
            .get()
            .addOnSuccessListener { document ->
                val estadoAnterior = document.getBoolean("completado") ?: false

                // Solo actualizar si hay un cambio de estado
                if (estadoAnterior != todosCompletados) {
                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                        .collection("dias")
                        .document(diaId)
                        .update("completado", todosCompletados)
                        .addOnSuccessListener {
                            Log.d("Firestore", "Estado del día actualizado: completado = $todosCompletados")

                            // Recargar días completados para actualizar el contador
                            cargarDiasCompletados()

                            // Actualizar el estado en la UI
                            actualizarEstadoUI(todosCompletados)
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error al actualizar el estado del día", e)
                        }
                } else {
                    // Solo actualizar la UI si no hay cambio en Firestore
                    actualizarEstadoUI(todosCompletados)
                }
            }
    }

    private fun actualizarEstadoUI(diaCompletado: Boolean) {
        val estadoText = binding.root.findViewById<TextView>(R.id.day_status_text)
        estadoText?.text = if (diaCompletado) "Completado" else "Pendiente"
    }

    private fun actualizarEstadoHabitoDelDia(uid: String, desafioId: String, diaId: String, habitIndex: Int, completado: Boolean, habitosActuales: List<Map<String, Any>>) {
        // Verificar si TODOS los hábitos actuales están completados antes de permitir desmarcar
//        if (!completado) { // Si se intenta desmarcar
//            val todosCompletados = habitosActuales.all { (it["completado"] as? Boolean) == true }
//            if (todosCompletados) {
//                Toast.makeText(context, "No puedes desmarcar hábitos cuando todos están completados", Toast.LENGTH_SHORT).show()
//                return
//            }
//        }

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .document(diaId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val habitos = document.get("habitos") as? ArrayList<Map<String, Any>> ?: return@addOnSuccessListener

                    // Asegurarse de que el índice sea válido
                    if (habitIndex < 0 || habitIndex >= habitos.size) return@addOnSuccessListener

                    // Actualizar el estado del hábito
                    habitos[habitIndex] = habitos[habitIndex].toMutableMap().apply {
                        this["completado"] = completado
                    }

                    // Guardar cambios en Firestore
                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                        .collection("dias")
                        .document(diaId)
                        .update("habitos", habitos)
                        .addOnSuccessListener {
                            Log.d("Firestore", "Hábito actualizado correctamente: $habitIndex -> $completado")
                            verificarEstadoDia(uid, desafioId, diaId, habitos)

                            // NO recargar todo el contenido, solo actualizar la UI local
                            actualizarHabitoEnUI(habitIndex, completado)
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error al actualizar el hábito", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al obtener los hábitos del día", e)
            }
    }

    private fun actualizarHabitoEnUI(habitIndex: Int, completado: Boolean) {
        val habitsContainer = binding.root.findViewById<LinearLayout>(R.id.habits_container)
        if (habitIndex < habitsContainer.childCount) {
            val habitView = habitsContainer.getChildAt(habitIndex)
            val habitIcon = habitView.findViewById<ImageView>(R.id.habito_icono)
            habitIcon?.setImageResource(
                if (completado) R.drawable.ic_check_green else R.drawable.ic_circle_empty
            )
        }
    }

    private fun mostrarEtiquetas(etiquetas: List<String>) {
        val tagsContainer = binding.root.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.tags_container)
        tagsContainer?.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())

        for (etiqueta in etiquetas) {
            val tagView = inflater.inflate(R.layout.item_tag, tagsContainer, false)
            val tagText = tagView.findViewById<TextView>(R.id.tag_text)
            tagText?.text = etiqueta
            tagsContainer?.addView(tagView)
        }

        // Si no hay etiquetas, mostrar mensaje
        if (etiquetas.isEmpty()) {
            val emptyView = inflater.inflate(R.layout.empty_tags_view, tagsContainer, false)
            tagsContainer?.addView(emptyView)
        }
    }

    private fun actualizarUI(
        nombre: String,
        descripcion: String,
        dias: Int,
        diaActual: Int,
        completados: Int,
        totalHabitos: Int,
        estado: String,
        habitos: List<Map<String, Any>>
    ) {
        Log.d("ItemDetailFragment", "Actualizando UI con ${habitos.size} hábitos")

        val habitsContainer = binding.root.findViewById<LinearLayout>(R.id.habits_container)

        // IMPORTANTE: Limpiar el contenedor antes de agregar nuevos elementos
        habitsContainer.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val uid = auth.currentUser?.uid ?: return
        val desafioId = desafio.id

        // Crear una lista mutable para manejar el estado local
        val habitosLocales = habitos.map { it.toMutableMap() }.toMutableList()

        // Verificar si TODOS los hábitos están completados
        val todosHabitosCompletados = habitosLocales.all { (it["completado"] as? Boolean) == true }

        obtenerDiaActual(uid, desafioId) { diaId ->
            if (diaId != null) {
                // Verificar si el día está marcado como completado en Firestore
                verificarSiDiaEstaCompletado(uid, desafioId, diaId) { diaCompletado ->
                    Log.d("ItemDetailFragment", "Creando ${habitosLocales.size} elementos de hábitos")
                    Log.d("ItemDetailFragment", "Día completado: $diaCompletado, Todos hábitos completados: $todosHabitosCompletados")

                    for ((index, habit) in habitosLocales.withIndex()) {
                        val habitName = habit["nombre"] as? String ?: "Hábito sin nombre"
                        val habitCompleted = habit["completado"] as? Boolean ?: false

                        val habitView = inflater.inflate(R.layout.habito_item, habitsContainer, false)
                        val habitNameText = habitView.findViewById<TextView>(R.id.habito_nombre)
                        val habitIcon = habitView.findViewById<ImageView>(R.id.habito_icono)

                        habitNameText.text = habitName
                        habitIcon.setImageResource(
                            if (habitCompleted) R.drawable.ic_check_green else R.drawable.ic_circle_empty
                        )

                        habitIcon.setOnClickListener {
                            val estadoActual = habitosLocales[index]["completado"] as? Boolean ?: false
                            val nuevoEstado = !estadoActual

                            // VALIDACIÓN: Si el día está completado y todos los hábitos están marcados,
                            // no permitir desmarcar ningún hábito
                            if (diaCompletado && todosHabitosCompletados && !nuevoEstado) {
                                Toast.makeText(
                                    context,
                                    "No puedes desmarcar hábitos de un día ya completado",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@setOnClickListener
                            }

                            // Actualizar el estado local inmediatamente
                            habitosLocales[index]["completado"] = nuevoEstado

                            // Actualizar la UI inmediatamente
                            habitIcon.setImageResource(
                                if (nuevoEstado) R.drawable.ic_check_green else R.drawable.ic_circle_empty
                            )

                            // Actualizar en Firestore
                            actualizarEstadoHabitoDelDia(uid, desafioId, diaId, index, nuevoEstado, habitosLocales)
                        }

                        habitsContainer.addView(habitView)
                    }

                    // Actualizar el estado inicial en la UI basado en si todos están completados
                    actualizarEstadoUI(todosHabitosCompletados)

                    Log.d("ItemDetailFragment", "UI actualizada con ${habitsContainer.childCount} elementos")
                }
            } else {
                Log.e("Firestore", "No se encontró el día actual.")
                Toast.makeText(context, "No se encontró información del día actual", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun verificarSiDiaEstaCompletado(uid: String, desafioId: String, diaId: String, callback: (Boolean) -> Unit) {
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .document(diaId)
            .get()
            .addOnSuccessListener { document ->
                val completado = document.getBoolean("completado") ?: false
                callback(completado)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al verificar estado del día", e)
                callback(false)
            }
    }

    private fun crearDiaActual(uid: String, desafioId: String, fecha: String, callback: (List<Map<String, Any>>) -> Unit) {
        // Obtener información del desafío para crear los hábitos base
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .get()
            .addOnSuccessListener { desafioDoc ->
                val diaActual = desafioDoc.getLong("diaActual")?.toInt() ?: 1

                // Obtener hábitos base del desafío
                val habitosBase = desafioDoc.get("habitos") as? List<Map<String, Any>> ?: emptyList()

                // Crear hábitos para el día actual basados en los hábitos del desafío
                val habitosDelDia = habitosBase.map { habitoBase ->
                    mapOf(
                        "nombre" to (habitoBase["nombre"] as? String ?: "Hábito sin nombre"),
                        "completado" to false
                    )
                }

                val nuevoDia = mapOf(
                    "completado" to false,
                    "dia" to diaActual,
                    "fechaRealizacion" to fecha,
                    "fecha_creacion" to com.google.firebase.Timestamp.now(),
                    "habitos" to habitosDelDia,
                    "etiquetas" to emptyList<String>() // Agregar esta línea
                )

                firestore.collection("usuarios")
                    .document(uid)
                    .collection("desafios")
                    .document(desafioId)
                    .collection("dias")
                    .add(nuevoDia)
                    .addOnSuccessListener {
                        Log.d("Firestore", "Día actual creado exitosamente")
                        callback(habitosDelDia)
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error al crear día actual", e)
                        callback(emptyList())
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al obtener información del desafío", e)
                callback(emptyList())
            }
    }

    private fun obtenerHabitosDiaActual(uid: String, desafioId: String, callback: (List<Map<String, Any>>) -> Unit) {
        val fechaHoy = LocalDate.now().toString() // Fecha actual en formato "yyyy-MM-dd"

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .whereEqualTo("fechaRealizacion", fechaHoy)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val document = result.documents[0]
                    val habitos = document.get("habitos") as? List<Map<String, Any>> ?: emptyList()
                    Log.d("Firestore", "Hábitos del día actual obtenidos: ${habitos.size} hábitos")
                    callback(habitos)
                } else {
                    // Si no hay día para hoy, crear uno con hábitos basados en el desafío
                    Log.d("Firestore", "No se encontró día para hoy, creando...")
                    crearDiaActual(uid, desafioId, fechaHoy) { habitos ->
                        callback(habitos)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al obtener hábitos del día actual", e)
                callback(emptyList())
            }
    }

    private fun setupBottomNavigation() {
        // Home
        val homeLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            navigateToHome()
        }

        // Hoy
        val todayLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            navigateToToday()
        }

        // Configuración (deshabilitado por ahora)
        val settingsLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(2) as? LinearLayout
        settingsLayout?.setOnClickListener {
            Toast.makeText(context, getString(R.string.settings_coming_soon), Toast.LENGTH_SHORT).show()
        }

        // Establecer colores iniciales (ninguna activa en detalle)
        updateBottomNavigationColors("detalle")
    }

    private fun navigateToHome() {
        try {
            findNavController().navigate(R.id.action_itemDetailFragment_to_itemListFragment)
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.error_navigating_home, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToToday() {
        try {
            val bundle = Bundle().apply {
                putInt(DayDetailFragment.ARG_DAY_NUMBER, getCurrentDayNumber())
            }
            findNavController().navigate(R.id.action_itemListFragment_to_todayFragment, bundle)
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.error_navigating_today, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentDayNumber(): Int {
        // Obtener el día actual del desafío desde Firestore
        return desafio.diaActual
    }

    private fun updateBottomNavigationColors(activeTab: String) {
        val bottomNav = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)

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
            "detalle" -> {
                homeIcon?.setColorFilter(inactiveColor)
                homeText?.setTextColor(inactiveColor)
                todayIcon?.setColorFilter(inactiveColor)
                todayText?.setTextColor(inactiveColor)
            }
        }

        // Configuración siempre deshabilitada
        settingsIcon?.setColorFilter(disabledColor)
        settingsText?.setTextColor(disabledColor)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.challenge_context_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                val bundle = Bundle().apply {
                    putParcelable("desafio", desafio)
                }
                findNavController().navigate(R.id.action_itemDetailFragment_to_editDesafioFragment, bundle)
                true
            }
            R.id.action_delete -> {
                eliminarDesafio(desafio.id)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun eliminarDesafio(id: String) {
        val context = requireContext()

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(getString(R.string.delete_challenge))
            .setMessage(getString(R.string.delete_challenge_confirmation))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                firestore.collection("usuarios")
                    .document(uid)
                    .collection("desafios")
                    .document(id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, getString(R.string.challenge_deleted), Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_itemDetailFragment_to_itemListFragment)
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, getString(R.string.error_deleting_challenge), Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    companion object {
        const val ARG_ITEM_ID = "item_id"
    }
    override fun onResume() {
        super.onResume()
        // Solo recargar si no estamos ya actualizando
        if (!isUpdatingContent) {
            Log.d("ItemDetailFragment", "onResume: Recargando datos")
            cargarDiasCompletados()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}