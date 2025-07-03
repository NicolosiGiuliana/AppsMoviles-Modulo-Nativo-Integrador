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
import androidx.fragment.app.setFragmentResult
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

    companion object {
        private const val TAG = "EditDesafio"
        private const val COLLECTION_USUARIOS = "usuarios"
        private const val COLLECTION_DESAFIOS = "desafios"
        private const val COLLECTION_DIAS = "dias"
        private const val FIELD_DESCRIPCION = "descripcion"
        private const val FIELD_HABITOS = "habitos"
        private const val FIELD_ETIQUETAS = "etiquetas"
        private const val FIELD_NOMBRE = "nombre"
        private const val FIELD_COMPLETADO = "completado"
        private const val FIELD_FECHA_REALIZACION = "fechaRealizacion"


        private const val DEFAULT_HABIT_NAME = "Hábito sin nombre"
        private const val NEW_HABIT_PREFIX = "Nuevo hábito"
        private const val MIN_HABITS_REQUIRED = 3
    }

    private lateinit var desafio: ItemListFragment.Desafio
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var etDescripcion: EditText
    private lateinit var habitosContainer: LinearLayout
    private lateinit var btnAgregarHabito: FloatingActionButton
    private lateinit var btnGuardar: Button
    private lateinit var btnCancelar: Button

    private lateinit var chipGroupEtiquetas: ChipGroup
    private lateinit var btnAgregarEtiqueta: FloatingActionButton

    private val habitos = mutableListOf<HabitoItem>()
    private val etiquetas = mutableListOf<String>()

    private var cambiosRealizados = false
    private val habitosOriginales = mutableListOf<HabitoItem>()
    private val cambiosPendientes = mutableMapOf<Int, Boolean>()

    data class HabitoItem(
        var nombre: String,
        var completado: Boolean = false,
        var isNew: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            desafio = it.getParcelable("desafio")
                ?: throw IllegalStateException(getString(R.string.desafio_not_found_in_args))
        }
    }

    // Inflamos el layout del fragmento y configuramos las vistas.
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

    // Inicializa las vistas del fragmento.
    private fun initializeViews(view: View) {
        etDescripcion = view.findViewById(R.id.et_descripcion)
        habitosContainer = view.findViewById(R.id.habitos_container)
        btnAgregarHabito = view.findViewById(R.id.btn_agregar_habito)
        btnGuardar = view.findViewById(R.id.btn_guardar)
        btnCancelar = view.findViewById(R.id.btn_cancelar)

        chipGroupEtiquetas = view.findViewById(R.id.chip_group_etiquetas)
        btnAgregarEtiqueta = view.findViewById(R.id.btn_agregar_etiqueta)
    }

    // Configura los listeners de los botones y acciones de UI.
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

    // Revierte los cambios realizados en los hábitos a su estado original.
    private fun revertirCambios() {
        for (i in habitos.indices) {
            if (i < habitosOriginales.size) {
                habitos[i].completado = habitosOriginales[i].completado
            }
        }

        cambiosPendientes.clear()

        actualizarUIHabitos()
    }

    // Carga los datos del desafío desde Firestore y los muestra en la UI.
    private fun cargarDatosDesafio() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection(COLLECTION_USUARIOS)
            .document(uid)
            .collection(COLLECTION_DESAFIOS)
            .document(desafio.id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val descripcion = document.getString(FIELD_DESCRIPCION) ?: ""
                    etDescripcion.setText(descripcion)

                    val habitosBase =
                        document.get(FIELD_HABITOS) as? List<Map<String, Any>> ?: emptyList()
                    habitos.clear()

                    for (habitoMap in habitosBase) {

                        val nombre = habitoMap[FIELD_NOMBRE] as? String ?: DEFAULT_HABIT_NAME
                        val completado = habitoMap[FIELD_COMPLETADO] as? Boolean ?: false
                        habitos.add(HabitoItem(nombre, completado))
                    }

                    while (habitos.size < MIN_HABITS_REQUIRED) {
                        habitos.add(
                            HabitoItem(
                                "$NEW_HABIT_PREFIX ${habitos.size + 1}",
                                false,
                                true
                            )
                        )
                    }
                    val etiquetasBase =
                        document.get(FIELD_ETIQUETAS) as? List<String> ?: emptyList()
                    etiquetas.clear()
                    etiquetas.addAll(etiquetasBase)

                    actualizarUIHabitos()
                    actualizarUIEtiquetas()
                    cargarEstadoHabitosDelDiaActual()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al cargar datos: ${e.message}")
                Toast.makeText(context, getString(R.string.error_loading_data), Toast.LENGTH_SHORT)
                    .show()
            }
    }

    // Carga el estado de los hábitos para el día actual desde Firestore.
    private fun cargarEstadoHabitosDelDiaActual() {
        val uid = auth.currentUser?.uid ?: return
        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        firestore.collection(COLLECTION_USUARIOS)
            .document(uid)
            .collection(COLLECTION_DESAFIOS)
            .document(desafio.id)
            .collection(COLLECTION_DIAS)
            .whereEqualTo(FIELD_FECHA_REALIZACION, fechaHoy)
            .get()
            .addOnSuccessListener { diasSnapshot ->
                if (!diasSnapshot.isEmpty) {
                    val diaDoc = diasSnapshot.documents[0]
                    val habitosDelDia =
                        diaDoc.get(FIELD_HABITOS) as? List<Map<String, Any>> ?: emptyList()

                    for ((index, habitoDelDia) in habitosDelDia.withIndex()) {
                        if (index < habitos.size) {
                            val completado = habitoDelDia[FIELD_COMPLETADO] as? Boolean ?: false
                            habitos[index].completado = completado
                        }
                    }
                    habitosOriginales.clear()
                    habitosOriginales.addAll(habitos.map { it.copy() })

                    cambiosPendientes.clear()

                    actualizarUIHabitos()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al cargar estado de hábitos del día: ${e.message}")
            }
    }

    // Actualiza la UI para mostrar la lista de hábitos actuales.
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

            etNombre.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    habitos[index].nombre = etNombre.text.toString().trim()
                }
            }

            switchCompletado.setOnCheckedChangeListener { _, isChecked ->
                habitos[index].completado = isChecked
                cambiosPendientes[index] = isChecked
            }

            btnEliminar.setOnClickListener {
                if (habitos.size > MIN_HABITS_REQUIRED) {
                    mostrarDialogoEliminarHabito(index)
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.must_have_at_least_3_habits),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            btnEliminar.isEnabled = habitos.size > MIN_HABITS_REQUIRED
            btnEliminar.alpha = if (habitos.size > MIN_HABITS_REQUIRED) 1.0f else 0.5f

            habitosContainer.addView(habitoView)
        }
    }

    // Actualiza la UI para mostrar las etiquetas actuales.
    private fun actualizarUIEtiquetas() {
        chipGroupEtiquetas.removeAllViews()

        for (etiqueta in etiquetas) {
            val chip = Chip(requireContext()).apply {
                text = etiqueta
                isCloseIconVisible = true

                setChipBackgroundColorResource(R.color.primary_green)
                setTextColor(resources.getColor(android.R.color.white, null))
                setCloseIconTintResource(android.R.color.white)

                setOnCloseIconClickListener {
                    etiquetas.remove(etiqueta)
                    actualizarUIEtiquetas()
                }
            }
            chipGroupEtiquetas.addView(chip)
        }
    }

    // Muestra un diálogo para agregar un nuevo hábito.
    private fun mostrarDialogoAgregarHabito() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_agregar_habito, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.til_habito)
        val input = dialogView.findViewById<TextInputEditText>(R.id.et_habito)

        builder.setTitle(getString(R.string.add_habit_create))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isNotEmpty()) {
                    habitos.add(HabitoItem(nombre, false, true))
                    actualizarUIHabitos()
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.name_cannot_be_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Muestra un diálogo para agregar una nueva etiqueta.
    private fun mostrarDialogoAgregarEtiqueta() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_agregar_etiqueta, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.til_etiqueta)
        val input = dialogView.findViewById<TextInputEditText>(R.id.et_etiqueta)

        builder.setTitle(getString(R.string.add_tag))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val etiqueta = input.text.toString().trim()
                if (etiqueta.isNotEmpty()) {
                    if (!etiquetas.contains(etiqueta)) {
                        etiquetas.add(etiqueta)
                        actualizarUIEtiquetas()
                    } else {
                        Toast.makeText(
                            context,
                            getString(R.string.tag_already_exists),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.name_cannot_be_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Muestra un diálogo de confirmación para eliminar un hábito.
    private fun mostrarDialogoEliminarHabito(index: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_habit))
            .setMessage(getString(R.string.delete_habit_confirmation, habitos[index].nombre))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                habitos.removeAt(index)
                actualizarUIHabitos()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Valida los datos ingresados antes de guardar los cambios.
    private fun validarDatos(): Boolean {
        val descripcion = etDescripcion.text.toString().trim()
        if (descripcion.isEmpty()) {
            etDescripcion.error = getString(R.string.description_required)
            return false
        }

        if (habitos.size < MIN_HABITS_REQUIRED) {
            Toast.makeText(
                context,
                getString(R.string.must_have_at_least_3_habits),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        for (i in habitos.indices) {
            val habitoView = habitosContainer.getChildAt(i)
            val etNombre = habitoView.findViewById<EditText>(R.id.et_habito_nombre)
            val nombre = etNombre.text.toString().trim()

            if (nombre.isEmpty()) {
                etNombre.error = getString(R.string.habit_name_required)
                return false
            }
            habitos[i].nombre = nombre
        }
        return true
    }

    // Guarda los cambios realizados en el desafío y sus hábitos en Firestore.
    private fun guardarCambios() {
        val uid = auth.currentUser?.uid ?: return
        val descripcion = etDescripcion.text.toString().trim()

        val habitosMap = habitos.map { habito ->
            mapOf(
                FIELD_NOMBRE to habito.nombre,
                FIELD_COMPLETADO to habito.completado
            )
        }

        val updates = mapOf(
            FIELD_DESCRIPCION to descripcion,
            FIELD_HABITOS to habitosMap,
            FIELD_ETIQUETAS to etiquetas
        )

        val progressDialog = android.app.ProgressDialog(requireContext()).apply {
            setMessage(getString(R.string.saving_changes))
            setCancelable(false)
            show()
        }

        firestore.collection(COLLECTION_USUARIOS)
            .document(uid)
            .collection(COLLECTION_DESAFIOS)
            .document(desafio.id)
            .update(updates)
            .addOnSuccessListener {
                guardarCambiosHabitosDelDia {
                    progressDialog.dismiss()
                    actualizarDiasConNuevosHabitos(uid) {
                        cambiosRealizados = true

                        val bundle = Bundle().apply {
                            putBoolean("cambios_realizados", true)
                        }
                        setFragmentResult("desafio_editado", bundle)

                        Toast.makeText(
                            context,
                            getString(R.string.changes_saved_successfully),
                            Toast.LENGTH_SHORT
                        ).show()
                        findNavController().navigateUp()
                    }
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(
                    context,
                    getString(R.string.error_saving_changes),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Guarda los cambios de estado de los hábitos para el día actual en Firestore.
    private fun guardarCambiosHabitosDelDia(callback: () -> Unit) {
        if (cambiosPendientes.isEmpty()) {
            callback()
            return
        }

        val uid = auth.currentUser?.uid ?: return
        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        firestore.collection(COLLECTION_USUARIOS)
            .document(uid)
            .collection(COLLECTION_DESAFIOS)
            .document(desafio.id)
            .collection(COLLECTION_DIAS)
            .whereEqualTo(FIELD_FECHA_REALIZACION, fechaHoy)
            .get()
            .addOnSuccessListener { diasSnapshot ->
                if (!diasSnapshot.isEmpty) {
                    val diaDoc = diasSnapshot.documents[0]
                    val habitosDelDia = (diaDoc.get(FIELD_HABITOS) as? List<Map<String, Any>>
                        ?: emptyList()).toMutableList()

                    for ((index, nuevoEstado) in cambiosPendientes) {
                        if (index < habitosDelDia.size) {
                            val habitoActualizado = habitosDelDia[index].toMutableMap()
                            habitoActualizado[FIELD_COMPLETADO] = nuevoEstado
                            habitosDelDia[index] = habitoActualizado
                        }
                    }

                    val todosCompletados =
                        habitosDelDia.all { (it[FIELD_COMPLETADO] as? Boolean) == true }

                    val updatesDelDia = mapOf(
                        FIELD_HABITOS to habitosDelDia,
                        FIELD_COMPLETADO to todosCompletados
                    )

                    diaDoc.reference.update(updatesDelDia)
                        .addOnSuccessListener {
                            cambiosPendientes.clear()
                            callback()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error al guardar cambios de hábitos: ${e.message}")
                            callback()
                        }
                } else {
                    callback()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al buscar día actual: ${e.message}")
                callback()
            }
    }

    // Actualiza todos los días del desafío con la nueva lista de hábitos.
    private fun actualizarDiasConNuevosHabitos(uid: String, callback: () -> Unit) {
        firestore.collection(COLLECTION_USUARIOS)
            .document(uid)
            .collection(COLLECTION_DESAFIOS)
            .document(desafio.id)
            .collection(COLLECTION_DIAS)
            .get()
            .addOnSuccessListener { diasSnapshot ->
                val batch = firestore.batch()

                for (diaDoc in diasSnapshot.documents) {
                    val habitosActuales =
                        diaDoc.get(FIELD_HABITOS) as? List<Map<String, Any>> ?: emptyList()
                    val nuevosHabitos = mutableListOf<Map<String, Any>>()

                    for ((index, nuevoHabito) in habitos.withIndex()) {
                        val habitoExistente = habitosActuales.getOrNull(index)

                        if (habitoExistente != null) {
                            nuevosHabitos.add(
                                mapOf(
                                    FIELD_NOMBRE to nuevoHabito.nombre,
                                    FIELD_COMPLETADO to (habitoExistente[FIELD_COMPLETADO] as? Boolean
                                        ?: false)
                                )
                            )
                        } else {
                            nuevosHabitos.add(
                                mapOf(
                                    FIELD_NOMBRE to nuevoHabito.nombre,
                                    FIELD_COMPLETADO to nuevoHabito.completado
                                )
                            )
                        }
                    }

                    val diaRef = firestore.collection(COLLECTION_USUARIOS)
                        .document(uid)
                        .collection(COLLECTION_DESAFIOS)
                        .document(desafio.id)
                        .collection(COLLECTION_DIAS)
                        .document(diaDoc.id)

                    batch.update(diaRef, FIELD_HABITOS, nuevosHabitos)
                }

                batch.commit()
                    .addOnSuccessListener {
                        callback()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al actualizar días: ${e.message}")
                        callback()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener días: ${e.message}")
                callback()
            }
    }

    // Notifica si se realizaron cambios al destruir la vista.
    override fun onDestroyView() {
        super.onDestroyView()

        if (cambiosRealizados) {
            val bundle = Bundle().apply {
                putBoolean("cambios_realizados", true)
            }
            setFragmentResult("desafio_editado", bundle)
        }
    }
}