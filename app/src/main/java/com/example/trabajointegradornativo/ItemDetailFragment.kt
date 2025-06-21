package com.example.trabajointegradornativo

import android.content.ClipData
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
import com.example.trabajointegradornativo.databinding.FragmentItemDetailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

// IMPORTACIONES PARA EL MAPA
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.location.Geocoder
import androidx.cardview.widget.CardView
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
        val visibilidad: String = "privado"
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
        Configuration.getInstance().load(requireContext(),
            requireContext().getSharedPreferences("osmdroid", 0))

        arguments?.let { args ->
            // CORREGIR: Manejar tanto el objeto Desafio como el ID
            val desafioFromArgs = args.getParcelable<ItemListFragment.Desafio>("desafio")
            val desafioId = args.getString("desafio_id") ?: args.getString(ARG_ITEM_ID)

            if (desafioFromArgs != null) {
                Log.d("ItemDetailFragment", "Desafío recibido desde argumentos: ${desafioFromArgs.nombre}")
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
                        visibilidad = document.getString("visibilidad") ?: "privado"
                    )

                    Log.d("ItemDetailFragment", "Desafío cargado: ${desafio.nombre}")
                    cargarDiasCompletados()
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        val rootView = binding.root

        Log.d("ItemDetailFragment", "onCreateView: Inicializando vistas")

        // INICIALIZAR VISTAS DEL MAPA
        initializeMapViews()

        setupBottomNavigation(rootView)

        // Configuración del botón para editar desafío
        rootView.findViewById<View>(R.id.btn_edit_challenge)?.setOnClickListener {
            Toast.makeText(context, "Función de edición no implementada", Toast.LENGTH_SHORT).show()
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
            val marker = Marker(map)
            marker.position = challengePoint
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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

    private fun setupBottomNavigation(view: View) {
        val bottomNav = view.findViewById<LinearLayout>(R.id.bottom_navigation)

        val homeLayout = bottomNav.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.itemListFragment)
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.error_navigating_home), Toast.LENGTH_SHORT).show()
            }
        }

        val todayLayout = bottomNav.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.todayFragment)
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.error_navigating_home), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, getString(R.string.error_navigating_profile), Toast.LENGTH_SHORT).show()
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

        // Solo recargar si no estamos ya actualizando
        if (!isUpdatingContent) {
            Log.d("ItemDetailFragment", "onResume: Recargando datos")
            cargarDiasCompletados()
        }
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