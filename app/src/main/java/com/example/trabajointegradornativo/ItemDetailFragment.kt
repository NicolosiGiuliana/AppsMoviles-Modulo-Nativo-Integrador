package com.example.trabajointegradornativo

// IMPORTACIONES PARA EL MAPA
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.trabajointegradornativo.databinding.FragmentItemDetailBinding
import com.google.android.gms.maps.model.Marker
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
    private val binding get() = _binding!!

    // Clase simple para el desafío (reemplaza ItemListFragment.Desafio)
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
        val fechaInicio: String = "" // Agregamos fecha de inicio
    )

    private lateinit var desafio: Desafio

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var diasCompletados = mutableSetOf<Int>()
    private var isUpdatingContent = false

    // VARIABLES PARA EL MAPA
    private var challengeMap: MapView? = null
    private var locationCard: CardView? = null
    private var locationAddress: TextView? = null
    private var challengeLatitude: Double? = null
    private var challengeLongitude: Double? = null
    private var challengeLocationName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        // CONFIGURAR OSMDROID
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osmdroid", 0)
        )

        arguments?.let { args ->
            // CORREGIR: Manejar tanto el objeto Desafio como el ID
            val desafioFromArgs = args.getParcelable<ItemListFragment.Desafio>("desafio")
            val desafioId = args.getString("desafio_id") ?: args.getString(ARG_ITEM_ID)

            if (desafioFromArgs != null) {
                Log.d(
                    "ItemDetailFragment",
                    "Desafío recibido desde argumentos: ${desafioFromArgs.nombre}"
                )
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
                    visibilidad = desafioFromArgs.visibilidad
                )
            } else if (desafioId != null) {
                Log.d("ItemDetailFragment", "ID recibido: $desafioId")
                cargarDesafioPorId(desafioId)
                return // Salir aquí porque cargarDesafioPorId manejará el resto
            } else {
                Log.e("ItemDetailFragment", "No se recibió ni desafío ni ID")
                throw IllegalStateException("ID de desafío no válido")
            }
        }
    }

    private fun cargarDesafioPorId(desafioId: String) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Obtener fecha de inicio del desafío
                    val fechaInicioTimestamp = document.getTimestamp("fechaInicio")
                    val fechaCreacionTimestamp = document.getTimestamp("fechaCreacion")

                    // Usar fechaInicio si existe, sino usar fechaCreacion
                    val fechaInicio =
                        fechaInicioTimestamp?.toDate() ?: fechaCreacionTimestamp?.toDate()
                    val fechaInicioString = fechaInicio?.let {
                        val formatter =
                            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        formatter.format(it)
                    } ?: LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                    desafio = Desafio(
                        id = document.id,
                        nombre = document.getString("nombre") ?: "",
                        descripcion = document.getString("descripcion") ?: "",
                        dias = document.getLong("dias")?.toInt() ?: 0,
                        diaActual = document.getLong("diaActual")?.toInt() ?: 1,
                        completados = document.getLong("completados")?.toInt() ?: 0,
                        totalHabitos = document.getLong("totalHabitos")?.toInt() ?: 5,
                        etiquetas = document.get("etiquetas") as? List<String> ?: emptyList(),
                        creadoPor = uid,
                        visibilidad = document.getString("visibilidad") ?: "privado",
                        fechaInicio = fechaInicioString
                    )

                    Log.d("ItemDetailFragment", "Desafío cargado: ${desafio.nombre}")
                    if (isAdded && view != null) {
                        cargarDiasCompletados()
                    }
                } else {
                    Log.e("ItemDetailFragment", "Desafío no encontrado")
                    Toast.makeText(context, "Desafío no encontrado", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ItemDetailFragment", "Error al cargar desafío: ${e.message}")
                Toast.makeText(context, "Error al cargar el desafío", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
    }

    // Función para calcular el día actual basado en la fecha de inicio
    private fun calcularDiaActual(fechaInicio: String): Int {
        return try {
            val fechaInicioDate =
                LocalDate.parse(fechaInicio, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val fechaHoy = LocalDate.now()
            val diasTranscurridos = ChronoUnit.DAYS.between(fechaInicioDate, fechaHoy).toInt()

            // El día actual es diasTranscurridos + 1, pero no puede exceder el total de días del desafío
            val diaCalculado = diasTranscurridos + 1
            if (diaCalculado > desafio.dias) desafio.dias else if (diaCalculado < 1) 1 else diaCalculado
        } catch (e: Exception) {
            Log.e("ItemDetailFragment", "Error calculando día actual: ${e.message}")
            1 // Valor por defecto
        }
    }

    @SuppressLint("UseRequireInsteadOfGet")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        val rootView = binding.root

        Log.d("ItemDetailFragment", "onCreateView: Inicializando vistas")

        // Inicializar vistas del mapa
        initializeMapViews()

        // Inicializar navegación inferior
        setupBottomNavigation(rootView)

        // Configuración del botón para editar desafío
        binding.btnEditChallenge?.setOnClickListener {
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
            findNavController().navigate(
                R.id.action_itemDetailFragment_to_editDesafioFragment,
                bundle
            )
        }

        binding.botonFinalizarDesafio?.setOnClickListener {
            finalizarDesafio()
        }

        binding.btnDeleteChallenge?.setOnClickListener {
            eliminarDesafio(desafio.id)
        }

        return rootView
    }


    // FUNCIÓN PARA INICIALIZAR LAS VISTAS DEL MAPA
    private fun initializeMapViews() {
        try {
            challengeMap = binding.root.findViewById(R.id.challenge_map)
            locationCard = binding.root.findViewById(R.id.location_card)
            locationAddress = binding.root.findViewById(R.id.location_address)

            Log.d("ItemDetailFragment", "Vistas del mapa inicializadas")
            Log.d("ItemDetailFragment", "challengeMap: ${challengeMap != null}")
            Log.d("ItemDetailFragment", "locationCard: ${locationCard != null}")

            // Configurar el mapa si existe
            challengeMap?.let { map ->
                map.setTileSource(TileSourceFactory.MAPNIK)
                map.setMultiTouchControls(true)
                map.controller.setZoom(15.0)
                Log.d("ItemDetailFragment", "Mapa configurado correctamente")
            }
        } catch (e: Exception) {
            Log.e("ItemDetailFragment", "Error inicializando mapa: ${e.message}")
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
                    // CORREGIR: Cargar datos de ubicación con nombres correctos
                    val latitude = document.getDouble("ubicacion_latitude")
                    val longitude = document.getDouble("ubicacion_longitude")
                    val locationName = document.getString("ubicacion_nombre")

                    Log.d("DEBUG_MAP", "=== DATOS DE UBICACIÓN ===")
                    Log.d("DEBUG_MAP", "Latitude: $latitude")
                    Log.d("DEBUG_MAP", "Longitude: $longitude")
                    Log.d("DEBUG_MAP", "Location Name: $locationName")
                    Log.d("DEBUG_MAP", "Document data: ${document.data}")

                    if (latitude != null && longitude != null) {
                        challengeLatitude = latitude
                        challengeLongitude = longitude
                        challengeLocationName = locationName
                        Log.d("DEBUG_MAP", "Configurando mapa con datos válidos")
                        setupMap()
                    } else {
                        Log.d("DEBUG_MAP", "No hay datos de ubicación válidos")
                        locationCard?.visibility = View.GONE
                    }

                    // Actualizar información básica
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
                            val diasCompletadosAnterior = diasCompletados.size
                            diasCompletados.clear()

                            for (doc in result) {
                                val dia = doc.getLong("dia")?.toInt() ?: 0
                                if (dia > 0) {
                                    diasCompletados.add(dia)
                                }
                            }

                            val diasCompletadosNuevo = diasCompletados.size
                            Log.d(
                                "ItemDetailFragment",
                                "Días completados: $diasCompletadosAnterior -> $diasCompletadosNuevo"
                            )

                            // Solo actualizar si no estamos ya actualizando y si hay cambios
                            if (!isUpdatingContent) {
                                updateContent()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(
                                "ItemDetailFragment",
                                "Error al cargar días completados: ${e.message}"
                            )
                            if (!isUpdatingContent) {
                                updateContent()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ItemDetailFragment", "Error al cargar desafío: ${e.message}")
            }
    }

    // FUNCIÓN PARA CONFIGURAR EL MAPA
    private fun setupMap() {
        val latitude = challengeLatitude ?: return
        val longitude = challengeLongitude ?: return
        val map = challengeMap ?: return

        try {
            Log.d("ItemDetailFragment", "Configurando mapa con coordenadas: $latitude, $longitude")

            // Mostrar la tarjeta del mapa
            locationCard?.visibility = View.VISIBLE
            Log.d("ItemDetailFragment", "Tarjeta del mapa mostrada")

            // Crear punto geográfico
            val challengePoint = GeoPoint(latitude, longitude)

            // Centrar el mapa en la ubicación
            map.controller.setCenter(challengePoint)

            // CAMBIAR: Aumentar el zoom para ver más cerca (era 15.0)
            map.controller.setZoom(18.0) // Zoom más cercano

            // Agregar marcador
            val marker = org.osmdroid.views.overlay.Marker(map)
            marker.position = challengePoint
            marker.setAnchor(0.5f, 1.0f)
            marker.title = "Ubicación del Desafío"

            map.overlays.clear()
            map.overlays.add(marker)

            Log.d("ItemDetailFragment", "Marcador agregado al mapa")

            // Actualizar la dirección
            updateLocationAddress(latitude, longitude)

            // Forzar actualización del mapa
            map.invalidate()

        } catch (e: Exception) {
            Log.e("ItemDetailFragment", "Error configurando mapa: ${e.message}")
            locationCard?.visibility = View.GONE
        }
    }

    // FUNCIÓN PARA ACTUALIZAR LA DIRECCIÓN
    private fun updateLocationAddress(latitude: Double, longitude: Double) {
        if (challengeLocationName != null) {
            locationAddress?.text = challengeLocationName
        } else {
            locationAddress?.text = "Lat: ${String.format("%.4f", latitude)}, " +
                    "Lng: ${String.format("%.4f", longitude)}"
        }
    }

    private fun updateBasicInfo() {
        // Actualizar información básica del desafío
        binding.root.findViewById<TextView>(R.id.challenge_title)?.text = desafio.nombre
        binding.root.findViewById<TextView>(R.id.challenge_description)?.text = desafio.descripcion
        binding.root.findViewById<TextView>(R.id.challenge_duration)?.text = "${desafio.dias} días"
    }

    private fun updateContent() {
        // Evitar múltiples actualizaciones simultáneas
        if (isUpdatingContent) {
            Log.d("ItemDetailFragment", "Ya se está actualizando el contenido, saltando...")
            return
        }

        isUpdatingContent = true
        val uid = auth.currentUser?.uid ?: return

        // PRIMERO verificar si el desafío ha finalizado
        verificarSiDesafioFinalizado { desafioFinalizado ->
            if (desafioFinalizado) {
                Log.d("ItemDetailFragment", "Desafío finalizado detectado - Mostrando vista especial")
                // Mostrar vista de desafío finalizado
                mostrarVistaDesafioFinalizado()
                isUpdatingContent = false
                return@verificarSiDesafioFinalizado
            }

            // Si no ha finalizado, continuar con el flujo normal
            Log.d("ItemDetailFragment", "Desafío activo - Mostrando contenido normal")

            // Resto del código original de updateContent()...
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
                        val estado = document.getString("estado") ?: "Indefinido"
                        val completados = diasCompletados.size

                        // Calcular el día actual basado en la fecha de inicio
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

                        // Obtener los hábitos del día actual con su estado
                        obtenerHabitosDiaActual(uid, desafio.id) { habitosDelDia ->
                            // Configurar UI con los datos de Firestore
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

        // Actualizar progreso - CORREGIDO: Mostrar el día actual calculado
        binding.root.findViewById<TextView>(R.id.progress_subtitle)?.text =
            "Día $diaActual de $dias"

        // Cambiar el texto del título de progreso
        binding.root.findViewById<TextView>(R.id.progress_title)?.text = "Tu progreso"

        binding.root.findViewById<TextView>(R.id.challenge_duration)?.text = "$dias días"

        // Actualizar barra de progreso - CORREGIDO: Usar días completados, no día actual
        val progressBar = binding.root.findViewById<ProgressBar>(R.id.circular_progress)
        progressBar?.let {
            it.max = dias
            it.progress = completados // Usar días completados para el progreso
            Log.d("ItemDetailFragment", "Progress bar actualizada: $completados/$dias")
        }

        // Actualizar porcentaje - CORREGIDO: Basado en días completados
        val porcentaje = if (dias > 0) (completados * 100) / dias else 0
        binding.root.findViewById<TextView>(R.id.progress_text)?.text = "$porcentaje%"

        Log.d(
            "ItemDetailFragment",
            "Información actualizada - Día actual: $diaActual, Días completados: $completados, Porcentaje: $porcentaje%"
        )
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
                        Log.d(
                            "ItemDetailFragment",
                            "Cambios detectados via listener: ${diasCompletados.size} -> ${nuevosCompletados.size}"
                        )
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

    private fun verificarEstadoDia(
        uid: String,
        desafioId: String,
        diaId: String,
        habitos: List<Map<String, Any>>
    ) {
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
                            Log.d(
                                "Firestore",
                                "Estado del día actualizado: completado = $todosCompletados"
                            )

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
                            Log.d(
                                "Firestore",
                                "Hábito actualizado correctamente: $habitIndex -> $completado"
                            )
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
                    Log.d(
                        "ItemDetailFragment",
                        "Creando ${habitosLocales.size} elementos de hábitos"
                    )
                    Log.d(
                        "ItemDetailFragment",
                        "Día completado: $diaCompletado, Todos hábitos completados: $todosHabitosCompletados"
                    )

                    for ((index, habit) in habitosLocales.withIndex()) {
                        val habitName = habit["nombre"] as? String ?: "Hábito sin nombre"
                        val habitCompleted = habit["completado"] as? Boolean ?: false

                        val habitView =
                            inflater.inflate(R.layout.habito_item, habitsContainer, false)
                        val habitNameText = habitView.findViewById<TextView>(R.id.habito_nombre)
                        val habitIcon = habitView.findViewById<ImageView>(R.id.habito_icono)

                        habitNameText.text = habitName
                        habitIcon.setImageResource(
                            if (habitCompleted) R.drawable.ic_check_green else R.drawable.ic_circle_empty
                        )

                        habitIcon.setOnClickListener {
                            val estadoActual =
                                habitosLocales[index]["completado"] as? Boolean ?: false
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

                    // Actualizar el estado inicial en la UI basado en si todos están completados
                    actualizarEstadoUI(todosHabitosCompletados)

                    Log.d(
                        "ItemDetailFragment",
                        "UI actualizada con ${habitsContainer.childCount} elementos"
                    )
                }
            } else {
                Log.e("Firestore", "No se encontró el día actual.")
                Toast.makeText(
                    context,
                    "No se encontró información del día actual",
                    Toast.LENGTH_SHORT
                ).show()
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
        // Obtener información del desafío para crear los hábitos base
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .get()
            .addOnSuccessListener { desafioDoc ->
                // Calcular el día actual basado en la fecha de inicio
                val fechaInicioTimestamp = desafioDoc.getTimestamp("fechaInicio")
                val fechaCreacionTimestamp = desafioDoc.getTimestamp("fechaCreacion")
                val fechaInicio = fechaInicioTimestamp?.toDate() ?: fechaCreacionTimestamp?.toDate()
                val fechaInicioString = fechaInicio?.let {
                    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    formatter.format(it)
                } ?: LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                val diaActualCalculado = calcularDiaActual(fechaInicioString)

                // Obtener hábitos base del desafío
                val habitosBase =
                    desafioDoc.get("habitos") as? List<Map<String, Any>> ?: emptyList()

                // Crear hábitos para el día actual basados en los hábitos del desafío
                val habitosDelDia = habitosBase.map { habitoBase ->
                    mapOf(
                        "nombre" to (habitoBase["nombre"] as? String ?: "Hábito sin nombre"),
                        "completado" to false
                    )
                }

                val nuevoDia = mapOf(
                    "completado" to false,
                    "dia" to diaActualCalculado, // Usar el día calculado
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

    private fun obtenerHabitosDiaActual(
        uid: String,
        desafioId: String,
        callback: (List<Map<String, Any>>) -> Unit
    ) {
        val fechaHoy = LocalDate.now().toString() // Fecha actual en formato "yyyy-MM-dd"

        // Primero verificar si el desafío ha finalizado
        verificarSiDesafioFinalizado { desafioFinalizado ->
            if (desafioFinalizado) {
                // No crear días adicionales, devolver lista vacía
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
                        Log.d("Firestore", "Hábitos del día actual obtenidos: ${habitos.size} hábitos")
                        callback(habitos)
                    } else {
                        // Verificar si ya hemos superado el límite de días del desafío
                        verificarLimiteDiasDesafio(uid, desafioId) { puedeCrearDia ->
                            if (puedeCrearDia) {
                                // Si no hay día para hoy y no hemos superado el límite, crear uno
                                Log.d("Firestore", "No se encontró día para hoy, creando...")
                                crearDiaActual(uid, desafioId, fechaHoy) { habitos ->
                                    callback(habitos)
                                }
                            } else {
                                // Si ya hemos superado el límite, no crear más días
                                Log.d("Firestore", "Límite de días alcanzado, no se crean más días")
                                callback(emptyList())
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error al obtener hábitos del día actual", e)
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

                    Log.d("ItemDetailFragment", "Verificando finalización - Último día: $diaNumero de ${desafio.dias}")
                    Log.d("ItemDetailFragment", "Fecha realización último día: $fechaRealizacion")

                    // Verificar si llegamos al último día del desafío
                    if (diaNumero >= desafio.dias && fechaRealizacion != null) {
                        try {
                            val fechaUltimoDia = LocalDate.parse(fechaRealizacion, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            val fechaHoy = LocalDate.now()
                            val diasTranscurridos = ChronoUnit.DAYS.between(fechaUltimoDia, fechaHoy)

                            Log.d("ItemDetailFragment", "Días transcurridos desde último día: $diasTranscurridos")

                            // Si ha pasado más de 1 día desde el último día del desafío
                            val desafioFinalizado = diasTranscurridos >= 1
                            Log.d("ItemDetailFragment", "¿Desafío finalizado? $desafioFinalizado")
                            callback(desafioFinalizado)
                        } catch (e: Exception) {
                            Log.e("ItemDetailFragment", "Error parseando fecha: ${e.message}")
                            callback(false)
                        }
                    } else {
                        Log.d("ItemDetailFragment", "Desafío no ha llegado al final o no hay fecha")
                        callback(false)
                    }
                } else {
                    Log.d("ItemDetailFragment", "No hay días registrados")
                    callback(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e("ItemDetailFragment", "Error verificando finalización: ${e.message}")
                callback(false)
            }
    }

    private fun mostrarVistaDesafioFinalizado() {
        Log.d("ItemDetailFragment", "Mostrando vista de desafío finalizado")

        // Ocultar TODOS los elementos del contenido normal
        ocultarContenidoNormal()

        // Crear o mostrar vista de finalización
        val finalizacionContainer = binding.root.findViewById<LinearLayout>(R.id.finalizacion_container)
            ?: crearVistaFinalizacion()

        finalizacionContainer.visibility = View.VISIBLE

        // Calcular días completados para mostrar estadísticas
        calcularEstadisticasFinales { diasCompletados, totalDias ->
            actualizarEstadisticasFinales(finalizacionContainer, diasCompletados, totalDias)
        }
    }

    private fun ocultarContenidoNormal() {
        // Ocultar descripción
        binding.root.findViewById<LinearLayout>(R.id.item_description)?.visibility = View.GONE

        // Ocultar listado de hábitos
        binding.root.findViewById<LinearLayout>(R.id.item_habits)?.visibility = View.GONE

        // Ocultar etiquetas
        binding.root.findViewById<LinearLayout>(R.id.item_etiquetas)?.visibility = View.GONE

        // Ocultar duración
        binding.root.findViewById<LinearLayout>(R.id.item_duracion)?.visibility = View.GONE

        // Ocultar estado del día
        binding.root.findViewById<LinearLayout>(R.id.item_estado)?.visibility = View.GONE

        binding.root.findViewById<LinearLayout>(R.id.item_location)?.visibility = View.GONE

        // Ocultar sección de progreso si existe
        binding.root.findViewById<View>(R.id.progress_section)?.visibility = View.GONE

        // Ocultar botones de edición y eliminación
        binding.btnEditChallenge?.visibility = View.GONE
        binding.btnDeleteChallenge?.visibility = View.GONE

//        // Ocultar cualquier otra vista relacionada con el contenido del día
//        binding.root.findViewById<View>(R.id.day_content_section)?.visibility = View.GONE

        Log.d("ItemDetailFragment", "Contenido normal ocultado")
    }

    // Nuevo método para crear la vista de finalización
    private fun crearVistaFinalizacion(): LinearLayout {
        val context = requireContext()

        // Crear container principal
        val finalizacionContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(32, 64, 32, 32) // Más margen superior
            }
            id = R.id.finalizacion_container
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        // Icono de finalización grande
        val icono = ImageView(context).apply {
            setImageResource(R.drawable.ic_check_green)
            layoutParams = LinearLayout.LayoutParams(150, 150).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 32)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        // Título principal
        val titulo = TextView(context).apply {
            text = "¡Desafío Terminado!"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        // Mensaje de felicitación
        val mensaje = TextView(context).apply {
            text = "¡Felicitaciones! Has completado tu desafío de ${desafio.dias} días."
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 32)
            }
        }

        // Card para estadísticas
        val statsCard = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 32)
            }
            radius = 16f
            cardElevation = 8f
            setCardBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        }

        val statsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 24, 32, 24)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val statsTitle = TextView(context).apply {
            text = "Resumen del Desafío"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        // Estadísticas
        val estadisticas = TextView(context).apply {
            text = "Cargando estadísticas..."
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            id = R.id.estadisticas_finales
        }

        statsContainer.addView(statsTitle)
        statsContainer.addView(estadisticas)
        statsCard.addView(statsContainer)

        // Botón finalizar con diseño mejorado
        val botonFinalizar = androidx.appcompat.widget.AppCompatButton(context).apply {
            text = "Finalizar Desafío"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(android.graphics.Color.parseColor("#FF5722"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140 // Altura fija para el botón
            ).apply {
                setMargins(0, 16, 0, 16)
            }

            // Esquinas redondeadas
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(android.graphics.Color.parseColor("#FF5722"))
            }

            setOnClickListener {
                finalizarDesafio()
            }
        }

        // Agregar elementos al container
        finalizacionContainer.addView(icono)
        finalizacionContainer.addView(titulo)
        finalizacionContainer.addView(mensaje)
        finalizacionContainer.addView(statsCard)
        finalizacionContainer.addView(botonFinalizar)

        // Agregar al layout principal
        val mainContainer = binding.root.findViewById<LinearLayout>(R.id.main_content_container)
            ?: binding.root as ViewGroup

        if (mainContainer is LinearLayout) {
            mainContainer.addView(finalizacionContainer)
        } else {
            (mainContainer as ViewGroup).addView(finalizacionContainer)
        }

        Log.d("ItemDetailFragment", "Vista de finalización creada")
        return finalizacionContainer
    }

    // Nuevo método para calcular estadísticas finales
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
                Log.e("ItemDetailFragment", "Error calculando estadísticas: ${e.message}")
                callback(0, desafio.dias)
            }
    }

    // Nuevo método para actualizar estadísticas en la vista
    private fun actualizarEstadisticasFinales(container: LinearLayout, diasCompletados: Int, totalDias: Int) {
        val estadisticasView = container.findViewById<TextView>(R.id.estadisticas_finales)
        val porcentaje = if (totalDias > 0) (diasCompletados * 100) / totalDias else 0

        val estadisticasText = when {
            porcentaje == 100 -> "¡Perfecto! 🎉\n$diasCompletados de $totalDias días completados\n(100% de éxito)"
            porcentaje >= 80 -> "¡Excelente! 👏\n$diasCompletados de $totalDias días completados\n($porcentaje% de éxito)"
            porcentaje >= 60 -> "¡Buen trabajo! 👍\n$diasCompletados de $totalDias días completados\n($porcentaje% de éxito)"
            else -> "¡Sigue intentando! 💪\n$diasCompletados de $totalDias días completados\n($porcentaje% de éxito)"
        }

        estadisticasView?.text = estadisticasText
        Log.d("ItemDetailFragment", "Estadísticas actualizadas: $diasCompletados/$totalDias ($porcentaje%)")
    }

    // Nuevo método para finalizar el desafío
    private fun finalizarDesafio() {
        val context = requireContext()
        val uid = auth.currentUser?.uid ?: return

        // Mostrar progress dialog
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setMessage("Finalizando desafío...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        // Eliminar el desafío completo (incluye subcolecciones)
        eliminarDesafioCompleto(uid, desafio.id) { success ->
            progressDialog.dismiss()

            if (success) {
                Toast.makeText(context, "¡Desafío finalizado exitosamente!", Toast.LENGTH_LONG).show()
                // Redirigir a ItemListFragment
                try {
                    findNavController().navigate(R.id.itemListFragment)
                } catch (e: Exception) {
                    Log.e("ItemDetailFragment", "Error navegando: ${e.message}")
                    // Alternativa si falla la navegación
                    requireActivity().onBackPressed()
                }
            } else {
                Toast.makeText(context, "Error al finalizar el desafío", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Nuevo método para eliminar el desafío completo incluyendo subcolecciones
    private fun eliminarDesafioCompleto(uid: String, desafioId: String, callback: (Boolean) -> Unit) {
        try {
            // Primero eliminar todos los días
            firestore.collection("usuarios")
                .document(uid)
                .collection("desafios")
                .document(desafioId)
                .collection("dias")
                .get()
                .addOnSuccessListener { diasSnapshot ->
                    val batch = firestore.batch()

                    // Agregar eliminación de días al batch
                    for (diaDoc in diasSnapshot.documents) {
                        batch.delete(diaDoc.reference)
                    }

                    // Agregar eliminación del desafío al batch
                    val desafioRef = firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                    batch.delete(desafioRef)

                    // Ejecutar batch
                    batch.commit()
                        .addOnSuccessListener {
                            Log.d("ItemDetailFragment", "Desafío eliminado completamente")
                            callback(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e("ItemDetailFragment", "Error eliminando desafío: ${e.message}")
                            callback(false)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("ItemDetailFragment", "Error obteniendo días para eliminar: ${e.message}")
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e("ItemDetailFragment", "Error general en eliminarDesafioCompleto: ${e.message}")
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

                    // Obtener el límite de días del desafío
                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                        .get()
                        .addOnSuccessListener { desafioDoc ->
                            val limiteDias = desafioDoc.getLong("dias")?.toInt() ?: 0
                            val puedeCrear = diaNumero < limiteDias
                            Log.d("ItemDetailFragment", "Último día: $diaNumero, Límite: $limiteDias, Puede crear: $puedeCrear")
                            callback(puedeCrear)
                        }
                        .addOnFailureListener {
                            callback(false)
                        }
                } else {
                    // Si no hay días, puede crear el primero
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
                Toast.makeText(
                    context,
                    getString(R.string.error_navigating_home),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val todayLayout = bottomNav.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.todayFragment)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    getString(R.string.error_navigating_home),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val exploreLayout = bottomNav.getChildAt(2) as? LinearLayout
        exploreLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.publicChallengeFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val profileLayout = bottomNav.getChildAt(3) as? LinearLayout
        profileLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.profileFragment)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    getString(R.string.error_navigating_profile),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        const val ARG_ITEM_ID = "item_id"
    }

    override fun onResume() {
        super.onResume()
        challengeMap?.onResume()
        Log.d("ItemDetailFragment", "onResume - Mapa reanudado")

        // CORREGIR: Verificar que desafio esté inicializado antes de recargar
        if (::desafio.isInitialized && !isUpdatingContent) {
            Log.d("ItemDetailFragment", "onResume: Recargando datos")
            cargarDiasCompletados()
        }
    }

    private fun eliminarDesafio(id: String) {
        val context = requireContext()

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Eliminar Desafío")
            .setMessage("¿Estás seguro de que deseas eliminar este desafío? Esta acción no se puede deshacer.")
            .setPositiveButton("Sí") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                firestore.collection("usuarios")
                    .document(uid)
                    .collection("desafios")
                    .document(id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Desafío eliminado", Toast.LENGTH_SHORT).show()
                        // Redirigir a ItemListFragment
                        findNavController().navigate(R.id.itemListFragment)
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Error al eliminar desafío", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        challengeMap?.onPause()
        Log.d("ItemDetailFragment", "onPause - Mapa pausado")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}