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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
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
    val diaActual: Int,
    val totalDias: Int,
    val habitos: MutableList<Habito>
)

class TodayFragment : Fragment() {

    companion object {
        private const val TAG = "TodayFragment"
        private const val MAX_IMAGE_SIZE = 1024 // Tamaño máximo en píxeles
        private const val JPEG_QUALITY = 80 // Calidad de compresión JPEG
    }

    private lateinit var dateTextView: TextView
    private lateinit var progressTextView: TextView
    private lateinit var activitiesContainer: LinearLayout

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    private var desafiosActivos = mutableListOf<Desafio>()
    private var habitoSeleccionadoParaFoto: Pair<String, String>? = null

    // Listeners para detectar cambios en tiempo real
    private val firestoreListeners = mutableListOf<ListenerRegistration>()

    // Launcher para tomar fotos con cámara - ACTUALIZADO
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

    // Launcher para seleccionar foto de galería - ACTUALIZADO
    private val pickImageLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedImage(uri)
            }
        }
    }

    // Launcher para permisos de cámara
    private val requestCameraPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            abrirCamara()
        } else {
            Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para permisos de almacenamiento - ACTUALIZADO
    private val requestStoragePermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            abrirGaleria()
        } else {
            Toast.makeText(context, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
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

        return view
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Recargando datos")
        cargarDesafiosDelUsuario()
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
        val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
        val fechaFormateada = dateFormat.format(calendar.time)
        dateTextView.text = fechaFormateada.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    private fun cargarDesafiosDelUsuario() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        limpiarListeners()

        val desafiosListener = firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .whereEqualTo("estado", "activo")
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    Log.e(TAG, "Error al escuchar desafíos: ${error.message}")
                    return@addSnapshotListener
                }

                if (documents != null) {
                    desafiosActivos.clear()

                    if (documents.isEmpty) {
                        mostrarMensajeSinDesafios()
                        return@addSnapshotListener
                    }

                    for (document in documents) {
                        val desafioId = document.id
                        val nombre = document.getString("nombre") ?: "Desafío sin nombre"
                        val diaActual = document.getLong("diaActual")?.toInt() ?: 1
                        val totalDias = document.getLong("dias")?.toInt() ?: 30

                        cargarHabitosDelDiaConListener(desafioId, nombre, diaActual, totalDias)
                    }
                }
            }

        firestoreListeners.add(desafiosListener)
    }

    private fun cargarHabitosDelDiaConListener(desafioId: String, nombreDesafio: String, diaActual: Int, totalDias: Int) {
        val uid = auth.currentUser?.uid ?: return

        val diaCompletadoListener = firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafioId)
            .collection("dias_completados")
            .whereEqualTo("dia", diaActual)
            .addSnapshotListener { completadosSnapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error al verificar día completado: ${error.message}")
                    return@addSnapshotListener
                }

                val diaEstaCompletado = completadosSnapshot != null && !completadosSnapshot.isEmpty

                val habitosListener = firestore.collection("usuarios")
                    .document(uid)
                    .collection("desafios")
                    .document(desafioId)
                    .collection("dias")
                    .document("dia_$diaActual")
                    .addSnapshotListener { document, habitosError ->
                        if (habitosError != null) {
                            Log.e(TAG, "Error al cargar hábitos: ${habitosError.message}")
                            return@addSnapshotListener
                        }

                        if (document != null && document.exists()) {
                            val habitosData = document.get("habitos") as? List<Map<String, Any>> ?: emptyList()
                            val habitos = habitosData.map { habitoMap: Map<String, Any> ->
                                val habitoCompletado = if (diaEstaCompletado) {
                                    true
                                } else {
                                    habitoMap["completado"] as? Boolean ?: false
                                }

                                Habito(
                                    nombre = habitoMap["nombre"] as? String ?: "",
                                    completado = habitoCompletado,
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

                            Log.d(TAG, "Desafío actualizado: $nombreDesafio, día completado: $diaEstaCompletado")
                            actualizarInterfaz()
                        }
                    }

                firestoreListeners.add(habitosListener)
            }

        firestoreListeners.add(diaCompletadoListener)
    }

    private fun mostrarMensajeSinDesafios() {
        activitiesContainer.removeAllViews()

        val mensajeLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 64, 32, 64)
        }

        val textoMensaje = TextView(requireContext()).apply {
            text = "No tienes desafíos activos.\n¡Ve a Home para crear uno!"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        }

        mensajeLayout.addView(textoMensaje)
        activitiesContainer.addView(mensajeLayout)

        progressTextView.text = "0/0 completados"
    }

    private fun actualizarInterfaz() {
        activitiesContainer.removeAllViews()

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
        }

        val cardLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val tituloDesafio = TextView(requireContext()).apply {
            text = "${desafio.nombre} - Día ${desafio.diaActual}"
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
                        Toast.makeText(context, "Este día ya está marcado como completado", Toast.LENGTH_SHORT).show()
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

    // NUEVA FUNCIÓN: Mostrar diálogo de opciones de foto mejorado
    private fun mostrarOpcionesFoto() {
        val opciones = arrayOf("Tomar foto", "Seleccionar de galería", "Cancelar")

        AlertDialog.Builder(requireContext())
            .setTitle("Agregar foto al hábito")
            .setItems(opciones) { dialog, which ->
                when (which) {
                    0 -> verificarPermisosCamara() // Tomar foto
                    1 -> verificarPermisosGaleria() // Seleccionar de galería
                    2 -> dialog.dismiss() // Cancelar
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

    private fun crearContenidoExtra(habito: Habito): LinearLayout {
        val extraLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 24, 0, 0)
        }

        if (habito.fotoUrl != null) {
            val fotoPlaceholder = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(160, 160).apply {
                    bottomMargin = 24
                }
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                gravity = android.view.Gravity.CENTER
            }

            val textoFoto = TextView(requireContext()).apply {
                text = "Foto\nadjunta"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                gravity = android.view.Gravity.CENTER
            }

            fotoPlaceholder.addView(textoFoto)
            extraLayout.addView(fotoPlaceholder)
        }

        if (habito.comentario != null) {
            val comentarioLayout = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_light))
                setPadding(24, 24, 24, 24)
            }

            val textoComentario = TextView(requireContext()).apply {
                text = habito.comentario
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }

            comentarioLayout.addView(textoComentario)
            extraLayout.addView(comentarioLayout)
        }

        return extraLayout
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
                Toast.makeText(context, "Error al guardar progreso: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarResumenProgreso() {
        val totalHabitos = desafiosActivos.sumOf { it.habitos.size }
        val habitosCompletados = desafiosActivos.sumOf { desafio ->
            desafio.habitos.count { it.completado }
        }

        progressTextView.text = "$habitosCompletados/$totalHabitos completados"
    }

    // FUNCIONES MEJORADAS DE MANEJO DE IMÁGENES (del ProfileFragment)

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
        // Usar el mismo enfoque que ProfileFragment - más directo
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            takePictureLauncher.launch(takePictureIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir cámara: ${e.message}")
            Toast.makeText(context, "Error al acceder a la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    // NUEVA FUNCIÓN: Manejar imagen seleccionada de galería
    private fun handleSelectedImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
                mostrarDialogoComentario(resizedBitmap)
            } else {
                Toast.makeText(requireContext(), "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Error al cargar imagen desde galería", e)
            Toast.makeText(requireContext(), "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al procesar imagen", e)
            Toast.makeText(requireContext(), "Error al procesar la imagen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // NUEVA FUNCIÓN: Redimensionar imagen (del ProfileFragment)
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
            hint = "Agrega un comentario (opcional)"
            maxLines = 3
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Agregar comentario")
            .setMessage("Puedes agregar un comentario opcional para esta foto")
            .setView(editText)
            .setPositiveButton("Guardar") { _, _ ->
                val comentario = editText.text.toString().trim()
                subirFotoYGuardarComentario(bitmap, comentario)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // FUNCIÓN MEJORADA: Subir foto con mejor manejo de errores
    private fun subirFotoYGuardarComentario(bitmap: Bitmap, comentario: String) {
        val habitoInfo = habitoSeleccionadoParaFoto ?: return
        val (desafioId, habitoNombre) = habitoInfo

        val uid = auth.currentUser?.uid ?: return

        Log.d(TAG, "Iniciando subida de imagen para hábito: $habitoNombre")

        // Mostrar indicador de carga
        Toast.makeText(requireContext(), "Subiendo imagen...", Toast.LENGTH_SHORT).show()

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        val data: ByteArray = baos.toByteArray()

        Log.d(TAG, "Tamaño de imagen comprimida: ${data.size} bytes")

        val timestamp = System.currentTimeMillis()
        val storageRef = storage.reference
            .child("usuarios/$uid/fotos_habitos/${desafioId}_${habitoNombre}_$timestamp.jpg")

        // Agregar metadata
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        storageRef.putBytes(data, metadata)
            .addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                Log.d(TAG, "Progreso de subida: $progress%")
            }
            .addOnSuccessListener { taskSnapshot ->
                Log.d(TAG, "Imagen subida exitosamente")
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    actualizarHabitoConFotoYComentario(desafioId, habitoNombre, uri.toString(), comentario)
                    Toast.makeText(requireContext(), "Foto guardada exitosamente", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Error al obtener URL de descarga", e)
                    Toast.makeText(requireContext(), "Error al obtener URL de la imagen", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al subir imagen", exception)

                val errorMessage = when (exception) {
                    is StorageException -> {
                        when (exception.errorCode) {
                            StorageException.ERROR_OBJECT_NOT_FOUND -> "Archivo no encontrado"
                            StorageException.ERROR_BUCKET_NOT_FOUND -> "Bucket de almacenamiento no encontrado"
                            StorageException.ERROR_PROJECT_NOT_FOUND -> "Proyecto no encontrado"
                            StorageException.ERROR_QUOTA_EXCEEDED -> "Cuota de almacenamiento excedida"
                            StorageException.ERROR_NOT_AUTHENTICATED -> "Usuario no autenticado"
                            StorageException.ERROR_NOT_AUTHORIZED -> "Sin permisos para subir archivos"
                            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "Límite de reintentos excedido"
                            StorageException.ERROR_INVALID_CHECKSUM -> "Checksum inválido"
                            StorageException.ERROR_CANCELED -> "Operación cancelada"
                            else -> "Error de almacenamiento: ${exception.message}"
                        }
                    }
                    else -> "Error desconocido: ${exception.message}"
                }

                Toast.makeText(requireContext(), "Error al subir imagen: $errorMessage", Toast.LENGTH_LONG).show()

                // Mostrar diálogo con opciones
                mostrarDialogoErrorSubida(bitmap, comentario)
            }
    }

    // NUEVA FUNCIÓN: Diálogo de error con opciones
    private fun mostrarDialogoErrorSubida(bitmap: Bitmap, comentario: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Error al subir imagen")
            .setMessage("No se pudo subir la imagen. ¿Qué deseas hacer?")
            .setPositiveButton("Reintentar") { _, _ ->
                subirFotoYGuardarComentario(bitmap, comentario)
            }
            .setNegativeButton("Guardar localmente") { _, _ ->
                // Guardar solo el comentario sin la imagen
                val habitoInfo = habitoSeleccionadoParaFoto ?: return@setNegativeButton
                val (desafioId, habitoNombre) = habitoInfo
                if (comentario.isNotEmpty()) {
                    actualizarHabitoConFotoYComentario(desafioId, habitoNombre, null, comentario)
                    Toast.makeText(requireContext(), "Comentario guardado (sin imagen)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun actualizarHabitoConFotoYComentario(desafioId: String, habitoNombre: String, fotoUrl: String?, comentario: String) {
        val desafio = desafiosActivos.find { it.id == desafioId } ?: return
        val habito = desafio.habitos.find { it.nombre == habitoNombre } ?: return

        // Actualizar los datos del hábito
        if (fotoUrl != null) {
            habito.fotoUrl = fotoUrl
        }
        if (comentario.isNotEmpty()) {
            habito.comentario = comentario
        }

        // Guardar en Firestore
        guardarProgresoHabito(desafio, habito)

        Log.d(TAG, "Hábito actualizado: $habitoNombre con foto: ${fotoUrl != null}, comentario: ${comentario.isNotEmpty()}")
    }

    private fun setupBottomNavigation(view: View) {
        val bottomNav = view.findViewById<LinearLayout>(R.id.bottom_navigation)

        val homeLayout = bottomNav.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.itemListFragment)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al navegar a Home", Toast.LENGTH_SHORT).show()
            }
        }

        val todayLayout = bottomNav.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            Toast.makeText(context, "Ya estás en Hoy", Toast.LENGTH_SHORT).show()
        }

        val profileLayout = bottomNav.getChildAt(2) as? LinearLayout
        profileLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.profileFragment)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al navegar a Perfil", Toast.LENGTH_SHORT).show()
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