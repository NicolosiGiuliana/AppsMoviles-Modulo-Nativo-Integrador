package com.example.trabajointegradornativo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditDesafioFragment : Fragment() {

    private lateinit var desafio: ItemListFragment.Desafio
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // UI Components
    private lateinit var etDescripcion: EditText
    private lateinit var etDuracion: EditText
    private lateinit var habitosContainer: LinearLayout
    private lateinit var btnAgregarHabito: FloatingActionButton
    private lateinit var btnGuardar: Button
    private lateinit var btnCancelar: Button

    // Nuevos componentes para etiquetas
    private lateinit var chipGroupEtiquetas: ChipGroup
    private lateinit var btnAgregarEtiqueta: FloatingActionButton

    // Data
    private val habitos = mutableListOf<HabitoItem>()
    private val etiquetas = mutableListOf<String>()

    // AGREGAR estas variables:
    private val habitosOriginales = mutableListOf<HabitoItem>() // Estado original de los hábitos
    private val cambiosPendientes = mutableMapOf<Int, Boolean>() // Cambios temporales por índice

    data class HabitoItem(
        var nombre: String,
        var completado: Boolean = false,
        var isNew: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            desafio = it.getParcelable("desafio") ?: throw IllegalStateException("Desafio no encontrado")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit_desafio, container, false)

        initializeViews(view)
        setupListeners()
        cargarDatosDesafio()

        return view
    }

    private fun initializeViews(view: View) {
        etDescripcion = view.findViewById(R.id.et_descripcion)
        etDuracion = view.findViewById(R.id.et_duracion)
        habitosContainer = view.findViewById(R.id.habitos_container)
        btnAgregarHabito = view.findViewById(R.id.btn_agregar_habito)
        btnGuardar = view.findViewById(R.id.btn_guardar)
        btnCancelar = view.findViewById(R.id.btn_cancelar)

        // Inicializar componentes de etiquetas
        chipGroupEtiquetas = view.findViewById(R.id.chip_group_etiquetas)
        btnAgregarEtiqueta = view.findViewById(R.id.btn_agregar_etiqueta)
    }

    private fun setupListeners() {
        btnAgregarHabito.setOnClickListener {
            mostrarDialogoAgregarHabito()
        }

        btnAgregarEtiqueta.setOnClickListener {
            mostrarDialogoAgregarEtiqueta()
        }

        btnGuardar.setOnClickListener {
            if (validarDatos()) {
                guardarCambios()
            }
        }

        btnCancelar.setOnClickListener {
            revertirCambios()
            findNavController().navigateUp()
        }
    }

    private fun revertirCambios() {
        // Restaurar estados originales
        for (i in habitos.indices) {
            if (i < habitosOriginales.size) {
                habitos[i].completado = habitosOriginales[i].completado
            }
        }

        // Limpiar cambios pendientes
        cambiosPendientes.clear()

        // Actualizar UI para mostrar estados originales
        actualizarUIHabitos()
    }

    private fun cargarDatosDesafio() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Cargar descripción
                    val descripcion = document.getString("descripcion") ?: ""
                    etDescripcion.setText(descripcion)

                    // Cargar duración
                    val dias = document.getLong("dias")?.toInt() ?: 0
                    etDuracion.setText(dias.toString())

                    // Cargar hábitos
                    val habitosBase = document.get("habitos") as? List<Map<String, Any>> ?: emptyList()
                    habitos.clear()

                    for (habitoMap in habitosBase) {
                        val nombre = habitoMap["nombre"] as? String ?: "Hábito sin nombre"
                        val completado = habitoMap["completado"] as? Boolean ?: false
                        habitos.add(HabitoItem(nombre, completado))
                    }

                    // Asegurar mínimo 3 hábitos
                    while (habitos.size < 3) {
                        habitos.add(HabitoItem("Nuevo hábito ${habitos.size + 1}", false, true))
                    }

                    // Cargar etiquetas
                    val etiquetasBase = document.get("etiquetas") as? List<String> ?: emptyList()
                    etiquetas.clear()
                    etiquetas.addAll(etiquetasBase)

                    actualizarUIHabitos()
                    actualizarUIEtiquetas()
                    cargarEstadoHabitosDelDiaActual()
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditDesafio", "Error al cargar datos: ${e.message}")
                Toast.makeText(context, "Error al cargar los datos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarEstadoHabitosDelDiaActual() {
        val uid = auth.currentUser?.uid ?: return
        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .whereEqualTo("fechaRealizacion", fechaHoy)
            .get()
            .addOnSuccessListener { diasSnapshot ->
                if (!diasSnapshot.isEmpty) {
                    val diaDoc = diasSnapshot.documents[0]
                    val habitosDelDia = diaDoc.get("habitos") as? List<Map<String, Any>> ?: emptyList()

                    // Actualizar el estado de los hábitos con los datos del día actual
                    for ((index, habitoDelDia) in habitosDelDia.withIndex()) {
                        if (index < habitos.size) {
                            val completado = habitoDelDia["completado"] as? Boolean ?: false
                            habitos[index].completado = completado
                        }
                    }
                    habitosOriginales.clear()
                    habitosOriginales.addAll(habitos.map { it.copy() })

                    // Limpiar cambios pendientes
                    cambiosPendientes.clear()

                    // Refrescar la UI con los estados actualizados
                    actualizarUIHabitos()
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditDesafio", "Error al cargar estado de hábitos del día: ${e.message}")
            }
    }

    private fun actualizarUIHabitos() {
        habitosContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        for ((index, habito) in habitos.withIndex()) {
            val habitoView = inflater.inflate(R.layout.item_edit_habito, habitosContainer, false)

            val etNombre = habitoView.findViewById<EditText>(R.id.et_habito_nombre)
            val switchCompletado = habitoView.findViewById<CheckBox>(R.id.checkbox_completado)
            val btnEliminar = habitoView.findViewById<ImageButton>(R.id.btn_eliminar_habito)

            etNombre.setText(habito.nombre)
            switchCompletado.isChecked = habito.completado

            // Listener para cambios en el nombre
            etNombre.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    habitos[index].nombre = etNombre.text.toString().trim()
                }
            }

            // Listener para cambios en el estado - MEJORADO
            switchCompletado.setOnCheckedChangeListener { _, isChecked ->
                habitos[index].completado = isChecked
                // NO guardar inmediatamente, solo marcar como cambio pendiente
                cambiosPendientes[index] = isChecked
            }

            // Listener para eliminar hábito
            btnEliminar.setOnClickListener {
                if (habitos.size > 3) {
                    mostrarDialogoEliminarHabito(index)
                } else {
                    Toast.makeText(context, "Debe haber al menos 3 hábitos", Toast.LENGTH_SHORT).show()
                }
            }

            // Deshabilitar eliminación si solo hay 3 hábitos
            btnEliminar.isEnabled = habitos.size > 3
            btnEliminar.alpha = if (habitos.size > 3) 1.0f else 0.5f

            habitosContainer.addView(habitoView)
        }
    }

