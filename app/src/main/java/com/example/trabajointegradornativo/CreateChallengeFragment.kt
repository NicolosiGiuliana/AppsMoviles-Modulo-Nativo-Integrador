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

    // Views principales
    private lateinit var nombreInput: EditText
    private lateinit var descripcionInput: EditText
    private lateinit var habitoInput1: EditText
    private lateinit var habitoInput2: EditText
    private lateinit var habitoInput3: EditText
    private lateinit var crearButton: Button
    private lateinit var cancelarButton: TextView
    private lateinit var agregarHabitoButton: TextView

    // Opciones de duración
    private lateinit var option30Days: TextView
    private lateinit var option45Days: TextView
    private lateinit var option75Days: TextView

    // Ubicación
    private lateinit var layoutSelectLocation: LinearLayout
    private lateinit var textUbicacionSeleccionada: TextView
    private lateinit var buttonObtenerUbicacion: Button
    private lateinit var buttonEliminarUbicacion: Button
    private lateinit var checkboxUbicacionOpcional: CheckBox

    // NUEVOS ELEMENTOS - Etiquetas
    private lateinit var inputNewTag: EditText
    private lateinit var buttonAddTag: Button
    private lateinit var tagsContainer: LinearLayout

    // NUEVOS ELEMENTOS - Visibilidad
    private lateinit var radioGroupVisibility: RadioGroup
    private lateinit var radioPublic: RadioButton
    private lateinit var radioPrivate: RadioButton

    // Variables para almacenar datos
    private var duracionSeleccionada = 30 // Por defecto 30 días
    private val habitosAdicionales = mutableListOf<EditText>()
    private var ubicacionSeleccionada: String? = null
    private var latitudSeleccionada: Double? = null
    private var longitudSeleccionada: Double? = null

    // NUEVAS VARIABLES - Etiquetas y visibilidad
    private val etiquetasAgregadas = mutableListOf<String>()
    private var esPublico = true // Por defecto público

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Helper para geolocalización
    private lateinit var locationHelper: LocationHelper

    // Launcher para solicitar permisos
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Permisos concedidos, obtener ubicación
                obtenerUbicacionActual()
            }

            else -> {
                Toast.makeText(context, "Permisos de ubicación denegados", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_create_challenge, container, false)

        // Inicializar helper de ubicación
        locationHelper = LocationHelper(requireContext())

        inicializarViews(view)
        configurarEventos()

        return view
    }

    private fun inicializarViews(view: View) {
        // Inputs principales
        nombreInput = view.findViewById(R.id.inputChallengeName)
        descripcionInput = view.findViewById(R.id.inputChallengeDescription)
        habitoInput1 = view.findViewById(R.id.inputHabit1)
        habitoInput2 = view.findViewById(R.id.inputHabit2)
        habitoInput3 = view.findViewById(R.id.inputHabit3)

        // Botones
        crearButton = view.findViewById(R.id.buttonCreateChallenge)
        cancelarButton = view.findViewById(R.id.buttonCancel)
        agregarHabitoButton = view.findViewById(R.id.buttonAddHabit)

        // Opciones de duración
        option30Days = view.findViewById(R.id.option30Days)
        option45Days = view.findViewById(R.id.option45Days)
        option75Days = view.findViewById(R.id.option75Days)

        // Ubicación
        layoutSelectLocation = view.findViewById(R.id.layoutSelectLocation)
        textUbicacionSeleccionada = view.findViewById(R.id.textUbicacionSeleccionada)
        buttonObtenerUbicacion = view.findViewById(R.id.buttonObtenerUbicacion)
        buttonEliminarUbicacion = view.findViewById(R.id.buttonEliminarUbicacion)
        checkboxUbicacionOpcional = view.findViewById(R.id.checkboxUbicacionOpcional)

        // NUEVAS INICIALIZACIONES - Etiquetas
        inputNewTag = view.findViewById(R.id.inputNewTag)
        buttonAddTag = view.findViewById(R.id.buttonAddTag)
        tagsContainer = view.findViewById(R.id.tagsContainer)

        // NUEVAS INICIALIZACIONES - Visibilidad
        radioGroupVisibility = view.findViewById(R.id.radioGroupVisibility)
        radioPublic = view.findViewById(R.id.radioPublic)
        radioPrivate = view.findViewById(R.id.radioPrivate)

        // Establecer 30 días como seleccionado por defecto
        seleccionarDuracion(option30Days, 30)

        // Configurar estado inicial de ubicación
        actualizarEstadoUbicacion()
        if (esPublico) {
            checkboxUbicacionOpcional.visibility = View.GONE
        }
    }

    private fun configurarEventos() {
        // Eventos de duración
        option30Days.setOnClickListener { seleccionarDuracion(option30Days, 30) }
        option45Days.setOnClickListener { seleccionarDuracion(option45Days, 45) }
        option75Days.setOnClickListener { seleccionarDuracion(option75Days, 75) }

        // Agregar hábito
        agregarHabitoButton.setOnClickListener { agregarNuevoHabito() }

        // NUEVOS EVENTOS - Etiquetas
        buttonAddTag.setOnClickListener { agregarEtiqueta() }
        inputNewTag.setOnEditorActionListener { _, _, _ ->
            agregarEtiqueta()
            true
        }

        // NUEVOS EVENTOS - Visibilidad
        radioGroupVisibility.setOnCheckedChangeListener { _, checkedId ->
            esPublico = checkedId == R.id.radioPublic

            // Si se selecciona público, ocultar y limpiar ubicación
            if (esPublico) {
                checkboxUbicacionOpcional.visibility = View.GONE
                checkboxUbicacionOpcional.isChecked = false
                layoutSelectLocation.visibility = View.GONE
                limpiarUbicacion()
            } else {
                // Si se selecciona privado, mostrar opción de ubicación
                checkboxUbicacionOpcional.visibility = View.VISIBLE
            }
        }

        // Eventos de ubicación
        checkboxUbicacionOpcional.setOnCheckedChangeListener { _, isChecked ->
            layoutSelectLocation.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                limpiarUbicacion()
            }
        }

        buttonObtenerUbicacion.setOnClickListener { solicitarUbicacion() }
        buttonEliminarUbicacion.setOnClickListener { limpiarUbicacion() }

        // Botones principales
        crearButton.setOnClickListener { guardarDesafio() }
        cancelarButton.setOnClickListener { cancelarCreacion() }
    }

    // NUEVOS MÉTODOS - Gestión de etiquetas
    private fun agregarEtiqueta() {
        val nuevaEtiqueta = inputNewTag.text.toString().trim()

        when {
            nuevaEtiqueta.isEmpty() -> {
                Toast.makeText(context, "Escribe una etiqueta", Toast.LENGTH_SHORT).show()
                return
            }
            nuevaEtiqueta.length > 20 -> {
                Toast.makeText(context, "La etiqueta no puede superar 20 caracteres", Toast.LENGTH_SHORT).show()
                return
            }
            etiquetasAgregadas.contains(nuevaEtiqueta.lowercase()) -> {
                Toast.makeText(context, "Esta etiqueta ya fue agregada", Toast.LENGTH_SHORT).show()
                return
            }
            etiquetasAgregadas.size >= 10 -> {
                Toast.makeText(context, "No puedes agregar más de 10 etiquetas", Toast.LENGTH_SHORT).show()
                return
            }
        }

        etiquetasAgregadas.add(nuevaEtiqueta)
        crearVistaEtiqueta(nuevaEtiqueta)
        inputNewTag.text.clear()
    }

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
            setTextColor(resources.getColor(R.color.green_primary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
        }

        val botonEliminar = TextView(requireContext()).apply {
            text = "×"
            textSize = 16f
            setTextColor(resources.getColor(R.color.green_primary, null))
            setPadding(8, 0, 0, 0)
            setOnClickListener {
                eliminarEtiqueta(etiqueta, etiquetaView)
            }
        }

        etiquetaView.addView(textoEtiqueta)
        etiquetaView.addView(botonEliminar)
        tagsContainer.addView(etiquetaView)
    }

    private fun eliminarEtiqueta(etiqueta: String, vista: View) {
        etiquetasAgregadas.remove(etiqueta)
        tagsContainer.removeView(vista)
    }

    private fun seleccionarDuracion(opcionSeleccionada: TextView, dias: Int) {
        // Resetear todas las opciones
        resetearOpcionesDuracion()

        // Marcar la opción seleccionada
        opcionSeleccionada.setBackgroundResource(R.drawable.duration_selected_background)
        opcionSeleccionada.setTextColor(resources.getColor(R.color.green_primary, null))

        duracionSeleccionada = dias
    }

    private fun resetearOpcionesDuracion() {
        val opciones = listOf(option30Days, option45Days, option75Days)
        opciones.forEach { opcion ->
            opcion.setBackgroundResource(R.drawable.duration_unselected_background)
            opcion.setTextColor(resources.getColor(R.color.gray_text, null))
        }
    }

    private fun agregarNuevoHabito() {
        val containerPadre = view?.findViewById<LinearLayout>(R.id.habitosContainer)

        if (habitosAdicionales.size < 2) { // Máximo 5 hábitos (3 fijos + 2 adicionales)
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

            // Agregar antes del botón de agregar hábito
            val indexBotonAgregar = containerPadre?.indexOfChild(agregarHabitoButton) ?: 0
            containerPadre?.addView(nuevoHabito, indexBotonAgregar)

            // Ocultar botón si llegamos al máximo
            if (habitosAdicionales.size >= 2) {
                agregarHabitoButton.visibility = View.GONE
            }
        }
    }

    // Métodos de geolocalización (sin cambios)
    private fun solicitarUbicacion() {
        if (tienePermisosUbicacion()) {
            obtenerUbicacionActual()
        } else {
            solicitarPermisosUbicacion()
        }
    }

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

    private fun solicitarPermisosUbicacion() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun obtenerUbicacionActual() {
        buttonObtenerUbicacion.text = "Obteniendo ubicación..."
        buttonObtenerUbicacion.isEnabled = false

        locationHelper.getCurrentLocation(this)
    }

    // Implementar LocationCallback
    override fun onLocationReceived(latitude: Double, longitude: Double, address: String) {
        latitudSeleccionada = latitude
        longitudSeleccionada = longitude
        ubicacionSeleccionada = address

        textUbicacionSeleccionada.text = address
        actualizarEstadoUbicacion()

        buttonObtenerUbicacion.text = getString(R.string.get_my_location)
        buttonObtenerUbicacion.isEnabled = true

        Toast.makeText(context, "Ubicación obtenida correctamente", Toast.LENGTH_SHORT).show()
    }

    override fun onLocationError(error: String) {
        Toast.makeText(context, getString(R.string.error_format, error), Toast.LENGTH_LONG).show()

        buttonObtenerUbicacion.text = getString(R.string.get_my_location)
        buttonObtenerUbicacion.isEnabled = true
    }

    private fun limpiarUbicacion() {
        ubicacionSeleccionada = null
        latitudSeleccionada = null
        longitudSeleccionada = null
        textUbicacionSeleccionada.text = ""
        actualizarEstadoUbicacion()
    }

    private fun actualizarEstadoUbicacion() {
        val tieneUbicacion = ubicacionSeleccionada != null

        textUbicacionSeleccionada.visibility = if (tieneUbicacion) View.VISIBLE else View.GONE
        buttonEliminarUbicacion.visibility = if (tieneUbicacion) View.VISIBLE else View.GONE
        buttonObtenerUbicacion.text =
            if (tieneUbicacion) "Cambiar ubicación" else getString(R.string.get_my_location)
    }

    private fun recopilarHabitos(): List<String> {
        val habitos = mutableListOf<String>()

        // Agregar hábitos principales
        val habito1 = habitoInput1.text.toString().trim()
        val habito2 = habitoInput2.text.toString().trim()
        val habito3 = habitoInput3.text.toString().trim()

        if (habito1.isNotEmpty()) habitos.add(habito1)
        if (habito2.isNotEmpty()) habitos.add(habito2)
        if (habito3.isNotEmpty()) habitos.add(habito3)

        // Agregar hábitos adicionales
        habitosAdicionales.forEach { editText ->
            val habito = editText.text.toString().trim()
            if (habito.isNotEmpty()) habitos.add(habito)
        }

        return habitos
    }

    private fun validarFormulario(): Boolean {
        val nombre = nombreInput.text.toString().trim()
        val descripcion = descripcionInput.text.toString().trim()
        val habitos = recopilarHabitos()

        when {
            nombre.isEmpty() -> {
                Toast.makeText(context, "El nombre del desafío es obligatorio", Toast.LENGTH_SHORT)
                    .show()
                return false
            }

            descripcion.isEmpty() -> {
                Toast.makeText(context, "La descripción del desafío es obligatoria", Toast.LENGTH_SHORT).show()
                return false
            }

            habitos.size < 3 -> {
                Toast.makeText(context, "Debes agregar al menos 3 hábitos", Toast.LENGTH_SHORT)
                    .show()
                return false
            }

            habitos.size > 5 -> {
                Toast.makeText(context, "No puedes agregar más de 5 hábitos", Toast.LENGTH_SHORT)
                    .show()
                return false
            }

            esPublico && ubicacionSeleccionada != null -> {
                Toast.makeText(context, "Los desafíos públicos no pueden tener ubicación por privacidad", Toast.LENGTH_SHORT).show()
                return false
            }

            auth.currentUser?.uid == null -> {
                Toast.makeText(context, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show()
                return false
            }
        }

        return true
    }

    private fun guardarDesafio() {
        if (!validarFormulario()) return

        val nombre = nombreInput.text.toString().trim()
        val descripcion = descripcionInput.text.toString().trim()
        val habitos = recopilarHabitos()
        val uid = auth.currentUser?.uid!!
        val currentTime = com.google.firebase.Timestamp.now()

        // CORREGIR: Crear estructura de ubicación con nombres correctos
        val ubicacionData = if (ubicacionSeleccionada != null && latitudSeleccionada != null && longitudSeleccionada != null) {
            mapOf(
                "ubicacion_latitude" to latitudSeleccionada!!,
                "ubicacion_longitude" to longitudSeleccionada!!,
                "ubicacion_nombre" to ubicacionSeleccionada!!
            )
        } else {
            emptyMap<String, Any>()
        }

        // Crear estructura del desafío incluyendo etiquetas y visibilidad
        val desafioBase = hashMapOf(
            "nombre" to nombre,
            "descripcion" to descripcion,
            "tipo" to "personalizado",
            "completado" to false,
            "fechaCreacion" to currentTime,
            "fechaInicio" to currentTime,
            "dias" to duracionSeleccionada,
            "diaActual" to 1,
            "completados" to 0,
            "totalHabitos" to habitos.size,
            "creadoPor" to uid,
            "estado" to "activo",
            // NUEVOS CAMPOS - Etiquetas y visibilidad
            "etiquetas" to etiquetasAgregadas.toList(),
            "esPublico" to esPublico,
            "visibilidad" to if (esPublico) "publico" else "privado",
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

        // Agregar datos de ubicación al documento principal
        desafioBase.putAll(ubicacionData)

        crearButton.isEnabled = false
        crearButton.text = "Creando..."

        // 1. Crear el desafío con la estructura completa
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .add(desafioBase)
            .addOnSuccessListener { documentRef ->

                val batch = firestore.batch()

                // 2. Crear los días dentro del desafío con sus hábitos
                val habitosParaDias = habitos.map { habito ->
                    mapOf(
                        "nombre" to habito,
                        "completado" to false
                    )
                }

                val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val calendar = Calendar.getInstance()

                for (i in 1..duracionSeleccionada) {
                    val diaRef = documentRef.collection("dias").document("dia_$i")
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

                // 3. Si es público, también guardarlo en la colección pública
                if (esPublico) {
                    val desafioPublico = desafioBase.toMutableMap().apply {
                        put("autorId", uid)
                        put("autorNombre", auth.currentUser?.displayName ?: "Usuario")
                        put("fechaPublicacion", currentTime)
                        put("desafioOriginalId", documentRef.id)
                        put("seguidores", 0)
                        put("meGusta", 0)
                    }

                    val publicRef = firestore.collection("desafiosPublicos").document()
                    batch.set(publicRef, desafioPublico)
                }

                // 4. Commit de batch
                batch.commit().addOnSuccessListener {
                    Toast.makeText(context, "¡Desafío creado exitosamente!", Toast.LENGTH_SHORT)
                        .show()

                    // CORREGIR: Pasar el ID del desafío al Intent
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
                Toast.makeText(context, getString(R.string.error_format, e.message), Toast.LENGTH_LONG)
                    .show()
                crearButton.isEnabled = true
                crearButton.text = getString(R.string.create_challenge)
            }
    }

    private fun cancelarCreacion() {
        // Detener actualizaciones de ubicación si están activas
        locationHelper.stopLocationUpdates()

        // Mostrar diálogo de confirmación si hay datos ingresados
        val hayDatos = nombreInput.text.toString().trim().isNotEmpty() ||
                descripcionInput.text.toString().trim().isNotEmpty() || // Nueva línea
                recopilarHabitos().isNotEmpty() ||
                etiquetasAgregadas.isNotEmpty()

        if (hayDatos) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Cancelar creación")
                .setMessage("¿Estás seguro de que quieres cancelar? Se perderán los datos ingresados.")
                .setPositiveButton("Sí, cancelar") { _, _ ->
                    activity?.onBackPressed()
                }
                .setNegativeButton("Continuar editando", null)
                .show()
        } else {
            activity?.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationHelper.stopLocationUpdates()
    }
}