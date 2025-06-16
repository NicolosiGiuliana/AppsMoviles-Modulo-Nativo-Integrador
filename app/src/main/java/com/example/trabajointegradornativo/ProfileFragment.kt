package com.example.trabajointegradornativo

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
        private const val PICK_IMAGE_REQUEST = 102
        private const val CAMERA_REQUEST = 103
        private const val TAG = "ProfileFragment"
        private const val MAX_IMAGE_SIZE = 1024 // Tamaño máximo en píxeles
        private const val JPEG_QUALITY = 80 // Calidad de compresión JPEG
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var sharedPreferences: SharedPreferences

    // Views
    private lateinit var profileImage: ImageView
    private lateinit var profileInitials: TextView
    private lateinit var profileName: TextView
    private lateinit var profileEmail: TextView
    private lateinit var profileMember: TextView
    private lateinit var cameraIcon: ImageView
    private lateinit var dailyNotificationsSwitch: SwitchCompat
    private lateinit var motivationalPhrasesSwitch: SwitchCompat
    private lateinit var notificationTimeLayout: LinearLayout
    private lateinit var notificationTimeText: TextView
    private lateinit var languageLayout: LinearLayout
    private lateinit var selectedLanguageText: TextView
    private lateinit var exportDataLayout: LinearLayout
    private lateinit var logoutLayout: LinearLayout

    // Data
    private val languages = arrayOf("Español", "English", "Português")
    private val languageCodes = arrayOf("es", "en", "pt")
    private var selectedLanguageIndex = 0
    private var selectedNotificationHour = 9
    private var selectedNotificationMinute = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.profile_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initFirebase()
        initViews(view)
        loadUserData()
        setupClickListeners()
        setupSwitches()
        setupBottomNavigation()
        loadSettings()
        loadProfileImage()

    }

    private fun initFirebase() {
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        Log.d(TAG, "Firebase inicializado - Usuario: ${auth.currentUser?.uid}")
    }

    private fun initViews(view: View) {
        profileImage = view.findViewById(R.id.profileImage)
        profileInitials = view.findViewById(R.id.profileInitials)
        profileName = view.findViewById(R.id.profileName)
        profileEmail = view.findViewById(R.id.profileEmail)
        profileMember = view.findViewById(R.id.profileMember)
        cameraIcon = view.findViewById(R.id.cameraIcon)
        dailyNotificationsSwitch = view.findViewById(R.id.dailyNotificationsSwitch)
        motivationalPhrasesSwitch = view.findViewById(R.id.motivationalPhrasesSwitch)
        notificationTimeLayout = view.findViewById(R.id.notificationTimeLayout)
        notificationTimeText = view.findViewById(R.id.notificationTimeText)
        languageLayout = view.findViewById(R.id.languageLayout)
        selectedLanguageText = view.findViewById(R.id.selectedLanguageText)
        logoutLayout = view.findViewById(R.id.logoutLayout)
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Cargar datos del usuario
            profileEmail.text = currentUser.email
            profileName.text = currentUser.displayName ?: "Usuario"

            // Generar iniciales
            val name = currentUser.displayName ?: "Usuario"
            val initials = getInitials(name)
            profileInitials.text = initials

            // Fecha de registro
            val memberSince = sharedPreferences.getString("member_since", "")
            if (memberSince.isNullOrEmpty()) {
                val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                profileMember.text = "Miembro desde $currentDate"
                sharedPreferences.edit().putString("member_since", currentDate).apply()
            } else {
                profileMember.text = "Miembro desde $memberSince"
            }
        }
    }

    private fun getInitials(name: String): String {
        val words = name.trim().split(" ")
        return when {
            words.size >= 2 -> "${words[0].first().uppercase()}${words[1].first().uppercase()}"
            words.size == 1 && words[0].length >= 2 -> "${words[0].first().uppercase()}${words[0][1].uppercase()}"
            words.size == 1 -> words[0].first().uppercase()
            else -> "U"
        }
    }

    private fun loadSettings() {
        // Cargar configuración de notificaciones
        val notificationsEnabled = sharedPreferences.getBoolean("daily_notifications", true)
        dailyNotificationsSwitch.isChecked = notificationsEnabled

        val motivationalEnabled = sharedPreferences.getBoolean("motivational_phrases", true)
        motivationalPhrasesSwitch.isChecked = motivationalEnabled

        // Cargar horario de notificaciones
        selectedNotificationHour = sharedPreferences.getInt("notification_hour", 9)
        selectedNotificationMinute = sharedPreferences.getInt("notification_minute", 0)
        updateNotificationTimeDisplay()

        // Cargar idioma seleccionado
        val savedLanguage = sharedPreferences.getString("selected_language", "es")
        selectedLanguageIndex = languageCodes.indexOf(savedLanguage)
        if (selectedLanguageIndex == -1) selectedLanguageIndex = 0
        selectedLanguageText.text = languages[selectedLanguageIndex]
    }

    private fun updateNotificationTimeDisplay() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedNotificationHour)
        calendar.set(Calendar.MINUTE, selectedNotificationMinute)
        notificationTimeText.text = timeFormat.format(calendar.time)
    }

    private fun setupClickListeners() {
        // Cambiar foto de perfil
        cameraIcon.setOnClickListener { showImagePickerDialog() }
        profileImage.setOnClickListener { showImagePickerDialog() }

        // Horario de notificaciones
        notificationTimeLayout.setOnClickListener { showTimePickerDialog() }

        // Selector de idioma
        languageLayout.setOnClickListener { showLanguageDialog() }

        // Cerrar sesión
        logoutLayout.setOnClickListener { showLogoutDialog() }
    }

    private fun setupSwitches() {
        // Switch de notificaciones diarias
        dailyNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Verificar permisos antes de habilitar notificaciones
                checkNotificationPermissions { permissionsGranted ->
                    if (permissionsGranted) {
                        sharedPreferences.edit().putBoolean("daily_notifications", true).apply()
                        scheduleNotifications()
                        Toast.makeText(requireContext(), "Notificaciones habilitadas", Toast.LENGTH_SHORT).show()
                    } else {
                        dailyNotificationsSwitch.isChecked = false
                        Toast.makeText(requireContext(), "Se necesitan permisos para las notificaciones", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                sharedPreferences.edit().putBoolean("daily_notifications", false).apply()
                Toast.makeText(requireContext(), "Notificaciones deshabilitadas", Toast.LENGTH_SHORT).show()
            }
        }

        // Switch de frases motivacionales
        motivationalPhrasesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("motivational_phrases", isChecked).apply()
            if (dailyNotificationsSwitch.isChecked) {
                scheduleNotifications()
            }
        }
    }

    private fun checkNotificationPermissions(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requiere permiso POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        // Verificar permisos de alarma exacta para Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showExactAlarmPermissionDialog()
                callback(false)
                return
            }
        }

        callback(true)
    }

    private fun showNotificationSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permisos de notificación")
            .setMessage("Para recibir recordatorios, necesitas habilitar las notificaciones en la configuración de la aplicación.")
            .setPositiveButton("Ir a configuración") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showExactAlarmPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permisos de alarma")
            .setMessage("Para programar recordatorios precisos, necesitas habilitar los permisos de alarma en la configuración.")
            .setPositiveButton("Ir a configuración") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupBottomNavigation() {
        // Implementar navegación si es necesario
    }

    private fun loadProfileImage() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "Cargando imagen de perfil para usuario: ${currentUser.uid}")
            val imageRef = storage.reference.child("profile_images/${currentUser.uid}.jpg")

            imageRef.downloadUrl.addOnSuccessListener { uri ->
                Log.d(TAG, "URL de imagen obtenida: $uri")
                loadImageFromUri(uri)
            }.addOnFailureListener { exception ->
                Log.w(TAG, "No se encontró imagen de perfil: ${exception.message}")
                setDefaultProfileImage()
            }
        } else {
            Log.w(TAG, "Usuario no autenticado")
            setDefaultProfileImage()
        }
    }

    private fun setDefaultProfileImage() {
        try {
            val defaultDrawable = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_camera)
            val currentUser = auth.currentUser
            val userName = currentUser?.displayName ?: "Usuario"
            val initials = getInitials(userName)

            profileImage.setImageDrawable(defaultDrawable)
            profileImage.visibility = View.VISIBLE
            profileInitials.text = initials
            profileInitials.visibility = View.VISIBLE
            profileInitials.setBackgroundResource(android.R.drawable.button_onoff_indicator_on)

            Log.d(TAG, "Imagen por defecto establecida para usuario: $userName")

        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer imagen por defecto", e)
            profileImage.visibility = View.GONE
            profileInitials.visibility = View.VISIBLE
        }
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            profileImage.setImageBitmap(bitmap)
            profileImage.visibility = View.VISIBLE
            profileInitials.visibility = View.GONE
            Log.d(TAG, "Imagen de perfil cargada desde Firebase Storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profile image", e)
            setDefaultProfileImage()
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Tomar foto", "Seleccionar de galería", "Usar imagen por defecto", "Cancelar")

        AlertDialog.Builder(requireContext())
            .setTitle("Cambiar foto de perfil")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndTakePhoto()
                    1 -> checkGalleryPermissionAndOpenGallery()
                    2 -> {
                        setDefaultProfileImage()
                        Toast.makeText(requireContext(), "Imagen por defecto establecida", Toast.LENGTH_SHORT).show()
                    }
                    3 -> { /* Cancelar */ }
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndTakePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_REQUEST
            )
        } else {
            takePhoto()
        }
    }

    private fun checkGalleryPermissionAndOpenGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(permission), PERMISSION_REQUEST_CODE)
        } else {
            openGallery()
        }
    }

    private fun takePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA_REQUEST)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(requireContext(), "Permiso denegado", Toast.LENGTH_SHORT).show()
                }
            }
            CAMERA_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                } else {
                    Toast.makeText(requireContext(), "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sharedPreferences.edit().putBoolean("daily_notifications", true).apply()
                    scheduleNotifications()
                    Toast.makeText(requireContext(), "Permisos de notificación concedidos", Toast.LENGTH_SHORT).show()
                } else {
                    dailyNotificationsSwitch.isChecked = false
                    Toast.makeText(requireContext(), "Permisos de notificación denegados", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    data?.data?.let { uri ->
                        handleSelectedImage(uri)
                    }
                }
                CAMERA_REQUEST -> {
                    val bitmap = data?.extras?.get("data") as? Bitmap
                    bitmap?.let {
                        handleCapturedImage(it)
                    }
                }
            }
        }
    }

    private fun handleSelectedImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
            uploadProfileImage(resizedBitmap)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Error al cargar imagen desde galería", e)
            Toast.makeText(requireContext(), "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCapturedImage(bitmap: Bitmap) {
        val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
        uploadProfileImage(resizedBitmap)
    }

    // NUEVO: Función para redimensionar imagen
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

    private fun uploadProfileImage(bitmap: Bitmap) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "Usuario no autenticado")
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Iniciando subida de imagen para usuario: ${currentUser.uid}")

        // Mostrar indicador de carga
        Toast.makeText(requireContext(), "Subiendo imagen...", Toast.LENGTH_SHORT).show()

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        val data = baos.toByteArray()

        Log.d(TAG, "Tamaño de imagen comprimida: ${data.size} bytes")

        val imageRef = storage.reference.child("profile_images/${currentUser.uid}.jpg")

        // Agregar metadata
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        imageRef.putBytes(data, metadata)
            .addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                Log.d(TAG, "Progreso de subida: $progress%")
            }
            .addOnSuccessListener { taskSnapshot ->
                Log.d(TAG, "Imagen subida exitosamente")
                profileImage.setImageBitmap(bitmap)
                profileImage.visibility = View.VISIBLE
                profileInitials.visibility = View.GONE
                Toast.makeText(requireContext(), "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()

                // Guardar referencia en Firestore (opcional)
                saveImageReferenceToFirestore(currentUser.uid)
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
                showUploadErrorDialog(bitmap)
            }
    }

    // NUEVO: Guardar referencia en Firestore
    private fun saveImageReferenceToFirestore(userId: String) {
        val userRef = firestore.collection("usuarios").document(userId)
        val imageData = mapOf(
            "profileImageUrl" to "profile_images/$userId.jpg",
            "lastUpdated" to com.google.firebase.Timestamp.now()
        )

        userRef.update(imageData)
            .addOnSuccessListener {
                Log.d(TAG, "Referencia de imagen guardada en Firestore")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error al guardar referencia en Firestore", e)
            }
    }

    // NUEVO: Diálogo de error con opciones
    private fun showUploadErrorDialog(bitmap: Bitmap) {
        AlertDialog.Builder(requireContext())
            .setTitle("Error al subir imagen")
            .setMessage("No se pudo subir la imagen. ¿Qué deseas hacer?")
            .setPositiveButton("Reintentar") { _, _ ->
                uploadProfileImage(bitmap)
            }
            .setNegativeButton("Usar localmente") { _, _ ->
                // Usar la imagen localmente sin subirla
                profileImage.setImageBitmap(bitmap)
                profileImage.visibility = View.VISIBLE
                profileInitials.visibility = View.GONE
                Toast.makeText(requireContext(), "Imagen establecida localmente", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun showTimePickerDialog() {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                selectedNotificationHour = hourOfDay
                selectedNotificationMinute = minute
                updateNotificationTimeDisplay()
                saveNotificationTime()
                if (dailyNotificationsSwitch.isChecked) {
                    scheduleNotifications()
                }
            },
            selectedNotificationHour,
            selectedNotificationMinute,
            true
        ).show()
    }

    private fun saveNotificationTime() {
        sharedPreferences.edit()
            .putInt("notification_hour", selectedNotificationHour)
            .putInt("notification_minute", selectedNotificationMinute)
            .apply()
    }

    private fun scheduleNotifications() {
        val context = requireContext()
        val hour = selectedNotificationHour
        val minute = selectedNotificationMinute
        val includeMotivational = motivationalPhrasesSwitch.isChecked

        Log.d(TAG, "Notificaciones programadas para $hour:$minute")
    }

    private fun showLanguageDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Seleccionar idioma")
            .setSingleChoiceItems(languages, selectedLanguageIndex) { dialog, which ->
                selectedLanguageIndex = which
                selectedLanguageText.text = languages[selectedLanguageIndex]
                saveLanguagePreference()
                dialog.dismiss()

                Toast.makeText(
                    requireContext(),
                    "Idioma cambiado a ${languages[selectedLanguageIndex]}",
                    Toast.LENGTH_SHORT
                ).show()

                changeAppLanguage(languageCodes[selectedLanguageIndex])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveLanguagePreference() {
        sharedPreferences.edit()
            .putString("selected_language", languageCodes[selectedLanguageIndex])
            .apply()
    }

    private fun changeAppLanguage(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = requireActivity().resources.configuration
        config.setLocale(locale)
        requireActivity().resources.updateConfiguration(config, requireActivity().resources.displayMetrics)

        requireActivity().recreate()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cerrar sesión")
            .setMessage("¿Estás seguro de que deseas cerrar sesión?")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun performLogout() {
        sharedPreferences.edit().clear().apply()
        auth.signOut()
        findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
        Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()
    }
}