//    // NUEVO MeTODO: Guardar estado del hábito inmediatamente
//    private fun guardarEstadoHabito(habitoIndex: Int, completado: Boolean) {
//        val uid = auth.currentUser?.uid ?: return
//
//        // Preparar los datos actualizados de todos los hábitos
//        val habitosMap = habitos.mapIndexed { index, habito ->
//            mapOf(
//                "nombre" to habito.nombre,
//                "completado" to if (index == habitoIndex) completado else habito.completado
//            )
//        }
//
//        // Actualizar en el documento principal del desafío
//        firestore.collection("usuarios")
//            .document(uid)
//            .collection("desafios")
//            .document(desafio.id)
//            .update("habitos", habitosMap)
//            .addOnSuccessListener {
//                Log.d("EditDesafio", "Estado del hábito actualizado correctamente")
//                // Opcional: mostrar un pequeño feedback visual
//                // Toast.makeText(context, "Estado guardado", Toast.LENGTH_SHORT).show()
//            }
//            .addOnFailureListener { e ->
//                Log.e("EditDesafio", "Error al actualizar estado del hábito: ${e.message}")
//                // Revertir el cambio en la UI si falla
//                habitos[habitoIndex].completado = !completado
//                actualizarUIHabitos()
//                Toast.makeText(context, "Error al guardar el estado", Toast.LENGTH_SHORT).show()
//            }
//        actualizarHabitoEnDiaActual(habitoIndex, completado)
//    }

    private fun actualizarHabitoEnDiaActual(habitoIndex: Int, completado: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .whereEqualTo("fechaRealizacion", fechaHoy)
            .get()
            .addOnSuccessListener { diasSnapshot ->
                if (!diasSnapshot.isEmpty) {
                    val diaDoc = diasSnapshot.documents[0]
                    val habitosDelDia = (diaDoc.get("habitos") as? List<Map<String, Any>> ?: emptyList()).toMutableList()

                    if (habitoIndex < habitosDelDia.size) {
                        val habitoActualizado = habitosDelDia[habitoIndex].toMutableMap()
                        habitoActualizado["completado"] = completado
                        habitosDelDia[habitoIndex] = habitoActualizado

                        diaDoc.reference.update("habitos", habitosDelDia)
                    }
                }
            }
    }

    private fun actualizarUIEtiquetas() {
        chipGroupEtiquetas.removeAllViews()

        for (etiqueta in etiquetas) {
            val chip = Chip(requireContext()).apply {
                text = etiqueta
                isCloseIconVisible = true

                // Configurar estilo del chip
                setChipBackgroundColorResource(R.color.primary_green)
                setTextColor(resources.getColor(android.R.color.white, null))
                setCloseIconTintResource(android.R.color.white)

                // Listener para eliminar etiqueta
                setOnCloseIconClickListener {
                    etiquetas.remove(etiqueta)
                    actualizarUIEtiquetas()
                }
            }
            chipGroupEtiquetas.addView(chip)
        }
    }

    private fun mostrarDialogoAgregarHabito() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_agregar_habito, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.til_habito)
        val input = dialogView.findViewById<TextInputEditText>(R.id.et_habito)

        builder.setTitle("Agregar Hábito")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isNotEmpty()) {
                    habitos.add(HabitoItem(nombre, false, true))
                    actualizarUIHabitos()
                } else {
                    Toast.makeText(context, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoAgregarEtiqueta() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_agregar_etiqueta, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.til_etiqueta)
        val input = dialogView.findViewById<TextInputEditText>(R.id.et_etiqueta)

        builder.setTitle("Agregar Etiqueta")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val etiqueta = input.text.toString().trim()
                if (etiqueta.isNotEmpty()) {
                    if (!etiquetas.contains(etiqueta)) {
                        etiquetas.add(etiqueta)
                        actualizarUIEtiquetas()
                    } else {
                        Toast.makeText(context, "Esta etiqueta ya existe", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoEliminarHabito(index: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Hábito")
            .setMessage("¿Estás seguro de que quieres eliminar '${habitos[index].nombre}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                habitos.removeAt(index)
                actualizarUIHabitos()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun validarDatos(): Boolean {
        // Validar descripción
        val descripcion = etDescripcion.text.toString().trim()
        if (descripcion.isEmpty()) {
            etDescripcion.error = "La descripción es obligatoria"
            return false
        }

        // Validar duración
        val duracionStr = etDuracion.text.toString().trim()
        if (duracionStr.isEmpty()) {
            etDuracion.error = "La duración es obligatoria"
            return false
        }

        val duracion = duracionStr.toIntOrNull()
        if (duracion == null || duracion < 1) {
            etDuracion.error = "La duración debe ser un número mayor a 0"
            return false
        }

        // Validar hábitos
        if (habitos.size < 3) {
            Toast.makeText(context, "Debe haber al menos 3 hábitos", Toast.LENGTH_SHORT).show()
            return false
        }

        // Actualizar nombres de hábitos desde los EditText
        for (i in habitos.indices) {
            val habitoView = habitosContainer.getChildAt(i)
            val etNombre = habitoView.findViewById<EditText>(R.id.et_habito_nombre)
            val nombre = etNombre.text.toString().trim()

            if (nombre.isEmpty()) {
                etNombre.error = "El nombre del hábito es obligatorio"
                return false
            }

            habitos[i].nombre = nombre
        }

        return true
    }

    private fun guardarCambios() {
        val uid = auth.currentUser?.uid ?: return
        val descripcion = etDescripcion.text.toString().trim()
        val duracion = etDuracion.text.toString().toInt()

        // Preparar datos para actualizar
        val habitosMap = habitos.map { habito ->
            mapOf(
                "nombre" to habito.nombre,
                "completado" to habito.completado
            )
        }

        val updates = mapOf(
            "descripcion" to descripcion,
            "dias" to duracion,
            "habitos" to habitosMap,
            "etiquetas" to etiquetas
        )

        // Mostrar progress
        val progressDialog = android.app.ProgressDialog(requireContext()).apply {
            setMessage("Guardando cambios...")
            setCancelable(false)
            show()
        }

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .update(updates)
            .addOnSuccessListener {
                // AGREGAR: Guardar cambios de hábitos del día actual
                guardarCambiosHabitosDelDia {
                    progressDialog.dismiss()
                    actualizarDiasConNuevosHabitos(uid, duracion) {
                        Toast.makeText(context, "Cambios guardados exitosamente", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Log.e("EditDesafio", "Error al guardar: ${e.message}")
                Toast.makeText(context, "Error al guardar los cambios", Toast.LENGTH_SHORT).show()
            }
    }

    private fun guardarCambiosHabitosDelDia(callback: () -> Unit) {
        if (cambiosPendientes.isEmpty()) {
            callback()
            return
        }

        val uid = auth.currentUser?.uid ?: return
        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .whereEqualTo("fechaRealizacion", fechaHoy)
            .get()
            .addOnSuccessListener { diasSnapshot ->
                if (!diasSnapshot.isEmpty) {
                    val diaDoc = diasSnapshot.documents[0]
                    val habitosDelDia = (diaDoc.get("habitos") as? List<Map<String, Any>> ?: emptyList()).toMutableList()

                    // Aplicar todos los cambios pendientes
                    for ((index, nuevoEstado) in cambiosPendientes) {
                        if (index < habitosDelDia.size) {
                            val habitoActualizado = habitosDelDia[index].toMutableMap()
                            habitoActualizado["completado"] = nuevoEstado
                            habitosDelDia[index] = habitoActualizado
                        }
                    }

                    diaDoc.reference.update("habitos", habitosDelDia)
                        .addOnSuccessListener {
                            cambiosPendientes.clear()
                            callback()
                        }
                        .addOnFailureListener { e ->
                            Log.e("EditDesafio", "Error al guardar cambios de hábitos: ${e.message}")
                            callback() // Continuar aunque falle
                        }
                } else {
                    callback()
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditDesafio", "Error al buscar día actual: ${e.message}")
                callback()
            }
    }

    private fun actualizarDiasConNuevosHabitos(uid: String, nuevaDuracion: Int, callback: () -> Unit) {
        // Obtener todos los días del desafío
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .get()
            .addOnSuccessListener { diasSnapshot ->
                val batch = firestore.batch()

                for (diaDoc in diasSnapshot.documents) {
                    val habitosActuales = diaDoc.get("habitos") as? List<Map<String, Any>> ?: emptyList()
                    val nuevosHabitos = mutableListOf<Map<String, Any>>()

                    // Mantener hábitos existentes y agregar nuevos
                    for ((index, nuevoHabito) in habitos.withIndex()) {
                        val habitoExistente = habitosActuales.getOrNull(index)

                        if (habitoExistente != null) {
                            // Mantener el estado del hábito existente pero actualizar el nombre
                            nuevosHabitos.add(mapOf(
                                "nombre" to nuevoHabito.nombre,
                                "completado" to (habitoExistente["completado"] as? Boolean ?: false)
                            ))
                        } else {
                            // Nuevo hábito - usar el estado del hábito principal
                            nuevosHabitos.add(mapOf(
                                "nombre" to nuevoHabito.nombre,
                                "completado" to nuevoHabito.completado
                            ))
                        }
                    }

                    val diaRef = firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafio.id)
                        .collection("dias")
                        .document(diaDoc.id)

                    batch.update(diaRef, "habitos", nuevosHabitos)
                }

                // También actualizar la duración en el documento principal si cambió
                val desafioRef = firestore.collection("usuarios")
                    .document(uid)
                    .collection("desafios")
                    .document(desafio.id)

                batch.update(desafioRef, "dias", nuevaDuracion)

                batch.commit()
                    .addOnSuccessListener {
                        callback()
                    }
                    .addOnFailureListener { e ->
                        Log.e("EditDesafio", "Error al actualizar días: ${e.message}")
                        callback() // Continuar aunque falle la actualización de días
                    }
            }
            .addOnFailureListener { e ->
                Log.e("EditDesafio", "Error al obtener días: ${e.message}")
                callback() // Continuar aunque falle
            }
    }
}