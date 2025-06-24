package com.example.trabajointegradornativo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.trabajointegradornativo.databinding.FragmentItemDetailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

class ItemDetailFragment : Fragment() {

    private var _binding: FragmentItemDetailBinding? = null
    // SOLUCIÓN: Mantener el getter original pero con verificación
    private val binding get() = _binding!!

    data class Desafio(
        val id: String = "",
        val nombre: String = "",
        val descripcion: String = "",
        val dias: Int = 0,
        val diaActual: Int = 1,
        val completados: Int = 0,
        val totalHabitos: Int = 0,
        val etiquetas: List<String> = emptyList(),
        val creadoPor: String = "",
        val visibilidad: String = "privado",
        val fechaInicio: String = ""
    )

    private lateinit var desafio: Desafio

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var diasCompletados = mutableSetOf<Int>()
    private var isUpdatingContent = false

    private var challengeMap: MapView? = null
    private var locationCard: CardView? = null
    private var locationAddress: TextView? = null
    private var challengeLatitude: Double? = null
    private var challengeLongitude: Double? = null
    private var challengeLocationName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osmdroid", 0)
        )

        arguments?.let { args ->
            val desafioFromArgs = args.getParcelable<ItemListFragment.Desafio>("desafio")
            val desafioId = args.getString("desafio_id") ?: args.getString(ARG_ITEM_ID)

            if (desafioFromArgs != null) {
                convertirDesafio(desafioFromArgs)
            } else if (desafioId != null) {
                cargarDesafioPorId(desafioId)
                return
            } else {
                throw IllegalStateException("ID de desafío no válido")
            }
        }
    }

    private fun convertirDesafio(desafioFromArgs: ItemListFragment.Desafio) {
        desafio = Desafio(
            id = desafioFromArgs.id,
            nombre = desafioFromArgs.nombre,
            descripcion = desafioFromArgs.descripcion,
            dias = desafioFromArgs.dias,
            diaActual = desafioFromArgs.diaActual,
            completados = desafioFromArgs.completados,
            totalHabitos = desafioFromArgs.totalHabitos,
            etiquetas = desafioFromArgs.etiquetas,
            creadoPor = desafioFromArgs.creadoPor,
            visibilidad = desafioFromArgs.visibilidad,
            fechaInicio = ""
        )
    }

    private fun cargarDesafioPorId(desafioId: String) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection(COLLECTION_USUARIOS)
            .document(uid)
            .collection(COLLECTION_DESAFIOS)
            .document(desafioId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val fechaInicioTimestamp = document.getTimestamp("fechaInicio")
                    val fechaCreacionTimestamp = document.getTimestamp("fechaCreacion")

                    val fechaInicio =
                        fechaInicioTimestamp?.toDate() ?: fechaCreacionTimestamp?.toDate()
                    val fechaInicioString = fechaInicio?.let {
                        val formatter =
                            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        formatter.format(it)
                    } ?: LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                    desafio = Desafio(
                        id = document.id,
                        nombre = document.getString("nombre") ?: DEFAULT_NAME,
                        descripcion = document.getString("descripcion") ?: DEFAULT_DESCRIPTION,
                        dias = document.getLong("dias")?.toInt() ?: 0,
                        diaActual = document.getLong("diaActual")?.toInt() ?: 1,
                        completados = document.getLong("completados")?.toInt() ?: 0,
                        totalHabitos = document.getLong("totalHabitos")?.toInt() ?: 5,
                        etiquetas = document.get("etiquetas") as? List<String> ?: emptyList(),
                        creadoPor = uid,
                        visibilidad = document.getString("visibilidad") ?: DEFAULT_VISIBILITY,
                        fechaInicio = fechaInicioString
                    )

                    if (isAdded && view != null) {
                        cargarDiasCompletados()
                    }
                } else {
                    mostrarError("Desafío no encontrado")
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error cargando desafío: ${e.message}")
                mostrarError("Error al cargar el desafío")
                findNavController().popBackStack()
            }
    }

    private fun calcularDiaActual(fechaInicio: String): Int {
        return try {
            val fechaInicioDate =
                LocalDate.parse(fechaInicio, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val fechaHoy = LocalDate.now()
            val diasTranscurridos = ChronoUnit.DAYS.between(fechaInicioDate, fechaHoy).toInt()

            val diaCalculado = diasTranscurridos + 1
            when {
                diaCalculado > desafio.dias -> desafio.dias
                diaCalculado < 1 -> 1
                else -> diaCalculado
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando día actual: ${e.message}")
            1
        }
    }

    @SuppressLint("UseRequireInsteadOfGet")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        val rootView = binding.root

        initializeMapViews()
        setupBottomNavigation(rootView)
        setupFloatingButtons()

        return rootView
    }

    private fun setupFloatingButtons() {
        // Botón de editar
        binding.btnEditChallenge?.setOnClickListener {
            if (::desafio.isInitialized) {
                val desafioParaEditar = ItemListFragment.Desafio(
                    id = desafio.id,
                    nombre = desafio.nombre,
                    descripcion = desafio.descripcion,
                    dias = desafio.dias,
                    diaActual = desafio.diaActual,
                    completados = desafio.completados,
                    totalHabitos = desafio.totalHabitos,
                    etiquetas = desafio.etiquetas,
                    creadoPor = desafio.creadoPor,
                    visibilidad = desafio.visibilidad
                )

                val bundle = Bundle().apply {
                    putParcelable("desafio", desafioParaEditar)
                }
                try {
                    findNavController().navigate(
                        R.id.action_itemDetailFragment_to_editDesafioFragment,
                        bundle
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error navegando a editar: ${e.message}")
                    mostrarError("Error al abrir editor")
                }
            }
        }

        // Botón de eliminar
        binding.btnDeleteChallenge?.setOnClickListener {
            if (::desafio.isInitialized) {
                eliminarDesafio(desafio.id)
            }
        }

        // Botón de finalizar (si existe en el layout de finalización)
        binding.root.findViewById<View>(R.id.boton_finalizar_desafio)?.setOnClickListener {
            finalizarDesafio()
        }
    }

    private fun initializeMapViews() {
        try {
            challengeMap = binding.root.findViewById(R.id.challenge_map)
            locationCard = binding.root.findViewById(R.id.location_card)
            locationAddress = binding.root.findViewById(R.id.location_address)

            challengeMap?.let { map ->
                map.setTileSource(TileSourceFactory.MAPNIK)
                map.setMultiTouchControls(true)
                map.controller.setZoom(15.0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando mapa: ${e.message}")
        }
    }

    private fun cargarDiasCompletados() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Cargar información de ubicación
                    val latitude = document.getDouble("ubicacion_latitude")
                    val longitude = document.getDouble("ubicacion_longitude")
                    val locationName = document.getString("ubicacion_nombre")

                    if (latitude != null && longitude != null) {
                        challengeLatitude = latitude
                        challengeLongitude = longitude
                        challengeLocationName = locationName
                        setupMap()
                    } else {
                        locationCard?.visibility = View.GONE
                    }

                    updateBasicInfo()

                    // Cargar días completados
                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafio.id)
                        .collection("dias")
                        .whereEqualTo("completado", true)
                        .get()
                        .addOnSuccessListener { result ->
                            diasCompletados.clear()

                            for (doc in result) {
                                val dia = doc.getLong("dia")?.toInt() ?: 0
                                if (dia > 0) {
                                    diasCompletados.add(dia)
                                }
                            }

                            if (!isUpdatingContent) {
                                updateContent()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error al cargar días completados: ${e.message}")
                            if (!isUpdatingContent) {
                                updateContent()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error cargando información del desafío: ${e.message}")
            }
    }

    private fun setupMap() {
        val latitude = challengeLatitude ?: return
        val longitude = challengeLongitude ?: return
        val map = challengeMap ?: return

        try {
            locationCard?.visibility = View.VISIBLE

            val challengePoint = GeoPoint(latitude, longitude)
            map.controller.setCenter(challengePoint)
            map.controller.setZoom(18.0)

            val marker = org.osmdroid.views.overlay.Marker(map)
            marker.position = challengePoint
            marker.setAnchor(0.5f, 1.0f)
            marker.title = "Ubicación del Desafío"

            map.overlays.clear()
            map.overlays.add(marker)

            updateLocationAddress(latitude, longitude)
            map.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando mapa: ${e.message}")
            locationCard?.visibility = View.GONE
        }
    }

    private fun updateLocationAddress(latitude: Double, longitude: Double) {
        if (challengeLocationName != null) {
            locationAddress?.text = challengeLocationName
        } else {
            locationAddress?.text = "Lat: %.4f, Lng: %.4f".format(latitude, longitude)
        }
    }

    private fun updateBasicInfo() {
        binding.root.findViewById<TextView>(R.id.challenge_title)?.text = desafio.nombre
        binding.root.findViewById<TextView>(R.id.challenge_description)?.text = desafio.descripcion
        binding.root.findViewById<TextView>(R.id.challenge_duration)?.text =
            "${desafio.dias} ${getString(R.string.firestore_field_dias)}"
    }

    private fun updateContent() {
        if (isUpdatingContent) {
            return
        }
        isUpdatingContent = true
        val uid = auth.currentUser?.uid ?: return

        verificarSiDesafioFinalizado { desafioFinalizado ->
            if (desafioFinalizado) {
                mostrarVistaDesafioFinalizado()
                isUpdatingContent = false
                return@verificarSiDesafioFinalizado
            }

            firestore.collection(COLLECTION_USUARIOS)
                .document(uid)
                .collection(COLLECTION_DESAFIOS)
                .document(desafio.id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        firestore.collection("usuarios")
                            .document(uid)
                            .collection("desafios")
                            .document(desafio.id)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val nombre = document.getString("nombre") ?: DEFAULT_NAME
                                    val descripcion = document.getString("descripcion") ?: DEFAULT_DESCRIPTION
                                    val dias = document.getLong("dias")?.toInt() ?: 0
                                    val estado = document.getString("estado") ?: DEFAULT_STATUS
                                    val completados = diasCompletados.size

                                    val fechaInicioTimestamp = document.getTimestamp("fechaInicio")
                                    val fechaCreacionTimestamp = document.getTimestamp("fechaCreacion")
                                    val fechaInicio =
                                        fechaInicioTimestamp?.toDate() ?: fechaCreacionTimestamp?.toDate()
                                    val fechaInicioString = fechaInicio?.let {
                                        val formatter =
                                            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        formatter.format(it)
                                    } ?: LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                                    val diaActualCalculado = calcularDiaActual(fechaInicioString)

                                    val etiquetas = document.get("etiquetas") as? List<String> ?: emptyList()
                                    val habitosBase =
                                        document.get("habitos") as? List<Map<String, Any>> ?: emptyList()
                                    val totalHabitos = habitosBase.size

                                    obtenerHabitosDiaActual(uid, desafio.id) { habitosDelDia ->
                                        // SOLUCIÓN: Verificar que el fragment sigue activo antes de actualizar UI
                                        if (isAdded && _binding != null) {
                                            actualizarUI(
                                                nombre = nombre,
                                                descripcion = descripcion,
                                                dias = dias,
                                                diaActual = diaActualCalculado,
                                                completados = completados,
                                                totalHabitos = totalHabitos,
                                                estado = estado,
                                                habitos = habitosDelDia
                                            )

                                            actualizarInformacionGeneral(
                                                nombre,
                                                descripcion,
                                                dias,
                                                diaActualCalculado,
                                                completados,
                                                estado
                                            )
                                            mostrarEtiquetas(etiquetas)
                                        }

                                        isUpdatingContent = false
                                    }
                                } else {
                                    isUpdatingContent = false
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("Firestore", "Error obteniendo datos del desafío", exception)
                                mostrarError("Error al cargar el desafío")
                                isUpdatingContent = false
                            }
                    }
                }
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
        binding.root.findViewById<TextView>(R.id.challenge_title)?.text = nombre
        binding.root.findViewById<TextView>(R.id.challenge_description)?.text = descripcion
        binding.root.findViewById<TextView>(R.id.progress_subtitle)?.text =
            "${getString(R.string.day_label)} $diaActual ${getString(R.string.of_label)} $dias"
        binding.root.findViewById<TextView>(R.id.progress_title)?.text = getString(R.string.your_progress)
        binding.root.findViewById<TextView>(R.id.challenge_duration)?.text =
            "$dias ${getString(R.string.firestore_field_dias)}"

        val progressBar = binding.root.findViewById<ProgressBar>(R.id.circular_progress)
        progressBar?.let {
            it.max = dias
            it.progress = completados
        }

        val porcentaje = if (dias > 0) (completados * 100) / dias else 0
        binding.root.findViewById<TextView>(R.id.progress_text)?.text = "$porcentaje%"
    }

    private fun obtenerDiaActual(uid: String, desafioId: String, callback: (String?) -> Unit) {
        val fechaHoy = LocalDate.now().toString()

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .whereEqualTo("fechaRealizacion", fechaHoy)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    callback(result.documents[0].id)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error obteniendo día actual", e)
                callback(null)
            }
    }

    private fun verificarEstadoDia(
        uid: String,
        desafioId: String,
        diaId: String,
        habitos: List<Map<String, Any>>
    ) {
        val todosCompletados = habitos.all { (it["completado"] as? Boolean) == true }

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .document(diaId)
            .get()
            .addOnSuccessListener { document ->
                val estadoAnterior = document.getBoolean("completado") ?: false

                if (estadoAnterior != todosCompletados) {
                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                        .collection("dias")
                        .document(diaId)
                        .update("completado", todosCompletados)
                        .addOnSuccessListener {
                            cargarDiasCompletados()
                            actualizarEstadoUI(todosCompletados)
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error al actualizar el estado del día", e)
                        }
                } else {
                    actualizarEstadoUI(todosCompletados)
                }
            }
    }

    private fun actualizarEstadoUI(diaCompletado: Boolean) {
        val estadoText = binding.root.findViewById<TextView>(R.id.day_status_text)
        val estadoIcon = binding.root.findViewById<ImageView>(R.id.day_status_icon)

        if (diaCompletado) {
            estadoText?.text = getString(R.string.completed)
            estadoIcon?.setImageResource(R.drawable.ic_check_green)
        } else {
            estadoText?.text = getString(R.string.pending)
            estadoIcon?.setImageResource(R.drawable.ic_circle_empty)
        }
    }

    private fun actualizarEstadoHabitoDelDia(
        uid: String,
        desafioId: String,
        diaId: String,
        habitIndex: Int,
        completado: Boolean,
        habitosActuales: List<Map<String, Any>>
    ) {
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .document(diaId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val habitos = document.get("habitos") as? ArrayList<Map<String, Any>>
                        ?: return@addOnSuccessListener

                    if (habitIndex < 0 || habitIndex >= habitos.size) return@addOnSuccessListener

                    habitos[habitIndex] = habitos[habitIndex].toMutableMap().apply {
                        this["completado"] = completado
                    }

                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                        .collection("dias")
                        .document(diaId)
                        .update("habitos", habitos)
                        .addOnSuccessListener {
                            verificarEstadoDia(uid, desafioId, diaId, habitos)
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
        val tagsContainer =
            binding.root.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.tags_container)
        tagsContainer?.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())

        for (etiqueta in etiquetas) {
            val tagView = inflater.inflate(R.layout.item_tag, tagsContainer, false)
            val tagText = tagView.findViewById<TextView>(R.id.tag_text)
            tagText?.text = etiqueta
            tagsContainer?.addView(tagView)
        }

        if (etiquetas.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = getString(R.string.no_tags)
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                setPadding(16, 16, 16, 16)
            }
            tagsContainer?.addView(emptyText)
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
        val habitsContainer = binding.root.findViewById<LinearLayout>(R.id.habits_container)
        habitsContainer.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val uid = auth.currentUser?.uid ?: return
        val desafioId = desafio.id

        val habitosLocales = habitos.map { it.toMutableMap() }.toMutableList()
        val todosHabitosCompletados = habitosLocales.all { (it["completado"] as? Boolean) == true }

        obtenerDiaActual(uid, desafioId) { diaId ->
            if (diaId != null) {
                verificarSiDiaEstaCompletado(uid, desafioId, diaId) { diaCompletado ->

                    for ((index, habit) in habitosLocales.withIndex()) {
                        val habitName = habit["nombre"] as? String ?: DEFAULT_HABIT_NAME
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

                            if (diaCompletado && todosHabitosCompletados && !nuevoEstado) {
                                mostrarError("No puedes desmarcar hábitos de un día ya completado")
                                return@setOnClickListener
                            }

                            habitosLocales[index]["completado"] = nuevoEstado

                            habitIcon.setImageResource(
                                if (nuevoEstado) R.drawable.ic_check_green else R.drawable.ic_circle_empty
                            )

                            actualizarEstadoHabitoDelDia(
                                uid,
                                desafioId,
                                diaId,
                                index,
                                nuevoEstado,
                                habitosLocales
                            )
                        }

                        habitsContainer.addView(habitView)
                    }

                    actualizarEstadoUI(todosHabitosCompletados)
                }
            } else {
                Log.e("Firestore", "No se encontró el día actual.")
                mostrarError("No se encontró información del día actual")
            }
        }
    }

    private fun verificarSiDiaEstaCompletado(
        uid: String,
        desafioId: String,
        diaId: String,
        callback: (Boolean) -> Unit
    ) {
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

    private fun crearDiaActual(
        uid: String,
        desafioId: String,
        fecha: String,
        callback: (List<Map<String, Any>>) -> Unit
    ) {
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .get()
            .addOnSuccessListener { desafioDoc ->
                val fechaInicioTimestamp = desafioDoc.getTimestamp("fechaInicio")
                val fechaCreacionTimestamp = desafioDoc.getTimestamp("fechaCreacion")
                val fechaInicio = fechaInicioTimestamp?.toDate() ?: fechaCreacionTimestamp?.toDate()
                val fechaInicioString = fechaInicio?.let {
                    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    formatter.format(it)
                } ?: LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                val diaActualCalculado = calcularDiaActual(fechaInicioString)

                val habitosBase = desafioDoc.get("habitos") as? List<Map<String, Any>> ?: emptyList()

                val habitosDelDia = habitosBase.map { habitoBase ->
                    mapOf(
                        "nombre" to (habitoBase["nombre"] as? String ?: DEFAULT_HABIT_NAME),
                        "completado" to false
                    )
                }

                val nuevoDia = mapOf(
                    "completado" to false,
                    "dia" to diaActualCalculado,
                    "fechaRealizacion" to fecha,
                    "fecha_creacion" to com.google.firebase.Timestamp.now(),
                    "habitos" to habitosDelDia,
                    "etiquetas" to emptyList<String>()
                )

                firestore.collection("usuarios")
                    .document(uid)
                    .collection("desafios")
                    .document(desafioId)
                    .collection("dias")
                    .add(nuevoDia)
                    .addOnSuccessListener {
                        callback(habitosDelDia)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error creando día actual: ${e.message}")
                        callback(emptyList())
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error obteniendo desafío para crear día: ${e.message}")
                callback(emptyList())
            }
    }

    private fun obtenerHabitosDiaActual(
        uid: String,
        desafioId: String,
        callback: (List<Map<String, Any>>) -> Unit
    ) {
        val fechaHoy = LocalDate.now().toString()

        verificarSiDesafioFinalizado { desafioFinalizado ->
            if (desafioFinalizado) {
                callback(emptyList())
                return@verificarSiDesafioFinalizado
            }

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
                        callback(habitos)
                    } else {
                        verificarLimiteDiasDesafio(uid, desafioId) { puedeCrearDia ->
                            if (puedeCrearDia) {
                                crearDiaActual(uid, desafioId, fechaHoy) { habitos ->
                                    callback(habitos)
                                }
                            } else {
                                callback(emptyList())
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    callback(emptyList())
                }
        }
    }

    private fun verificarSiDesafioFinalizado(callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .orderBy("dia", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val ultimoDia = result.documents[0]
                    val fechaRealizacion = ultimoDia.getString("fechaRealizacion")
                    val diaNumero = ultimoDia.getLong("dia")?.toInt() ?: 0

                    if (diaNumero >= desafio.dias && fechaRealizacion != null) {
                        try {
                            val fechaUltimoDia = LocalDate.parse(fechaRealizacion, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            val fechaHoy = LocalDate.now()
                            val diasTranscurridos = ChronoUnit.DAYS.between(fechaUltimoDia, fechaHoy)

                            val desafioFinalizado = diasTranscurridos >= 1
                            callback(desafioFinalizado)
                        } catch (e: Exception) {
                            callback(false)
                        }
                    } else {
                        callback(false)
                    }
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error verificando si desafío está finalizado: ${e.message}")
                callback(false)
            }
    }

    private fun mostrarVistaDesafioFinalizado() {
        ocultarContenidoNormal()

        val finalizacionContainer = binding.root.findViewById<LinearLayout>(R.id.finalizacion_container)
        finalizacionContainer?.visibility = View.VISIBLE

        calcularEstadisticasFinales { diasCompletados, totalDias ->
            actualizarEstadisticasFinales(diasCompletados, totalDias)
        }
    }

    private fun ocultarContenidoNormal() {
        binding.root.findViewById<LinearLayout>(R.id.item_description)?.visibility = View.GONE
        binding.root.findViewById<LinearLayout>(R.id.item_habits)?.visibility = View.GONE
        binding.root.findViewById<LinearLayout>(R.id.item_etiquetas)?.visibility = View.GONE
        binding.root.findViewById<LinearLayout>(R.id.item_duracion)?.visibility = View.GONE
        binding.root.findViewById<LinearLayout>(R.id.item_estado)?.visibility = View.GONE
        binding.root.findViewById<LinearLayout>(R.id.item_location)?.visibility = View.GONE
        binding.root.findViewById<View>(R.id.progress_section)?.visibility = View.GONE
        binding.btnEditChallenge?.visibility = View.GONE
        binding.btnDeleteChallenge?.visibility = View.GONE
    }

    private fun calcularEstadisticasFinales(callback: (Int, Int) -> Unit) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .whereEqualTo("completado", true)
            .get()
            .addOnSuccessListener { result ->
                val diasCompletados = result.size()
                callback(diasCompletados, desafio.dias)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error calculando estadísticas: ${e.message}")
                callback(0, desafio.dias)
            }
    }

    private fun actualizarEstadisticasFinales(diasCompletados: Int, totalDias: Int) {
        val estadisticasView = binding.root.findViewById<TextView>(R.id.estadisticas_finales)
        val porcentaje = if (totalDias > 0) (diasCompletados * 100) / totalDias else 0

        val estadisticasText = when {
            porcentaje == 100 -> "¡Perfecto! 🎉\n$diasCompletados de $totalDias días completados\n(100% de éxito)"
            porcentaje >= 80 -> "¡Excelente! 👏\n$diasCompletados de $totalDias días completados\n($porcentaje% de éxito)"
            porcentaje >= 60 -> "¡Buen trabajo! 👍\n$diasCompletados de $totalDias días completados\n($porcentaje% de éxito)"
            else -> "¡Sigue intentando! 💪\n$diasCompletados de $totalDias días completados\n($porcentaje% de éxito)"
        }

        estadisticasView?.text = estadisticasText
    }

    private fun finalizarDesafio() {
        val context = requireContext()
        val uid = auth.currentUser?.uid ?: return

        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setMessage("Finalizando desafío...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        eliminarDesafioCompleto(uid, desafio.id) { success ->
            progressDialog.dismiss()

            if (success) {
                mostrarExito("¡Desafío finalizado exitosamente!")
                try {
                    findNavController().navigate(R.id.itemListFragment)
                } catch (e: Exception) {
                    Log.e(TAG, "Error navegando de vuelta: ${e.message}")
                    requireActivity().onBackPressed()
                }
            } else {
                mostrarError("Error al finalizar el desafío")
            }
        }
    }

    private fun eliminarDesafioCompleto(uid: String, desafioId: String, callback: (Boolean) -> Unit) {
        try {
            firestore.collection("usuarios")
                .document(uid)
                .collection("desafios")
                .document(desafioId)
                .collection("dias")
                .get()
                .addOnSuccessListener { diasSnapshot ->
                    val batch = firestore.batch()

                    for (diaDoc in diasSnapshot.documents) {
                        batch.delete(diaDoc.reference)
                    }

                    val desafioRef = firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                    batch.delete(desafioRef)

                    batch.commit()
                        .addOnSuccessListener {
                            callback(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error eliminando desafío completo: ${e.message}")
                            callback(false)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error obteniendo días para eliminar: ${e.message}")
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error general eliminando desafío: ${e.message}")
            callback(false)
        }
    }

    private fun verificarLimiteDiasDesafio(uid: String, desafioId: String, callback: (Boolean) -> Unit) {
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .orderBy("dia", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val ultimoDia = result.documents[0]
                    val diaNumero = ultimoDia.getLong("dia")?.toInt() ?: 0

                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                        .get()
                        .addOnSuccessListener { desafioDoc ->
                            val limiteDias = desafioDoc.getLong("dias")?.toInt() ?: 0
                            val puedeCrear = diaNumero < limiteDias
                            callback(puedeCrear)
                        }
                        .addOnFailureListener {
                            callback(false)
                        }
                } else {
                    callback(true)
                }
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    private fun setupBottomNavigation(view: View) {
        val bottomNav = view.findViewById<LinearLayout>(R.id.bottom_navigation)

        val homeLayout = bottomNav.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.itemListFragment)
            } catch (e: Exception) {
                mostrarError("Error al navegar al inicio")
            }
        }

        val todayLayout = bottomNav.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.todayFragment)
            } catch (e: Exception) {
                mostrarError("Error al navegar a hoy")
            }
        }

        val exploreLayout = bottomNav.getChildAt(2) as? LinearLayout
        exploreLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.publicChallengeFragment)
            } catch (e: Exception) {
                Log.e(TAG, "Error navegando a explorar: ${e.message}")
            }
        }

        val profileLayout = bottomNav.getChildAt(3) as? LinearLayout
        profileLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.profileFragment)
            } catch (e: Exception) {
                mostrarError("Error al navegar al perfil")
            }
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
                        mostrarExito(getString(R.string.challenge_deleted))
                        findNavController().navigate(R.id.itemListFragment)
                    }
                    .addOnFailureListener {
                        mostrarError(getString(R.string.error_deleting_challenge))
                    }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun mostrarError(mensaje: String) {
        Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
    }

    private fun mostrarExito(mensaje: String) {
        Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ARG_ITEM_ID = "item_id"
        private const val TAG = "ItemDetailFragment"

        // Constantes para valores por defecto
        private const val DEFAULT_NAME = "Sin nombre"
        private const val DEFAULT_DESCRIPTION = "Sin descripción"
        private const val DEFAULT_STATUS = "Indefinido"
        private const val DEFAULT_HABIT_NAME = "Hábito sin nombre"
        private const val DEFAULT_VISIBILITY = "privado"

        // Constantes para Firebase
        private const val COLLECTION_USUARIOS = "usuarios"
        private const val COLLECTION_DESAFIOS = "desafios"
        private const val COLLECTION_DIAS = "dias"
        private const val FIELD_NOMBRE = "nombre"
        private const val FIELD_DESCRIPCION = "descripcion"
        private const val FIELD_COMPLETADO = "completado"
        private const val FIELD_HABITOS = "habitos"
    }

    override fun onResume() {
        super.onResume()
        challengeMap?.onResume()

        if (::desafio.isInitialized && !isUpdatingContent) {
            cargarDiasCompletados()
        }
    }

    override fun onPause() {
        super.onPause()
        challengeMap?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}