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
        habitosContainer = view.findViewById(R.id.habitos_container)
        btnAgregarHabito = view.findViewById(R.id.btn_agregar_habito)
        btnGuardar = view.findViewById(R.id.btn_guardar)
        btnCancelar = view.findViewById(R.id.btn_cancelar)

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
        for (i in habitos.indices) {
            if (i < habitosOriginales.size) {
                habitos[i].completado = habitosOriginales[i].completado
            }
        }

        cambiosPendientes.clear()

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
                    val descripcion = document.getString("descripcion") ?: ""
                    etDescripcion.setText(descripcion)

                    val habitosBase = document.get("habitos") as? List<Map<String, Any>> ?: emptyList()
                    habitos.clear()

                    for (habitoMap in habitosBase) {
                        val nombre = habitoMap["nombre"] as? String ?: "Hábito sin nombre"
                        val completado = habitoMap["completado"] as? Boolean ?: false
                        habitos.add(HabitoItem(nombre, completado))
                    }

                    while (habitos.size < 3) {
                        habitos.add(HabitoItem("Nuevo hábito ${habitos.size + 1}", false, true))
                    }

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

                    for ((index, habitoDelDia) in habitosDelDia.withIndex()) {
                        if (index < habitos.size) {
                            val completado = habitoDelDia["completado"] as? Boolean ?: false
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
                if (habitos.size > 3) {
                    mostrarDialogoEliminarHabito(index)
                } else {
                    Toast.makeText(context, "Debe haber al menos 3 hábitos", Toast.LENGTH_SHORT).show()
                }
            }

            btnEliminar.isEnabled = habitos.size > 3
            btnEliminar.alpha = if (habitos.size > 3) 1.0f else 0.5f

            habitosContainer.addView(habitoView)
        }
    }


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
        val descripcion = etDescripcion.text.toString().trim()
        if (descripcion.isEmpty()) {
            etDescripcion.error = "La descripción es obligatoria"
            return false
        }

        if (habitos.size < 3) {
            Toast.makeText(context, "Debe haber al menos 3 hábitos", Toast.LENGTH_SHORT).show()
            return false
        }

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

        val habitosMap = habitos.map { habito ->
            mapOf(
                "nombre" to habito.nombre,
                "completado" to habito.completado
            )
        }

        val updates = mapOf(
            "descripcion" to descripcion,
            "habitos" to habitosMap,
            "etiquetas" to etiquetas
        )

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
                guardarCambiosHabitosDelDia {
                    progressDialog.dismiss()
                    actualizarDiasConNuevosHabitos(uid) {
                        cambiosRealizados = true

                        val bundle = Bundle().apply {
                            putBoolean("cambios_realizados", true)
                        }
                        setFragmentResult("desafio_editado", bundle)

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

                    for ((index, nuevoEstado) in cambiosPendientes) {
                        if (index < habitosDelDia.size) {
                            val habitoActualizado = habitosDelDia[index].toMutableMap()
                            habitoActualizado["completado"] = nuevoEstado
                            habitosDelDia[index] = habitoActualizado
                        }
                    }

                    val todosCompletados = habitosDelDia.all { (it["completado"] as? Boolean) == true }

                    val updatesDelDia = mapOf(
                        "habitos" to habitosDelDia,
                        "completado" to todosCompletados
                    )

                    diaDoc.reference.update(updatesDelDia)
                        .addOnSuccessListener {
                            Log.d("EditDesafio", "Día actualizado: completado = $todosCompletados")
                            cambiosPendientes.clear()
                            callback()
                        }
                        .addOnFailureListener { e ->
                            Log.e("EditDesafio", "Error al guardar cambios de hábitos: ${e.message}")
                            callback()
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

    private fun actualizarDiasConNuevosHabitos(uid: String, callback: () -> Unit) {
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

                    for ((index, nuevoHabito) in habitos.withIndex()) {
                        val habitoExistente = habitosActuales.getOrNull(index)

                        if (habitoExistente != null) {
                            nuevosHabitos.add(mapOf(
                                "nombre" to nuevoHabito.nombre,
                                "completado" to (habitoExistente["completado"] as? Boolean ?: false)
                            ))
                        } else {
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

                batch.commit()
                    .addOnSuccessListener {
                        callback()
                    }
                    .addOnFailureListener { e ->
                        Log.e("EditDesafio", "Error al actualizar días: ${e.message}")
                        callback()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("EditDesafio", "Error al obtener días: ${e.message}")
                callback()
            }
    }

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