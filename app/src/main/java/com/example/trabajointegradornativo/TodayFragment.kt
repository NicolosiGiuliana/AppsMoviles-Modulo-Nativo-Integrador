package com.example.trabajointegradornativo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

data class Habito(
    val nombre: String,
    var completado: Boolean = false,
    var fotoUrl: String? = null,
    var comentario: String? = null
)

data class Desafio(
    val id: String,
    val nombre: String,
    var diaActual: Int,
    val totalDias: Int,
    val habitos: MutableList<Habito>
)

class TodayFragment : Fragment() {

    companion object {
        private const val TAG = "TodayFragment"
        private const val MAX_IMAGE_SIZE = 1024
        private const val JPEG_QUALITY = 80
        private const val PREF_LAST_DATE = "last_date_checked"
    }

    private lateinit var dateTextView: TextView
    private lateinit var progressTextView: TextView
    private lateinit var activitiesContainer: LinearLayout

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val imgBBUploader = ImgBBUploader()

    private var desafiosActivos = mutableListOf<Desafio>()
    private var habitoSeleccionadoParaFoto: Pair<String, String>? = null
    private var ultimaFechaVerificada: String? = null

    private val firestoreListeners = mutableListOf<ListenerRegistration>()

    private val takePictureLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { bitmap ->
                val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
                mostrarDialogoComentario(resizedBitmap)
            }
        }
    }

    private val pickImageLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedImage(uri)
            }
        }
    }

    private val requestCameraPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            abrirCamara()
        } else {
            Toast.makeText(context, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStoragePermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            abrirGaleria()
        } else {
            Toast.makeText(context, getString(R.string.storage_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.action_to_today_fragment, container, false)

        inicializarViews(view)
        configurarFechaActual()
        setupBottomNavigation(view)
        iniciarVerificacionFecha()

        return view
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Verificando cambio de fecha")

        val fechaActual = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (ultimaFechaVerificada != fechaActual) {
            Log.d(TAG, "onResume: Fecha cambió, recargando datos")
            configurarFechaActual()
        } else {
            Log.d(TAG, "onResume: Misma fecha, recargando datos normalmente")
            cargarDesafiosDelUsuario()
        }
    }

    override fun onPause() {
        super.onPause()
        limpiarListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        limpiarListeners()
    }

    private fun limpiarListeners() {
        firestoreListeners.forEach { listener ->
            listener.remove()
        }
        firestoreListeners.clear()
    }

    private fun inicializarViews(view: View) {
        dateTextView = view.findViewById(R.id.date_today)
        progressTextView = view.findViewById(R.id.progress_summary)
        activitiesContainer = view.findViewById(R.id.activities_container)
    }

    private fun configurarFechaActual() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault())
        val fechaFormateada = dateFormat.format(calendar.time)
        dateTextView.text = fechaFormateada.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

        val fechaActual = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val sharedPrefs = requireActivity().getSharedPreferences("today_fragment", android.content.Context.MODE_PRIVATE)

        if (ultimaFechaVerificada == null) {
            ultimaFechaVerificada = sharedPrefs.getString(PREF_LAST_DATE, null)
        }

        if (ultimaFechaVerificada != fechaActual) {
            ultimaFechaVerificada = fechaActual
            sharedPrefs.edit().putString(PREF_LAST_DATE, fechaActual).apply()
            Log.d(TAG, "Fecha cambió a: $fechaActual - Recargando datos")
            cargarDesafiosDelUsuario()
        }
    }

    private fun cargarDesafiosDelUsuario() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, getString(R.string.error_user_not_authenticated), Toast.LENGTH_SHORT).show()
            return
        }

        limpiarListeners()
        desafiosActivos.clear()

        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        Log.d(TAG, "Cargando desafíos para fecha: $fechaHoy")

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .whereEqualTo("estado", "activo")
            .get()
            .addOnSuccessListener { desafiosSnapshot ->
                if (desafiosSnapshot.isEmpty) {
                    mostrarMensajeSinDesafios()
                    return@addOnSuccessListener
                }

                var desafiosEncontrados = 0
                val totalDesafios = desafiosSnapshot.size()

                for (desafioDoc in desafiosSnapshot) {
                    val desafioId = desafioDoc.id
                    val nombreDesafio = desafioDoc.getString("nombre") ?: getString(R.string.challenge_without_name)
                    val totalDias = desafioDoc.getLong("dias")?.toInt() ?: 30

                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                        .collection("dias")
                        .whereEqualTo("fechaRealizacion", fechaHoy)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { diasSnapshot ->
                            desafiosEncontrados++

                            if (!diasSnapshot.isEmpty) {
                                val diaDoc = diasSnapshot.first()
                                val nombreDia = diaDoc.id
                                val numeroDia = nombreDia.removePrefix("dia_").toIntOrNull() ?: 1

                                Log.d(TAG, "Desafío $nombreDesafio tiene día $numeroDia para hoy")
                                cargarHabitosDelDiaConListener(desafioId, nombreDesafio, numeroDia, totalDias)
                            } else {
                                Log.d(TAG, "Desafío $nombreDesafio no tiene día para hoy")
                            }

                            if (desafiosEncontrados == totalDesafios && desafiosActivos.isEmpty()) {
                                mostrarMensajeSinDesafios()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error al buscar días para desafío $desafioId: ${e.message}")
                            desafiosEncontrados++
                            if (desafiosEncontrados == totalDesafios && desafiosActivos.isEmpty()) {
                                mostrarMensajeSinDesafios()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al cargar desafíos: ${e.message}")
                Toast.makeText(context, "Error al cargar desafíos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarHabitosDelDiaConListener(desafioId: String, nombreDesafio: String, diaActual: Int, totalDias: Int) {
        val uid = auth.currentUser?.uid ?: return
        val fechaHoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        Log.d(TAG, "Configurando listener para desafío: $nombreDesafio, día: $diaActual")

        val diaListener = firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")
            .document("dia_$diaActual")
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Log.e(TAG, "Error al escuchar cambios en el día: ${error.message}")
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    val fechaRealizacion = document.getString("fechaRealizacion")
                    val diaCompletado = document.getBoolean("completado") ?: false

                    Log.d(TAG, "Día $diaActual - Fecha realización: $fechaRealizacion, Completado: $diaCompletado, Fecha hoy: $fechaHoy")

                    if (fechaRealizacion == fechaHoy && !diaCompletado) {
                        cargarHabitosDelDia(desafioId, nombreDesafio, diaActual, totalDias, document)
                    } else if (fechaRealizacion == fechaHoy && diaCompletado) {
                        removerDesafioDeInterfaz(desafioId)
                    } else if (fechaRealizacion != fechaHoy) {
                        removerDesafioDeInterfaz(desafioId)
                    }
                } else {
                    Log.d(TAG, "Documento del día $diaActual no existe")
                    removerDesafioDeInterfaz(desafioId)
                }
            }

        firestoreListeners.add(diaListener)
    }

    private fun cargarHabitosDelDia(desafioId: String, nombreDesafio: String, diaActual: Int, totalDias: Int, document: DocumentSnapshot) {
        val habitosData = document.get("habitos") as? List<Map<String, Any>> ?: emptyList()
        val habitos = habitosData.map { habitoMap: Map<String, Any> ->
            Habito(
                nombre = habitoMap["nombre"] as? String ?: "",
                completado = habitoMap["completado"] as? Boolean ?: false,
                fotoUrl = habitoMap["fotoUrl"] as? String,
                comentario = habitoMap["comentario"] as? String
            )
        }.toMutableList()

        val desafioExistente = desafiosActivos.find { it.id == desafioId }
        if (desafioExistente != null) {
            desafioExistente.habitos.clear()
            desafioExistente.habitos.addAll(habitos)
        } else {
            val desafio = Desafio(desafioId, nombreDesafio, diaActual, totalDias, habitos)
            desafiosActivos.add(desafio)
        }

        Log.d(TAG, "Desafío actualizado: $nombreDesafio")
        actualizarInterfaz()
    }

    private fun removerDesafioDeInterfaz(desafioId: String) {
        val posicionDesafio = desafiosActivos.indexOfFirst { it.id == desafioId }
        if (posicionDesafio != -1) {
            desafiosActivos.removeAt(posicionDesafio)
            Log.d(TAG, "Desafío removido de la interfaz: $desafioId")
        }
        actualizarInterfaz()
    }

    private fun iniciarVerificacionFecha() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val fechaActual = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                if (ultimaFechaVerificada != fechaActual) {
                    Log.d(TAG, "Cambio de fecha detectado: $ultimaFechaVerificada -> $fechaActual")
                    configurarFechaActual()
                }
                handler.postDelayed(this, 30000)
            }
        }
        handler.post(runnable)
    }

    private fun mostrarMensajeSinDesafios() {
        activitiesContainer.removeAllViews()

        val mensajeLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 64, 32, 64)
        }

        val textoMensaje = TextView(requireContext()).apply {
            text = "🎉\n\n¡Excelente trabajo!\n\nHas completado todos tus desafíos por hoy.\n¡Sigue así mañana!"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        }

        mensajeLayout.addView(textoMensaje)
        activitiesContainer.addView(mensajeLayout)
        progressTextView.text = "¡Todos los desafíos completados!"
    }

    private fun actualizarInterfaz() {
        activitiesContainer.removeAllViews()

        if (desafiosActivos.isEmpty()) {
            mostrarMensajeSinDesafios()
            return
        }

        for (desafio in desafiosActivos) {
            agregarDesafioALaInterfaz(desafio)
        }

        actualizarResumenProgreso()
    }

    private fun agregarDesafioALaInterfaz(desafio: Desafio) {
        val cardView = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 32
            }
            radius = 24f
            cardElevation = 4f
            setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            isClickable = true
            isFocusable = true
            foreground = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            setOnClickListener {
                navegarADetalleDesafio(desafio.id)
            }
        }

        val cardLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val tituloDesafio = TextView(requireContext()).apply {
            text = getString(R.string.challenge_day_format, desafio.nombre, desafio.diaActual)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 24)
        }
        cardLayout.addView(tituloDesafio)

        for (habito in desafio.habitos) {
            val habitoLayout = crearLayoutHabito(desafio, habito)
            cardLayout.addView(habitoLayout)
        }

        cardView.addView(cardLayout)
        activitiesContainer.addView(cardView)
    }

    private fun crearLayoutHabito(desafio: Desafio, habito: Habito): LinearLayout {
        val habitoLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val horizontalLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val checkbox = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                marginEnd = 24
            }
            setImageResource(if (habito.completado) R.drawable.ic_check_green else R.drawable.ic_circle_empty)
            setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (habito.completado) android.R.color.holo_green_dark else android.R.color.darker_gray
                )
            )
            setOnClickListener {
                verificarSiDiaEstaCompletado(desafio) { diaCompletado ->
                    if (!diaCompletado) {
                        toggleHabito(desafio, habito, this)
                    } else {
                        Toast.makeText(context, getString(R.string.day_already_completed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val textoHabito = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = habito.nombre
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }

        val iconoCamara = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(32, 32)
            setImageResource(R.drawable.ic_camera)
            setColorFilter(ContextCompat.getColor(context, android.R.color.darker_gray))
            contentDescription = getString(R.string.camera_icon)
            setOnClickListener {
                habitoSeleccionadoParaFoto = Pair(desafio.id, habito.nombre)
                mostrarOpcionesFoto()
            }
        }

        horizontalLayout.addView(checkbox)
        horizontalLayout.addView(textoHabito)
        horizontalLayout.addView(iconoCamara)
        habitoLayout.addView(horizontalLayout)

        if (habito.fotoUrl != null || habito.comentario != null) {
            val contenidoExtra = crearContenidoExtra(habito)
            habitoLayout.addView(contenidoExtra)
        }

        return habitoLayout
    }

    private fun navegarADetalleDesafio(desafioId: String) {
        try {
            val bundle = Bundle().apply {
                putString("desafio_id", desafioId)
            }
            findNavController().navigate(R.id.itemDetailFragment, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar al detalle del desafío: ${e.message}")
            Toast.makeText(context, getString(R.string.error_navigating_to_detail), Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarOpcionesFoto() {
        val opciones = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.select_from_gallery),
            getString(R.string.cancel)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add_photo_to_habit))
            .setItems(opciones) { dialog, which ->
                when (which) {
                    0 -> verificarPermisosCamara()
                    1 -> verificarPermisosGaleria()
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun verificarSiDiaEstaCompletado(desafio: Desafio, callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .document("dia_${desafio.diaActual}")
            .get()
            .addOnSuccessListener { document ->
                val completado = document.getBoolean("completado") ?: false
                callback(completado)
            }
            .addOnFailureListener {
                firestore.collection("usuarios")
                    .document(uid)
                    .collection("desafios")
                    .document(desafio.id)
                    .collection("dias_completados")
                    .whereEqualTo("dia", desafio.diaActual)
                    .get()
                    .addOnSuccessListener { documents ->
                        callback(!documents.isEmpty)
                    }
                    .addOnFailureListener {
                        callback(false)
                    }
            }
    }

    private fun verificarYMarcarDiaCompletado(desafio: Desafio) {
        val todosCompletados = desafio.habitos.all { it.completado }

        if (todosCompletados) {
            marcarDiaComoCompletado(desafio)
        } else {
            desmarcarDiaComoCompletado(desafio)
        }
    }

    private fun marcarDiaComoCompletado(desafio: Desafio) {
        val uid = auth.currentUser?.uid ?: return

        val updates = hashMapOf<String, Any>(
            "completado" to true,
            "fecha_completado" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .document("dia_${desafio.diaActual}")
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Día marcado como completado")
                Toast.makeText(context, "¡Día completado! 🎉", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al marcar día como completado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun desmarcarDiaComoCompletado(desafio: Desafio) {
        val uid = auth.currentUser?.uid ?: return

        val updates = hashMapOf<String, Any>(
            "completado" to false
        )

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .document("dia_${desafio.diaActual}")
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Día desmarcado como completado")
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al desmarcar día: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun crearContenidoExtra(habito: Habito): LinearLayout {
        val extraLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 24, 0, 0)
        }

        if (habito.fotoUrl != null) {
            // IMAGEN MÁS GRANDE: de 160x160 a 240x240 dp
            val fotoContainer = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(240, 240).apply { // Aumentado de 160 a 240
                    bottomMargin = 24
                }
            }

            // Contenedor de la imagen con fondo
            val imageContainer = LinearLayout(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                gravity = android.view.Gravity.CENTER
            }

            // ImageView para mostrar la imagen real
            val imageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            }

            // Texto de loading (también más grande)
            val loadingText = TextView(requireContext()).apply {
                text = "Cargando..."
                textSize = 14f // Aumentado de 12f a 14f
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                gravity = android.view.Gravity.CENTER
            }

            imageContainer.addView(imageView)
            imageContainer.addView(loadingText)

            // Botón de eliminar (mantener tamaño proporcional)
            val deleteButton = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(52, 52).apply { // Ligeramente más grande: de 48 a 52
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                    topMargin = 8
                    rightMargin = 8
                }

                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundResource(android.R.drawable.btn_default)
                background.setTint(ContextCompat.getColor(context, android.R.color.holo_red_light))
                setColorFilter(ContextCompat.getColor(context, android.R.color.white))
                setPadding(10, 10, 10, 10) // Aumentado de 8 a 10

                contentDescription = "Eliminar imagen"
                visibility = View.GONE
                isClickable = true
                isFocusable = true
                foreground = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            }

            fotoContainer.addView(imageContainer)
            fotoContainer.addView(deleteButton)

            // Cargar la imagen
            cargarImagenEnMiniatura(habito.fotoUrl!!, imageView, loadingText, deleteButton)

            // Click para ver imagen en grande
            imageContainer.setOnClickListener {
                mostrarImagenEnDialogo(habito.fotoUrl!!)
            }

            // Click para eliminar imagen
            deleteButton.setOnClickListener {
                mostrarDialogoEliminarImagen(habito)
            }

            extraLayout.addView(fotoContainer)
        }

        if (habito.comentario != null && habito.comentario!!.isNotEmpty()) {
            val comentarioLayout = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_light))
                setPadding(24, 24, 24, 24)
            }

            val textoComentario = TextView(requireContext()).apply {
                text = "💬 ${habito.comentario}"
                textSize = 13f // Ligeramente más grande: de 12f a 13f
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }

            comentarioLayout.addView(textoComentario)
            extraLayout.addView(comentarioLayout)
        }

        return extraLayout
    }
    private fun cargarImagenEnMiniatura(url: String, imageView: ImageView, loadingText: TextView, deleteButton: ImageView) {
        Thread {
            try {
                val connection = java.net.URL(url).openConnection()
                connection.doInput = true
                connection.connect()
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                requireActivity().runOnUiThread {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        loadingText.visibility = View.GONE
                        deleteButton.visibility = View.VISIBLE
                        Log.d(TAG, "Imagen cargada en miniatura desde: $url")
                    } else {
                        imageView.setImageDrawable(
                            ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_delete)
                        )
                        loadingText.text = "Error"
                        loadingText.visibility = View.VISIBLE
                        deleteButton.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar imagen en miniatura: ${e.message}")
                requireActivity().runOnUiThread {
                    imageView.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_delete)
                    )
                    loadingText.text = "Error al cargar"
                    loadingText.visibility = View.VISIBLE
                    deleteButton.visibility = View.GONE
                }
            }
        }.start()
    }
    private fun mostrarDialogoEliminarImagen(habito: Habito) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar imagen")
            .setMessage("¿Estás seguro de que quieres eliminar esta imagen del hábito '${habito.nombre}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarImagenDelHabito(habito)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarImagenDelHabito(habito: Habito) {
        val desafio = desafiosActivos.find { desafio ->
            desafio.habitos.any { it.nombre == habito.nombre }
        } ?: return

        // Mostrar loading
        Toast.makeText(requireContext(), "Eliminando imagen...", Toast.LENGTH_SHORT).show()

        // Limpiar la URL de la imagen del hábito
        habito.fotoUrl = null

        // Guardar cambios en Firestore
        guardarProgresoHabito(desafio, habito)

        // Actualizar la interfaz
        actualizarInterfaz()

        Toast.makeText(requireContext(), "Imagen eliminada", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Imagen eliminada del hábito: ${habito.nombre}")
    }

    private fun abrirImagenEnNavegador(url: String) {
        mostrarImagenEnDialogo(url)
    }

    private fun mostrarImagenEnDialogo(url: String) {
        try {
            // Crear ImageView para mostrar la imagen
            val imageView = ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            // Crear el diálogo
            val dialog = AlertDialog.Builder(requireContext())
                .setView(imageView)
                .setPositiveButton("Cerrar") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            // Mostrar loading mientras carga la imagen
            val progressBar = ProgressBar(requireContext())
            imageView.setImageDrawable(ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_gallery))

            dialog.show()

            // Cargar la imagen de forma asíncrona
            Thread {
                try {
                    val connection = java.net.URL(url).openConnection()
                    connection.doInput = true
                    connection.connect()
                    val inputStream = connection.getInputStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    // Actualizar la UI en el hilo principal
                    requireActivity().runOnUiThread {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                        } else {
                            imageView.setImageDrawable(ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_delete))
                            Toast.makeText(context, "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al cargar imagen: ${e.message}")
                    requireActivity().runOnUiThread {
                        imageView.setImageDrawable(ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_delete))
                        Toast.makeText(context, "Error al cargar la imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "Error al mostrar imagen: ${e.message}")
            Toast.makeText(context, "Error al mostrar imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleHabito(desafio: Desafio, habito: Habito, checkbox: ImageView) {
        habito.completado = !habito.completado

        checkbox.setImageResource(if (habito.completado) R.drawable.ic_check_green else R.drawable.ic_circle_empty)
        checkbox.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                if (habito.completado) android.R.color.holo_green_dark else android.R.color.darker_gray
            )
        )

        guardarProgresoHabito(desafio, habito)
        verificarYMarcarDiaCompletado(desafio)
        actualizarResumenProgreso()
    }

    private fun guardarProgresoHabito(desafio: Desafio, habito: Habito) {
        val uid = auth.currentUser?.uid ?: return

        val habitosData = desafio.habitos.map { h: Habito ->
            mapOf(
                "nombre" to h.nombre,
                "completado" to h.completado,
                "fotoUrl" to h.fotoUrl,
                "comentario" to h.comentario
            )
        }

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias")
            .document("dia_${desafio.diaActual}")
            .update("habitos", habitosData)
            .addOnFailureListener { e ->
                Toast.makeText(context, getString(R.string.error_saving_progress, e.message), Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarResumenProgreso() {
        if (desafiosActivos.isNotEmpty()) {
            val totalHabitos = desafiosActivos.sumOf { it.habitos.size }
            val habitosCompletados = desafiosActivos.sumOf { desafio ->
                desafio.habitos.count { it.completado }
            }
            progressTextView.text = getString(R.string.progress_format, habitosCompletados, totalHabitos)
        }
    }

    private fun verificarPermisosCamara() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                abrirCamara()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun verificarPermisosGaleria() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED -> {
                abrirGaleria()
            }
            else -> {
                requestStoragePermissionLauncher.launch(permission)
            }
        }
    }

    private fun abrirCamara() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            takePictureLauncher.launch(takePictureIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir cámara: ${e.message}")
            Toast.makeText(context, getString(R.string.error_accessing_camera, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun handleSelectedImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
                mostrarDialogoComentario(resizedBitmap)
            } else {
                Toast.makeText(requireContext(), getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show()
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Error al cargar imagen desde galería", e)
            Toast.makeText(requireContext(), getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al procesar imagen", e)
            Toast.makeText(requireContext(), getString(R.string.error_processing_image, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        Log.d(TAG, "Imagen original: ${width}x${height}")

        if (width <= maxSize && height <= maxSize) {
            Log.d(TAG, "Imagen ya tiene tamaño adecuado")
            return bitmap
        }

        val ratio = if (width > height) {
            maxSize.toFloat() / width.toFloat()
        } else {
            maxSize.toFloat() / height.toFloat()
        }

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        Log.d(TAG, "Redimensionando a: ${newWidth}x${newHeight}")

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun mostrarDialogoComentario(bitmap: Bitmap) {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.add_comment_optional)
            maxLines = 3
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add_comment))
            .setMessage(getString(R.string.add_comment_message))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val comentario = editText.text.toString().trim()
                subirFotoYGuardarComentario(bitmap, comentario)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun subirFotoYGuardarComentario(bitmap: Bitmap, comentario: String) {
        val habitoInfo = habitoSeleccionadoParaFoto ?: return
        val (desafioId, habitoNombre) = habitoInfo

        Log.d(TAG, "Iniciando subida de imagen para hábito: $habitoNombre usando ImgBB")

        // Mostrar Toast en el hilo principal
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Subiendo imagen...", Toast.LENGTH_SHORT).show()
        }

        imgBBUploader.uploadImage(bitmap, object : ImgBBUploader.UploadCallback {
            override fun onSuccess(imageUrl: String, deleteUrl: String) {
                Log.d(TAG, "Imagen subida exitosamente a ImgBB: $imageUrl")

                // Ejecutar en el hilo principal
                requireActivity().runOnUiThread {
                    actualizarHabitoConFotoYComentario(desafioId, habitoNombre, imageUrl, comentario)
                    Toast.makeText(requireContext(), "¡Foto guardada exitosamente! 📷", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "Error al subir imagen a ImgBB: $error")

                // Ejecutar en el hilo principal
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error al subir imagen: $error", Toast.LENGTH_LONG).show()
                    mostrarDialogoErrorSubida(bitmap, comentario)
                }
            }

            override fun onProgress(progress: Int) {
                Log.d(TAG, "Progreso de subida a ImgBB: $progress%")

                // Opcional: mostrar progreso en el hilo principal
                if (progress == 100) {
                    requireActivity().runOnUiThread {
                        Log.d(TAG, "Subida completada, procesando respuesta...")
                    }
                }
            }
        })
    }

    private fun mostrarDialogoErrorSubida(bitmap: Bitmap, comentario: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Error al subir imagen")
            .setMessage("Error al subir la imagen a ImgBB. ¿Qué deseas hacer?")
            .setPositiveButton("Reintentar") { _, _ ->
                subirFotoYGuardarComentario(bitmap, comentario)
            }
            .setNegativeButton("Solo guardar comentario") { _, _ ->
                val habitoInfo = habitoSeleccionadoParaFoto ?: return@setNegativeButton
                val (desafioId, habitoNombre) = habitoInfo
                if (comentario.isNotEmpty()) {
                    actualizarHabitoConFotoYComentario(desafioId, habitoNombre, null, comentario)
                    Toast.makeText(requireContext(), "Comentario guardado sin imagen", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun actualizarHabitoConFotoYComentario(desafioId: String, habitoNombre: String, fotoUrl: String?, comentario: String) {
        val desafio = desafiosActivos.find { it.id == desafioId } ?: return
        val habito = desafio.habitos.find { it.nombre == habitoNombre } ?: return

        if (fotoUrl != null) {
            habito.fotoUrl = fotoUrl
        }
        if (comentario.isNotEmpty()) {
            habito.comentario = comentario
        }

        guardarProgresoHabito(desafio, habito)

        Log.d(TAG, "Hábito actualizado: $habitoNombre con foto: ${fotoUrl != null}, comentario: ${comentario.isNotEmpty()}")

        // Actualizar la interfaz para mostrar los cambios
        actualizarInterfaz()
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
            Toast.makeText(context, getString(R.string.already_in_today), Toast.LENGTH_SHORT).show()
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

        updateBottomNavigationColors(view, "today")
    }

    private fun updateBottomNavigationColors(view: View, activeTab: String) {
        val bottomNav = view.findViewById<LinearLayout>(R.id.bottom_navigation)

        val homeLayout = bottomNav.getChildAt(0) as? LinearLayout
        val homeIcon = homeLayout?.getChildAt(0) as? ImageView
        val homeText = homeLayout?.getChildAt(1) as? TextView

        val todayLayout = bottomNav.getChildAt(1) as? LinearLayout
        val todayIcon = todayLayout?.getChildAt(0) as? ImageView
        val todayText = todayLayout?.getChildAt(1) as? TextView

        val profileLayout = bottomNav.getChildAt(2) as? LinearLayout
        val profileIcon = profileLayout?.getChildAt(0) as? ImageView
        val profileText = profileLayout?.getChildAt(1) as? TextView

        val activeColor = ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
        val inactiveColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)

        when (activeTab) {
            "home" -> {
                homeIcon?.setColorFilter(activeColor)
                homeText?.setTextColor(activeColor)
                todayIcon?.setColorFilter(inactiveColor)
                todayText?.setTextColor(inactiveColor)
                profileIcon?.setColorFilter(inactiveColor)
                profileText?.setTextColor(inactiveColor)
            }
            "today" -> {
                homeIcon?.setColorFilter(inactiveColor)
                homeText?.setTextColor(inactiveColor)
                todayIcon?.setColorFilter(activeColor)
                todayText?.setTextColor(activeColor)
                profileIcon?.setColorFilter(inactiveColor)
                profileText?.setTextColor(inactiveColor)
            }
            "profile" -> {
                homeIcon?.setColorFilter(inactiveColor)
                homeText?.setTextColor(inactiveColor)
                todayIcon?.setColorFilter(inactiveColor)
                todayText?.setTextColor(inactiveColor)
                profileIcon?.setColorFilter(activeColor)
                profileText?.setTextColor(inactiveColor)
            }
        }
    }
}