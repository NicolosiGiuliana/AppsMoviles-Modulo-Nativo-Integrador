package com.example.trabajointegradornativo

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
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
        private const val MAX_IMAGE_SIZE = 1024
        private const val JPEG_QUALITY = 80
    }

    private lateinit var imgBBUploader: ImgBBUploader
    private var uploadProgressDialog: AlertDialog? = null

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
    private lateinit var logoutLayout: LinearLayout

    // Data - Ahora usando arrays de strings
    private lateinit var languages: Array<String>
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

        initLanguageArray()
        initFirebase()
        initViews(view)
        loadUserData()
        setupClickListeners()
        setupSwitches()
        setupBottomNavigation()
        loadSettings()
        loadProfileImage()
    }

    private fun initLanguageArray() {
        languages = arrayOf(
            getString(R.string.language_spanish),
            getString(R.string.language_english),
            getString(R.string.language_portuguese)
        )
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = view?.findViewById<LinearLayout>(R.id.bottom_navigation)

        // Home (índice 0) - Navega a itemListFragment
        val homeLayout = bottomNavigation?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_profileFragment_to_itemListFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Today (índice 1)
        val todayLayout = bottomNavigation?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_profileFragment_to_todayFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Explorar (índice 2)
        val exploreLayout = bottomNavigation?.getChildAt(2) as? LinearLayout
        exploreLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_profileFragment_to_publicChallengeFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Profile (índice 3)
        val profileLayout = bottomNavigation?.getChildAt(3) as? LinearLayout
        profileLayout?.setOnClickListener {
            // Ya estamos en Profile
        }
    }

    private fun initFirebase() {
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        imgBBUploader = ImgBBUploader()
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
            profileEmail.text = currentUser.email
            profileName.text = currentUser.displayName ?: getString(R.string.default_name)

            val name = currentUser.displayName ?: getString(R.string.default_name)
            val initials = getInitials(name)
            profileInitials.text = initials

            val memberSince = sharedPreferences.getString("member_since", "")
            if (memberSince.isNullOrEmpty()) {
                val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                profileMember.text = getString(R.string.member_since) + " " + currentDate
                sharedPreferences.edit().putString("member_since", currentDate).apply()
            } else {
                profileMember.text = getString(R.string.member_since) + " " + memberSince
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
        val notificationsEnabled = sharedPreferences.getBoolean("daily_notifications", true)
        dailyNotificationsSwitch.isChecked = notificationsEnabled

        val motivationalEnabled = sharedPreferences.getBoolean("motivational_phrases", true)
        motivationalPhrasesSwitch.isChecked = motivationalEnabled

        selectedNotificationHour = sharedPreferences.getInt("notification_hour", 9)
        selectedNotificationMinute = sharedPreferences.getInt("notification_minute", 0)
        updateNotificationTimeDisplay()

        // Detectar el idioma actual correctamente
        selectedLanguageIndex = getCurrentLanguageIndex()

        // Asegurar que el índice esté dentro del rango válido
        if (selectedLanguageIndex < 0 || selectedLanguageIndex >= languages.size) {
            selectedLanguageIndex = 0
        }

        selectedLanguageText.text = languages[selectedLanguageIndex]

        Log.d(TAG, "Configuración de idioma cargada - Índice: $selectedLanguageIndex, Texto: ${languages[selectedLanguageIndex]}")
    }

    private fun getCurrentLanguageIndex(): Int {
        // Obtener el idioma actual del sistema/configuración
        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale.language
        }

        Log.d(TAG, "Idioma actual detectado: $currentLocale")

        // Mapear el idioma actual al índice correcto
        val index = when (currentLocale) {
            "es" -> 0  // Español
            "en" -> 1  // English
            "pt" -> 2  // Português
            else -> {
                // Si no reconoce el idioma, usar el guardado en preferencias
                val savedLanguage = LanguageHelper.getAppLanguage(requireContext())
                Log.d(TAG, "Idioma guardado en preferencias: $savedLanguage")
                languageCodes.indexOf(savedLanguage).takeIf { it >= 0 } ?: 0
            }
        }

        Log.d(TAG, "Índice de idioma seleccionado: $index (${languages[index]})")
        return index
    }

    private fun updateNotificationTimeDisplay() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedNotificationHour)
        calendar.set(Calendar.MINUTE, selectedNotificationMinute)
        notificationTimeText.text = timeFormat.format(calendar.time)
    }

    private fun setupClickListeners() {
        cameraIcon.setOnClickListener { showImagePickerDialog() }
        profileImage.setOnClickListener { showImagePickerDialog() }
        notificationTimeLayout.setOnClickListener { showTimePickerDialog() }
        languageLayout.setOnClickListener { showLanguageDialog() }
        logoutLayout.setOnClickListener { showLogoutDialog() }
    }

    private fun setupSwitches() {
        dailyNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkNotificationPermissions { permissionsGranted ->
                    if (permissionsGranted) {
                        sharedPreferences.edit().putBoolean("daily_notifications", true).apply()
                        scheduleNotifications()
                        Toast.makeText(requireContext(), getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show()
                    } else {
                        dailyNotificationsSwitch.isChecked = false
                        Toast.makeText(requireContext(), getString(R.string.notification_permissions_needed), Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                sharedPreferences.edit().putBoolean("daily_notifications", false).apply()
                Toast.makeText(requireContext(), getString(R.string.notifications_disabled), Toast.LENGTH_SHORT).show()
            }
        }

        motivationalPhrasesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("motivational_phrases", isChecked).apply()
            if (dailyNotificationsSwitch.isChecked) {
                scheduleNotifications()
            }
        }
    }

    private fun checkNotificationPermissions(callback: (Boolean) -> Unit) {
        var permissionsNeeded = mutableListOf<String>()

        // Verificar permiso de notificaciones para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Si hay permisos pendientes, solicitarlos
        if (permissionsNeeded.isNotEmpty()) {
            requestPermissions(
                permissionsNeeded.toTypedArray(),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showExactAlarmPermissionDialog()
                // Aún podemos continuar con alarmas inexactas
                callback(true)
                return
            }
        }

        callback(true)
    }

    private fun showNotificationSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.notification_permissions_title))
            .setMessage(getString(R.string.notification_permissions_message))
            .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showExactAlarmPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.alarm_permissions_title))
            .setMessage(getString(R.string.alarm_permissions_message))
            .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadProfileImage() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "Cargando imagen de perfil para usuario: ${currentUser.uid}")

            // Primero verificar si el usuario eligió usar imagen por defecto
            val useDefaultImage = sharedPreferences.getBoolean("use_default_image", false)

            if (useDefaultImage) {
                setDefaultProfileImage()
                return
            }

            // Cargar URL de imagen desde SharedPreferences o Firestore
            val savedImageUrl = sharedPreferences.getString("profile_image_url", null)

            if (savedImageUrl != null) {
                Log.d(TAG, "URL de imagen encontrada: $savedImageUrl")
                loadImageFromUrl(savedImageUrl)
            } else {
                // Intentar cargar desde Firestore
                loadImageUrlFromFirestore(currentUser.uid)
            }
        } else {
            Log.w(TAG, getString(R.string.user_not_authenticated))
            setDefaultProfileImage()
        }
    }

    // MODIFICAR: Función para cargar desde Firestore
    private fun loadImageUrlFromFirestore(userId: String) {
        firestore.collection("usuarios").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Verificar si el usuario eligió usar imagen por defecto
                    val useDefaultImage = document.getBoolean("useDefaultImage") ?: false

                    if (useDefaultImage) {
                        // Guardar preferencia localmente
                        sharedPreferences.edit().putBoolean("use_default_image", true).apply()
                        setDefaultProfileImage()
                        return@addOnSuccessListener
                    }

                    val imageUrl = document.getString("profileImageUrl")
                    if (imageUrl != null) {
                        Log.d(TAG, "URL de imagen encontrada en Firestore: $imageUrl")
                        loadImageFromUrl(imageUrl)
                        // Guardar en SharedPreferences para acceso rápido
                        sharedPreferences.edit()
                            .putString("profile_image_url", imageUrl)
                            .putBoolean("use_default_image", false)
                            .apply()
                    } else {
                        setDefaultProfileImage()
                    }
                } else {
                    setDefaultProfileImage()
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error al cargar datos del usuario", exception)
                setDefaultProfileImage()
            }
    }

    private fun loadImageFromUrl(imageUrl: String) {
        Thread {
            try {
                val url = java.net.URL(imageUrl)
                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())

                // Cambiar al hilo principal para actualizar la UI
                Handler(Looper.getMainLooper()).post {
                    profileImage.setImageBitmap(bitmap)
                    profileImage.visibility = View.VISIBLE
                    profileInitials.visibility = View.GONE
                    Log.d(TAG, "Imagen de perfil cargada desde URL")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar imagen desde URL", e)
                Handler(Looper.getMainLooper()).post {
                    setDefaultProfileImage()
                }
            }
        }.start()
    }

    private fun setDefaultProfileImage() {
        try {
            val currentUser = auth.currentUser
            val userName = currentUser?.displayName ?: getString(R.string.default_name)
            val initials = getInitials(userName)

            // Ocultar la imagen y mostrar las iniciales
            profileImage.visibility = View.GONE
            profileInitials.text = initials
            profileInitials.visibility = View.VISIBLE

            // Opcional: establecer un fondo circular para las iniciales si no está en el XML
            profileInitials.setBackgroundResource(R.drawable.profile_circle_bg)

            Log.d(TAG, "Imagen por defecto establecida para usuario: $userName con iniciales: $initials")

        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer imagen por defecto", e)
            // Como fallback, asegurar que al menos las iniciales sean visibles
            profileImage.visibility = View.GONE
            profileInitials.text = "U" // Usuario por defecto
            profileInitials.visibility = View.VISIBLE
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.select_from_gallery),
            getString(R.string.use_default_image),
            getString(R.string.cancel)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.change_profile_photo))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndTakePhoto()
                    1 -> checkGalleryPermissionAndOpenGallery()
                    2 -> {
                        // CAMBIO: Limpiar las URLs guardadas cuando se selecciona imagen por defecto
                        clearSavedImageUrls()
                        setDefaultProfileImage()
                        Toast.makeText(requireContext(), getString(R.string.default_image_set), Toast.LENGTH_SHORT).show()
                    }
                    3 -> { /* Cancelar */ }
                }
            }
            .show()
    }

    // NUEVA FUNCIÓN: Limpiar URLs guardadas
    private fun clearSavedImageUrls() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Limpiar SharedPreferences
            sharedPreferences.edit()
                .remove("profile_image_url")
                .remove("profile_image_delete_url")
                .apply()

            // Limpiar Firestore
            firestore.collection("usuarios").document(currentUser.uid)
                .update(
                    mapOf(
                        "profileImageUrl" to null,
                        "profileImageDeleteUrl" to null,
                        "useDefaultImage" to true, // Flag para indicar que usa imagen por defecto
                        "lastUpdated" to com.google.firebase.Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Log.d(TAG, "URLs de imagen limpiadas de Firestore")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error al limpiar URLs en Firestore", e)
                }
        }
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
                    Toast.makeText(requireContext(), getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
                }
            }
            CAMERA_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sharedPreferences.edit().putBoolean("daily_notifications", true).apply()
                    scheduleNotifications()
                    Toast.makeText(requireContext(), getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show()
                } else {
                    dailyNotificationsSwitch.isChecked = false
                    Toast.makeText(requireContext(), getString(R.string.notification_permissions_denied), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCapturedImage(bitmap: Bitmap) {
        val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
        uploadProfileImage(resizedBitmap)
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

    private fun uploadProfileImage(bitmap: Bitmap) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "Usuario no autenticado")
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Iniciando subida de imagen a ImgBB para usuario: ${currentUser.uid}")

        // Mostrar diálogo de progreso
        showUploadProgressDialog()

        imgBBUploader.uploadImage(bitmap, object : ImgBBUploader.UploadCallback {
            override fun onSuccess(imageUrl: String, deleteUrl: String) {
                // Cambiar al hilo principal
                Handler(Looper.getMainLooper()).post {
                    hideUploadProgressDialog()

                    // Actualizar la imagen en la UI
                    profileImage.setImageBitmap(bitmap)
                    profileImage.visibility = View.VISIBLE
                    profileInitials.visibility = View.GONE

                    // Guardar URL en SharedPreferences Y marcar que NO usa imagen por defecto
                    sharedPreferences.edit()
                        .putString("profile_image_url", imageUrl)
                        .putString("profile_image_delete_url", deleteUrl)
                        .putBoolean("use_default_image", false) // IMPORTANTE: Marcar que no usa imagen por defecto
                        .apply()

                    // Guardar en Firestore
                    saveImageUrlToFirestore(currentUser.uid, imageUrl, deleteUrl)

                    Toast.makeText(requireContext(), "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Imagen subida exitosamente a ImgBB: $imageUrl")
                }
            }

            override fun onError(error: String) {
                Handler(Looper.getMainLooper()).post {
                    hideUploadProgressDialog()
                    Log.e(TAG, "Error al subir imagen a ImgBB: $error")
                    Toast.makeText(requireContext(), "Error al subir imagen: $error", Toast.LENGTH_LONG).show()

                    // Mostrar diálogo de opciones de error
                    showUploadErrorDialog(bitmap)
                }
            }

            override fun onProgress(progress: Int) {
                Handler(Looper.getMainLooper()).post {
                    updateUploadProgress(progress)
                }
            }
        })
    }

    // MODIFICAR: Función para guardar en Firestore
    private fun saveImageUrlToFirestore(userId: String, imageUrl: String, deleteUrl: String) {
        val userRef = firestore.collection("usuarios").document(userId)
        val imageData = mapOf(
            "profileImageUrl" to imageUrl,
            "profileImageDeleteUrl" to deleteUrl,
            "useDefaultImage" to false, // IMPORTANTE: Marcar que no usa imagen por defecto
            "lastUpdated" to com.google.firebase.Timestamp.now()
        )

        userRef.update(imageData)
            .addOnSuccessListener {
                Log.d(TAG, "URL de imagen guardada en Firestore")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error al guardar URL en Firestore", e)
            }
    }

    private fun showUploadProgressDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(android.R.layout.simple_list_item_1, null)
        val textView = dialogView.findViewById<TextView>(android.R.id.text1)
        textView.text = "Subiendo imagen... 0%"

        uploadProgressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Subiendo imagen")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        uploadProgressDialog?.show()
    }

    private fun updateUploadProgress(progress: Int) {
        uploadProgressDialog?.let { dialog ->
            val textView = dialog.findViewById<TextView>(android.R.id.text1)
            textView?.text = "Subiendo imagen... $progress%"
        }
    }

    private fun hideUploadProgressDialog() {
        uploadProgressDialog?.dismiss()
        uploadProgressDialog = null
    }

    private fun showUploadErrorDialog(bitmap: Bitmap) {
        AlertDialog.Builder(requireContext())
            .setTitle("Error al subir imagen")
            .setMessage("No se pudo subir la imagen a ImgBB. ¿Qué deseas hacer?")
            .setPositiveButton("Reintentar") { _, _ ->
                uploadProfileImage(bitmap)
            }
            .setNegativeButton("Usar localmente") { _, _ ->
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

        Log.d(TAG, "Programando notificaciones para $hour:$minute")

        try {
            val notificationHelper = NotificationHelper(context)
            notificationHelper.programarNotificacionDiaria(hour, minute)
            Toast.makeText(context, getString(R.string.reminders_scheduled, hour, String.format("%02d", minute)), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error al programar notificaciones", e)
            Toast.makeText(context, getString(R.string.error_scheduling_reminders), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLanguageDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_language))
            .setSingleChoiceItems(languages, selectedLanguageIndex) { dialog, which ->
                selectedLanguageIndex = which
                selectedLanguageText.text = languages[selectedLanguageIndex]
                saveLanguagePreference()
                dialog.dismiss()

                val languageName = languages[selectedLanguageIndex]
                Toast.makeText(
                    requireContext(),
                    getString(R.string.language_changed_to, languageName),
                    Toast.LENGTH_SHORT
                ).show()

                changeAppLanguage(languageCodes[selectedLanguageIndex])
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveLanguagePreference() {
        LanguageHelper.setAppLanguage(requireContext(), languageCodes[selectedLanguageIndex])
    }

    private fun changeAppLanguage(languageCode: String) {
        LanguageHelper.setAppLanguage(requireContext(), languageCode)
        requireActivity().recreate()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logout_confirmation))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()

        with(sharedPreferences.edit()) {
            putString("user_email", "")
            putString("user_name", "")
            putBoolean("is_logged_in", false)
            apply()
        }

        val intent = Intent(requireActivity(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}