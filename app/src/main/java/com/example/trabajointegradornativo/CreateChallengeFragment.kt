package com.example.trabajointegradornativo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateChallengeFragment : Fragment(), LocationHelper.LocationCallback {

    private lateinit var nombreInput: EditText
    private lateinit var descripcionInput: EditText
    private lateinit var habitoInput1: EditText
    private lateinit var habitoInput2: EditText
    private lateinit var habitoInput3: EditText
    private lateinit var crearButton: Button
    private lateinit var cancelarButton: TextView
    private lateinit var agregarHabitoButton: TextView

    private lateinit var option30Days: TextView
    private lateinit var option45Days: TextView
    private lateinit var option75Days: TextView

    private lateinit var layoutSelectLocation: LinearLayout
    private lateinit var textUbicacionSeleccionada: TextView
    private lateinit var buttonObtenerUbicacion: Button
    private lateinit var buttonEliminarUbicacion: Button
    private lateinit var checkboxUbicacionOpcional: CheckBox

    private lateinit var inputNewTag: EditText
    private lateinit var buttonAddTag: Button
    private lateinit var tagsContainer: LinearLayout

    private lateinit var radioGroupVisibility: RadioGroup
    private lateinit var radioPublic: RadioButton
    private lateinit var radioPrivate: RadioButton

    private var duracionSeleccionada = 30
    private val habitosAdicionales = mutableListOf<EditText>()
    private var ubicacionSeleccionada: String? = null
    private var latitudSeleccionada: Double? = null
    private var longitudSeleccionada: Double? = null

    private val etiquetasAgregadas = mutableListOf<String>()
    private var esPublico = true

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var locationHelper: LocationHelper

    companion object {
        private const val TYPE_CUSTOM = "personalizado"
        private const val STATUS_ACTIVE = "activo"
        private const val VISIBILITY_PUBLIC = "publico"
        private const val VISIBILITY_PRIVATE = "privado"
        private const val DEFAULT_AUTHOR = "Usuario"
        private const val DAY_PREFIX = "dia_"
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                obtenerUbicacionActual()
            }

            else -> {
                Toast.makeText(
                    context,
                    getString(R.string.location_permissions_denied),
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    // Infla el layout del fragmento y configura vistas/eventos
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_create_challenge, container, false)

        locationHelper = LocationHelper(requireContext())

        inicializarViews(view)
        configurarEventos()

        return view
    }

    // Inicializa las vistas del fragmento
    private fun inicializarViews(view: View) {
        nombreInput = view.findViewById(R.id.inputChallengeName)
        descripcionInput = view.findViewById(R.id.inputChallengeDescription)
        habitoInput1 = view.findViewById(R.id.inputHabit1)
        habitoInput2 = view.findViewById(R.id.inputHabit2)
        habitoInput3 = view.findViewById(R.id.inputHabit3)

        crearButton = view.findViewById(R.id.buttonCreateChallenge)
        cancelarButton = view.findViewById(R.id.buttonCancel)
        agregarHabitoButton = view.findViewById(R.id.buttonAddHabit)

        option30Days = view.findViewById(R.id.option30Days)
        option45Days = view.findViewById(R.id.option45Days)
        option75Days = view.findViewById(R.id.option75Days)

        layoutSelectLocation = view.findViewById(R.id.layoutSelectLocation)
        textUbicacionSeleccionada = view.findViewById(R.id.textUbicacionSeleccionada)
        buttonObtenerUbicacion = view.findViewById(R.id.buttonObtenerUbicacion)
        buttonEliminarUbicacion = view.findViewById(R.id.buttonEliminarUbicacion)
        checkboxUbicacionOpcional = view.findViewById(R.id.checkboxUbicacionOpcional)

        inputNewTag = view.findViewById(R.id.inputNewTag)
        buttonAddTag = view.findViewById(R.id.buttonAddTag)
        tagsContainer = view.findViewById(R.id.tagsContainer)

        radioGroupVisibility = view.findViewById(R.id.radioGroupVisibility)
        radioPublic = view.findViewById(R.id.radioPublic)
        radioPrivate = view.findViewById(R.id.radioPrivate)

        seleccionarDuracion(option30Days, 30)

        actualizarEstadoUbicacion()
        if (esPublico) {
            checkboxUbicacionOpcional.visibility = View.GONE
        }
    }

    // Configura los listeners y eventos de los componentes
    private fun configurarEventos() {
        option30Days.setOnClickListener { seleccionarDuracion(option30Days, 30) }
        option45Days.setOnClickListener { seleccionarDuracion(option45Days, 45) }
        option75Days.setOnClickListener { seleccionarDuracion(option75Days, 75) }

        agregarHabitoButton.setOnClickListener { agregarNuevoHabito() }

        buttonAddTag.setOnClickListener { agregarEtiqueta() }
        inputNewTag.setOnEditorActionListener { _, _, _ ->
            agregarEtiqueta()
            true
        }

        radioGroupVisibility.setOnCheckedChangeListener { _, checkedId ->
            esPublico = checkedId == R.id.radioPublic

            if (esPublico) {
                checkboxUbicacionOpcional.visibility = View.GONE
                checkboxUbicacionOpcional.isChecked = false
                layoutSelectLocation.visibility = View.GONE
                limpiarUbicacion()
            } else {
                checkboxUbicacionOpcional.visibility = View.VISIBLE
            }
        }

        checkboxUbicacionOpcional.setOnCheckedChangeListener { _, isChecked ->
            layoutSelectLocation.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                limpiarUbicacion()
            }
        }

        buttonObtenerUbicacion.setOnClickListener { solicitarUbicacion() }
        buttonEliminarUbicacion.setOnClickListener { limpiarUbicacion() }

        crearButton.setOnClickListener { guardarDesafio() }
        cancelarButton.setOnClickListener { cancelarCreacion() }
    }

    // Agrega una nueva etiqueta a la lista y la muestra
    private fun agregarEtiqueta() {
        val nuevaEtiqueta = inputNewTag.text.toString().trim()

        when {
            nuevaEtiqueta.isEmpty() -> {
                Toast.makeText(context, getString(R.string.write_tag), Toast.LENGTH_SHORT).show()
                return
            }

            nuevaEtiqueta.length > 20 -> {
                Toast.makeText(context, getString(R.string.tag_max_length), Toast.LENGTH_SHORT)
                    .show()
                return
            }

            etiquetasAgregadas.contains(nuevaEtiqueta.lowercase()) -> {
                Toast.makeText(context, getString(R.string.tag_already_added), Toast.LENGTH_SHORT)
                    .show()
                return
            }

            etiquetasAgregadas.size >= 10 -> {
                Toast.makeText(context, getString(R.string.max_tags_limit), Toast.LENGTH_SHORT)
                    .show()
                return
            }
        }

        etiquetasAgregadas.add(nuevaEtiqueta)
        crearVistaEtiqueta(nuevaEtiqueta)
        inputNewTag.text.clear()
    }

    // Crea la vista visual de una etiqueta agregada
    private fun crearVistaEtiqueta(etiqueta: String) {
        val etiquetaView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.duration_selected_background)
            setPadding(24, 16, 16, 16)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 16
                bottomMargin = 8
            }
        }

        val textoEtiqueta = TextView(requireContext()).apply {
            text = etiqueta
            textSize = 14f
            setTextColor(resources.getColor(R.color.primary_green, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
        }

        val botonEliminar = TextView(requireContext()).apply {
            text = getString(R.string.remove_tag_symbol)
            textSize = 16f
            setTextColor(resources.getColor(R.color.primary_green, null))
            setPadding(8, 0, 0, 0)
            setOnClickListener {
                eliminarEtiqueta(etiqueta, etiquetaView)
            }
        }

        etiquetaView.addView(textoEtiqueta)
        etiquetaView.addView(botonEliminar)
        tagsContainer.addView(etiquetaView)
    }

    // Elimina una etiqueta de la lista y de la vista
    private fun eliminarEtiqueta(etiqueta: String, vista: View) {
        etiquetasAgregadas.remove(etiqueta)
        tagsContainer.removeView(vista)
    }

    // Selecciona la duración del desafío y actualiza el estilo
    private fun seleccionarDuracion(opcionSeleccionada: TextView, dias: Int) {
        resetearOpcionesDuracion()

        opcionSeleccionada.setBackgroundResource(R.drawable.duration_selected_background)
        opcionSeleccionada.setTextColor(resources.getColor(R.color.primary_green, null))

        duracionSeleccionada = dias
    }

    // Restaura el estilo de las opciones de duración
    private fun resetearOpcionesDuracion() {
        val opciones = listOf(option30Days, option45Days, option75Days)
        opciones.forEach { opcion ->
            opcion.setBackgroundResource(R.drawable.duration_unselected_background)
            opcion.setTextColor(resources.getColor(R.color.gray_text, null))
        }
    }

    // Agrega un nuevo campo de hábito adicional
    private fun agregarNuevoHabito() {
        val containerPadre = view?.findViewById<LinearLayout>(R.id.habitosContainer)

        if (habitosAdicionales.size < 2) {
            val nuevoHabito = EditText(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.edittext_height)
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.margin_small)
                }
                hint = getString(R.string.habit_name_default) + " ${4 + habitosAdicionales.size}"
                setBackgroundResource(R.drawable.edittext_background)
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.padding_medium),
                    resources.getDimensionPixelSize(R.dimen.padding_medium),
                    resources.getDimensionPixelSize(R.dimen.padding_medium),
                    resources.getDimensionPixelSize(R.dimen.padding_medium)
                )
                setHintTextColor(resources.getColor(R.color.gray_hint, null))
            }

            habitosAdicionales.add(nuevoHabito)

            val indexBotonAgregar = containerPadre?.indexOfChild(agregarHabitoButton) ?: 0
            containerPadre?.addView(nuevoHabito, indexBotonAgregar)

            if (habitosAdicionales.size >= 2) {
                agregarHabitoButton.visibility = View.GONE
            }
        }
    }

    // Solicita la ubicación actual del usuario
    private fun solicitarUbicacion() {
        if (tienePermisosUbicacion()) {
            obtenerUbicacionActual()
        } else {
            solicitarPermisosUbicacion()
        }
    }

    // Verifica si los permisos de ubicación están concedidos
    private fun tienePermisosUbicacion(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    // Solicita permisos de ubicación al usuario
    private fun solicitarPermisosUbicacion() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Obtiene la ubicación actual usando LocationHelper
    private fun obtenerUbicacionActual() {
        buttonObtenerUbicacion.text = getString(R.string.getting_location)
        buttonObtenerUbicacion.isEnabled = false

        locationHelper.getCurrentLocation(this)
    }

    // Callback: se recibe la ubicación correctamente
    override fun onLocationReceived(latitude: Double, longitude: Double, address: String) {
        latitudSeleccionada = latitude
        longitudSeleccionada = longitude
        ubicacionSeleccionada = address

        textUbicacionSeleccionada.text = address
        actualizarEstadoUbicacion()

        buttonObtenerUbicacion.text = getString(R.string.get_my_location)
        buttonObtenerUbicacion.isEnabled = true

        Toast.makeText(
            context,
            getString(R.string.location_obtained_successfully),
            Toast.LENGTH_SHORT
        ).show()
    }

    // Callback: ocurre un error al obtener la ubicación
    override fun onLocationError(error: String) {
        Toast.makeText(context, getString(R.string.error_format, error), Toast.LENGTH_LONG).show()

        buttonObtenerUbicacion.text = getString(R.string.get_my_location)
        buttonObtenerUbicacion.isEnabled = true
    }

    // Limpia la ubicación seleccionada
    private fun limpiarUbicacion() {
        ubicacionSeleccionada = null
        latitudSeleccionada = null
        longitudSeleccionada = null
        textUbicacionSeleccionada.text = ""
        actualizarEstadoUbicacion()
    }

    // Actualiza el estado visual de la sección de ubicación
    private fun actualizarEstadoUbicacion() {
        val tieneUbicacion = ubicacionSeleccionada != null

        textUbicacionSeleccionada.visibility = if (tieneUbicacion) View.VISIBLE else View.GONE
        buttonEliminarUbicacion.visibility = if (tieneUbicacion) View.VISIBLE else View.GONE
        buttonObtenerUbicacion.text =
            if (tieneUbicacion) getString(R.string.change_location) else getString(R.string.get_my_location)
    }

    // Recopila todos los hábitos ingresados por el usuario
    private fun recopilarHabitos(): List<String> {
        val habitos = mutableListOf<String>()

        val habito1 = habitoInput1.text.toString().trim()
        val habito2 = habitoInput2.text.toString().trim()
        val habito3 = habitoInput3.text.toString().trim()

        if (habito1.isNotEmpty()) habitos.add(habito1)
        if (habito2.isNotEmpty()) habitos.add(habito2)
        if (habito3.isNotEmpty()) habitos.add(habito3)

        habitosAdicionales.forEach { editText ->
            val habito = editText.text.toString().trim()
            if (habito.isNotEmpty()) habitos.add(habito)
        }

        return habitos
    }

    // Valida los datos del formulario antes de guardar
    private fun validarFormulario(): Boolean {
        val nombre = nombreInput.text.toString().trim()
        val descripcion = descripcionInput.text.toString().trim()
        val habitos = recopilarHabitos()

        when {
            nombre.isEmpty() -> {
                Toast.makeText(
                    context,
                    getString(R.string.challenge_name_required),
                    Toast.LENGTH_SHORT
                )
                    .show()
                return false
            }

            descripcion.isEmpty() -> {
                Toast.makeText(
                    context,
                    getString(R.string.challenge_description_required),
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }

            habitos.size < 3 -> {
                Toast.makeText(
                    context,
                    getString(R.string.minimum_habits_required),
                    Toast.LENGTH_SHORT
                )
                    .show()
                return false
            }

            habitos.size > 5 -> {
                Toast.makeText(
                    context,
                    getString(R.string.maximum_habits_exceeded),
                    Toast.LENGTH_SHORT
                )
                    .show()
                return false
            }

            esPublico && ubicacionSeleccionada != null -> {
                Toast.makeText(
                    context,
                    getString(R.string.public_challenges_no_location),
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }

            auth.currentUser?.uid == null -> {
                Toast.makeText(
                    context,
                    getString(R.string.user_not_authenticated),
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
        }

        return true
    }

    // Guarda el desafío en Firestore y crea los días correspondientes
    private fun guardarDesafio() {
        if (!validarFormulario()) return

        val nombre = nombreInput.text.toString().trim()
        val descripcion = descripcionInput.text.toString().trim()
        val habitos = recopilarHabitos()
        val uid = auth.currentUser?.uid!!
        val currentTime = com.google.firebase.Timestamp.now()

        val ubicacionData =
            if (ubicacionSeleccionada != null && latitudSeleccionada != null && longitudSeleccionada != null) {
                mapOf(
                    "ubicacion_latitude" to latitudSeleccionada!!,
                    "ubicacion_longitude" to longitudSeleccionada!!,
                    "ubicacion_nombre" to ubicacionSeleccionada!!
                )
            } else {
                emptyMap<String, Any>()
            }

        val desafioBase = hashMapOf(
            "nombre" to nombre,
            "descripcion" to descripcion,
            "tipo" to TYPE_CUSTOM,
            "completado" to false,
            "fechaCreacion" to currentTime,
            "fechaInicio" to currentTime,
            "dias" to duracionSeleccionada,
            "diaActual" to 1,
            "completados" to 0,
            "totalHabitos" to habitos.size,
            "creadoPor" to uid,
            "estado" to STATUS_ACTIVE,
            "etiquetas" to etiquetasAgregadas.toList(),
            "esPublico" to esPublico,
            "visibilidad" to if (esPublico) VISIBILITY_PUBLIC else VISIBILITY_PRIVATE,
            "habitos" to habitos.map { habito ->
                mapOf(
                    "nombre" to habito,
                    "completado" to false
                )
            },
            "progreso" to mapOf(
                "diasCompletados" to 0,
                "habitosCompletadosHoy" to emptyList<String>(),
                "ultimaActualizacion" to currentTime
            )
        )

        desafioBase.putAll(ubicacionData)

        crearButton.isEnabled = false
        crearButton.text = getString(R.string.creating)

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .add(desafioBase)
            .addOnSuccessListener { documentRef ->

                val batch = firestore.batch()

                val habitosParaDias = habitos.map { habito ->
                    mapOf(
                        "nombre" to habito,
                        "completado" to false
                    )
                }

                val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val calendar = Calendar.getInstance()

                for (i in 1..duracionSeleccionada) {
                    val diaRef = documentRef.collection("dias").document(DAY_PREFIX + i)
                    val fechaRealizacion = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, i - 1)
                    }

                    val dataDia = hashMapOf(
                        "dia" to i,
                        "habitos" to habitosParaDias,
                        "completado" to false,
                        "fecha_creacion" to currentTime,
                        "fechaRealizacion" to dateFormatter.format(fechaRealizacion.time)
                    )

                    batch.set(diaRef, dataDia)
                }

                if (esPublico) {
                    val desafioPublico = desafioBase.toMutableMap().apply {
                        put("autorId", uid)
                        put("autorNombre", auth.currentUser?.displayName ?: DEFAULT_AUTHOR)
                        put("fechaPublicacion", currentTime)
                        put("desafioOriginalId", documentRef.id)
                        put("seguidores", 0)
                        put("meGusta", 0)
                    }

                    val publicRef = firestore.collection("desafiosPublicos").document()
                    batch.set(publicRef, desafioPublico)
                }

                batch.commit().addOnSuccessListener {
                    Toast.makeText(
                        context,
                        getString(R.string.challenge_created_successfully),
                        Toast.LENGTH_SHORT
                    )
                        .show()

                    val intent = Intent(requireContext(), ItemDetailHostActivity::class.java)
                    intent.putExtra("desafio_id", documentRef.id)
                    startActivity(intent)
                    activity?.finish()
                }.addOnFailureListener { e ->
                    Toast.makeText(
                        context,
                        getString(R.string.error_format, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                    crearButton.isEnabled = true
                    crearButton.text = getString(R.string.create_challenge)
                }

            }.addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    getString(R.string.error_format, e.message),
                    Toast.LENGTH_LONG
                )
                    .show()
                crearButton.isEnabled = true
                crearButton.text = getString(R.string.create_challenge)
            }
    }

    // Cancela la creación del desafío y muestra confirmación si hay datos
    private fun cancelarCreacion() {
        locationHelper.stopLocationUpdates()

        val hayDatos = nombreInput.text.toString().trim().isNotEmpty() ||
                descripcionInput.text.toString().trim().isNotEmpty() ||
                recopilarHabitos().isNotEmpty() ||
                etiquetasAgregadas.isNotEmpty()

        if (hayDatos) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.cancel_creation))
                .setMessage(getString(R.string.cancel_creation_message))
                .setPositiveButton(getString(R.string.yes_cancel)) { _, _ ->
                    activity?.onBackPressed()
                }
                .setNegativeButton(getString(R.string.continue_editing), null)
                .show()
        } else {
            activity?.onBackPressed()
        }
    }

    // Detiene las actualizaciones de ubicación al destruir el fragmento
    override fun onDestroy() {
        super.onDestroy()
        locationHelper.stopLocationUpdates()
    }
}