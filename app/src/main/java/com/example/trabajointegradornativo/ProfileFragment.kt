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
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.trabajointegradornativo.LanguageHelper
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
        private const val MAX_IMAGE_SIZE = 1024
        private const val JPEG_QUALITY = 80
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
        setupBottomNavigation(view)
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

    private fun setupBottomNavigation(view: View) {
        val bottomNav = view.findViewById<LinearLayout>(R.id.bottom_navigation)

        // Home
        val homeLayout = bottomNav?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.itemListFragment)
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.error_navigating_home, e.message), Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error navegando a Home", e)
            }
        }

        // Today
        val todayLayout = bottomNav?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.todayFragment)
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.error_navigating_today, e.message), Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error navegando a Today", e)
            }
        }

        // Profile (ya estamos aquí)
        val profileLayout = bottomNav?.getChildAt(2) as? LinearLayout
        profileLayout?.setOnClickListener {
            Toast.makeText(context, getString(R.string.already_in_profile), Toast.LENGTH_SHORT).show()
        }

        updateBottomNavigationColors(view, "profile")
    }

    private fun updateBottomNavigationColors(view: View, activeTab: String) {
        val bottomNav = view.findViewById<LinearLayout>(R.id.bottom_navigation)

        val homeLayout = bottomNav?.getChildAt(0) as? LinearLayout
        val homeIcon = homeLayout?.getChildAt(0) as? ImageView
        val homeText = homeLayout?.getChildAt(1) as? TextView

        val todayLayout = bottomNav?.getChildAt(1) as? LinearLayout
        val todayIcon = todayLayout?.getChildAt(0) as? ImageView
        val todayText = todayLayout?.getChildAt(1) as? TextView

        val profileLayout = bottomNav?.getChildAt(2) as? LinearLayout
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
                profileText?.setTextColor(activeColor)
            }
        }
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

        val savedLanguage = LanguageHelper.getAppLanguage(requireContext())
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
                        Toast.makeText(requireContext(), "Se necesitan permisos para las notificaciones", Toast.LENGTH_LONG).show()
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
            .setTitle("Permisos de notificación")
            .setMessage("Para recibir recordatorios, necesitas habilitar las notificaciones en la configuración de la aplicación.")
            .setPositiveButton("Ir a configuración") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel), null)
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
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
//        try {
//            val defaultDrawable = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_camera)
//            val currentUser = auth.currentUser
//            val userName = currentUser?.displayName ?: "Usuario"
//            val initials = getInitials(userName)
//
//            profileImage.setImageDrawable(defaultDrawable)
//            profileImage.visibility = View.VISIBLE
//            profileInitials.text = initials
//            profileInitials.visibility = View.VISIBLE
//            profileInitials.setBackgroundResource(android.R.drawable.button_onoff_indicator_on)
//
//            Log.d(TAG, "Imagen por defecto establecida para usuario: $userName")
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error al establecer imagen por defecto", e)
//            profileImage.visibility = View.GONE
//            profileInitials.visibility = View.VISIBLE
//        }
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
                    Toast.makeText(requireContext(), getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show()
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

        Toast.makeText(requireContext(), getString(R.string.uploading_image), Toast.LENGTH_SHORT).show()

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        val data = baos.toByteArray()

        Log.d(TAG, "Tamaño de imagen comprimida: ${data.size} bytes")

        val imageRef = storage.reference.child("profile_images/${currentUser.uid}.jpg")

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
                Toast.makeText(requireContext(), getString(R.string.profile_photo_updated), Toast.LENGTH_SHORT).show()

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

                Toast.makeText(requireContext(), getString(R.string.error_uploading_image) + ": $errorMessage", Toast.LENGTH_LONG).show()

                showUploadErrorDialog(bitmap)
            }
    }

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

    private fun showUploadErrorDialog(bitmap: Bitmap) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.error_uploading_image))
            .setMessage("No se pudo subir la imagen. ¿Qué deseas hacer?")
            .setPositiveButton("Reintentar") { _, _ ->
                uploadProfileImage(bitmap)
            }
            .setNegativeButton("Usar localmente") { _, _ ->
                profileImage.setImageBitmap(bitmap)
                profileImage.visibility = View.VISIBLE
                profileInitials.visibility = View.GONE
                Toast.makeText(requireContext(), "Imagen establecida localmente", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(getString(R.string.cancel), null)
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
            Toast.makeText(context, "Recordatorios programados para $hour:${String.format("%02d", minute)}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error al programar notificaciones", e)
            Toast.makeText(context, "Error al programar recordatorios", Toast.LENGTH_SHORT).show()
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
