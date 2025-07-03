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
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
        private const val PICK_IMAGE_REQUEST = 102
        private const val CAMERA_REQUEST = 103
        private const val MAX_IMAGE_SIZE = 1024
        private const val JPEG_QUALITY = 80
    }

    private lateinit var imgBBUploader: ImgBBUploader
    private var uploadProgressDialog: AlertDialog? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var sharedPreferences: SharedPreferences

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

    private lateinit var languages: Array<String>
    private val languageCodes = arrayOf("es", "en", "pt")
    private var selectedLanguageIndex = 0
    private var selectedNotificationHour = 9
    private var selectedNotificationMinute = 0

    // Infla el layout del fragmento de perfil.
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.profile_fragment, container, false)
    }

    // Inicializa vistas, datos y listeners al crear la vista.
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

    // Inicializa el array de idiomas disponibles.
    private fun initLanguageArray() {
        languages = arrayOf(
            getString(R.string.language_spanish),
            getString(R.string.language_english),
            getString(R.string.language_portuguese)
        )
    }

    // Configura la navegación inferior del fragmento.
    private fun setupBottomNavigation() {
        val bottomNavigation = view?.findViewById<LinearLayout>(R.id.bottom_navigation)

        val homeLayout = bottomNavigation?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_profileFragment_to_itemListFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val todayLayout = bottomNavigation?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_profileFragment_to_todayFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val exploreLayout = bottomNavigation?.getChildAt(2) as? LinearLayout
        exploreLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_profileFragment_to_publicChallengeFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val profileLayout = bottomNavigation?.getChildAt(3) as? LinearLayout
        profileLayout?.setOnClickListener {
        }
    }

    // Inicializa las instancias de Firebase y preferencias.
    private fun initFirebase() {
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        imgBBUploader = ImgBBUploader()
    }

    // Inicializa las vistas del layout.
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

    // Carga los datos del usuario en la UI.
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

    // Obtiene las iniciales del nombre del usuario.
    private fun getInitials(name: String): String {
        val words = name.trim().split(" ")
        return when {
            words.size >= 2 -> "${words[0].first().uppercase()}${words[1].first().uppercase()}"
            words.size == 1 && words[0].length >= 2 -> "${words[0].first().uppercase()}${words[0][1].uppercase()}"
            words.size == 1 -> words[0].first().uppercase()
            else -> "U"
        }
    }

    // Carga las preferencias y configuraciones guardadas.
    private fun loadSettings() {
        val notificationsEnabled = sharedPreferences.getBoolean("daily_notifications", true)
        dailyNotificationsSwitch.isChecked = notificationsEnabled

        val motivationalEnabled = sharedPreferences.getBoolean("motivational_phrases", true)
        motivationalPhrasesSwitch.isChecked = motivationalEnabled

        selectedNotificationHour = sharedPreferences.getInt("notification_hour", 9)
        selectedNotificationMinute = sharedPreferences.getInt("notification_minute", 0)
        updateNotificationTimeDisplay()

        selectedLanguageIndex = getCurrentLanguageIndex()

        if (selectedLanguageIndex < 0 || selectedLanguageIndex >= languages.size) {
            selectedLanguageIndex = 0
        }

        selectedLanguageText.text = languages[selectedLanguageIndex]
    }

    // Obtiene el índice del idioma actual.
    private fun getCurrentLanguageIndex(): Int {
        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale.language
        }

        val index = when (currentLocale) {
            "es" -> 0
            "en" -> 1
            "pt" -> 2
            else -> {
                val savedLanguage = LanguageHelper.getAppLanguage(requireContext())
                languageCodes.indexOf(savedLanguage).takeIf { it >= 0 } ?: 0
            }
        }

        return index
    }

    // Actualiza el texto que muestra la hora de notificación.
    private fun updateNotificationTimeDisplay() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedNotificationHour)
        calendar.set(Calendar.MINUTE, selectedNotificationMinute)
        notificationTimeText.text = timeFormat.format(calendar.time)
    }

    // Asigna los listeners a los elementos interactivos de la UI.
    private fun setupClickListeners() {
        cameraIcon.setOnClickListener { showImagePickerDialog() }
        profileImage.setOnClickListener { showImagePickerDialog() }
        notificationTimeLayout.setOnClickListener { showTimePickerDialog() }
        languageLayout.setOnClickListener { showLanguageDialog() }
        logoutLayout.setOnClickListener { showLogoutDialog() }
        profileImage.setOnClickListener { mostrarImagenPerfilEnDialogo() }
    }

    // Configura los switches de notificaciones y frases motivacionales.
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

    // Verifica y solicita permisos necesarios para notificaciones.
    private fun checkNotificationPermissions(callback: (Boolean) -> Unit) {
        var permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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
                callback(true)
                return
            }
        }

        callback(true)
    }

    // Muestra un diálogo para solicitar permiso de alarmas exactas.
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

    // Carga la imagen de perfil del usuario.
    private fun loadProfileImage() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val useDefaultImage = sharedPreferences.getBoolean("use_default_image", false)

            if (useDefaultImage) {
                setDefaultProfileImage()
                return
            }

            val savedImageUrl = sharedPreferences.getString("profile_image_url", null)

            if (savedImageUrl != null) {
                loadImageFromUrl(savedImageUrl)
            } else {
                loadImageUrlFromFirestore(currentUser.uid)
            }
        } else {
            setDefaultProfileImage()
        }
    }

    // Obtiene la URL de la imagen de perfil desde Firestore.
    private fun loadImageUrlFromFirestore(userId: String) {
        firestore.collection("usuarios").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val useDefaultImage = document.getBoolean("useDefaultImage") ?: false

                    if (useDefaultImage) {
                        sharedPreferences.edit().putBoolean("use_default_image", true).apply()
                        setDefaultProfileImage()
                        return@addOnSuccessListener
                    }

                    val imageUrl = document.getString("profileImageUrl")
                    if (imageUrl != null) {
                        loadImageFromUrl(imageUrl)
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
                setDefaultProfileImage()
            }
    }

    // Descarga y muestra la imagen de perfil desde una URL.
    private fun loadImageFromUrl(imageUrl: String) {
        Thread {
            try {
                val url = URL(imageUrl)
                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())

                Handler(Looper.getMainLooper()).post {
                    profileImage.setImageBitmap(bitmap)
                    profileImage.visibility = View.VISIBLE
                    profileInitials.visibility = View.GONE
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    setDefaultProfileImage()
                }
            }
        }.start()
    }

    // Muestra la imagen de perfil por defecto (iniciales).
    private fun setDefaultProfileImage() {
        try {
            val currentUser = auth.currentUser
            val userName = currentUser?.displayName ?: getString(R.string.default_name)
            val initials = getInitials(userName)

            profileImage.visibility = View.GONE
            profileInitials.text = initials
            profileInitials.visibility = View.VISIBLE

            profileInitials.setBackgroundResource(R.drawable.profile_circle_bg)

        } catch (e: Exception) {
            profileImage.visibility = View.GONE
            profileInitials.text = "U"
            profileInitials.visibility = View.VISIBLE
        }
    }

    // Muestra un diálogo para elegir cómo cambiar la foto de perfil.
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
                        clearSavedImageUrls()
                        setDefaultProfileImage()
                        Toast.makeText(requireContext(), getString(R.string.default_image_set), Toast.LENGTH_SHORT).show()
                    }
                    3 -> { }
                }
            }
            .show()
    }

    // Limpia las URLs guardadas de la imagen de perfil.
    private fun clearSavedImageUrls() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            sharedPreferences.edit()
                .remove("profile_image_url")
                .remove("profile_image_delete_url")
                .apply()

            firestore.collection("usuarios").document(currentUser.uid)
                .update(
                    mapOf(
                        "profileImageUrl" to null,
                        "profileImageDeleteUrl" to null,
                        "useDefaultImage" to true,
                        "lastUpdated" to com.google.firebase.Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                }
                .addOnFailureListener { e ->
                }
        }
    }

    // Verifica permisos de cámara y toma una foto.
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

    // Verifica permisos de galería y abre la galería.
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

    // Lanza la intención para tomar una foto.
    private fun takePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA_REQUEST)
    }

    // Lanza la intención para seleccionar una imagen de la galería.
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    // Maneja el resultado de las solicitudes de permisos.
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

    // Maneja el resultado de las actividades de selección/captura de imagen.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    data?.data?.let { uri -> handleSelectedImage(uri) }
                }
                CAMERA_REQUEST -> {
                    val bitmap = data?.extras?.get("data") as? Bitmap
                    bitmap?.let { handleCapturedImage(it) }
                }
            }
        }
    }

    // Procesa la imagen seleccionada de la galería.
    private fun handleSelectedImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
            uploadProfileImage(resizedBitmap)
        } catch (e: FileNotFoundException) {
            Toast.makeText(requireContext(), getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show()
        }
    }

    // Procesa la imagen capturada con la cámara.
    private fun handleCapturedImage(bitmap: Bitmap) {
        val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
        uploadProfileImage(resizedBitmap)
    }

    // Redimensiona el bitmap a un tamaño máximo.
    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val ratio = if (width > height) {
            maxSize.toFloat() / width.toFloat()
        } else {
            maxSize.toFloat() / height.toFloat()
        }

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // Sube la imagen de perfil a ImgBB y actualiza la UI y Firestore.
    private fun uploadProfileImage(bitmap: Bitmap) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show()
            return
        }

        showUploadProgressDialog()

        imgBBUploader.uploadImage(bitmap, object : ImgBBUploader.UploadCallback {
            override fun onSuccess(imageUrl: String, deleteUrl: String) {
                Handler(Looper.getMainLooper()).post {
                    hideUploadProgressDialog()

                    profileImage.setImageBitmap(bitmap)
                    profileImage.visibility = View.VISIBLE
                    profileInitials.visibility = View.GONE

                    sharedPreferences.edit()
                        .putString("profile_image_url", imageUrl)
                        .putString("profile_image_delete_url", deleteUrl)
                        .putBoolean("use_default_image", false)
                        .apply()

                    saveImageUrlToFirestore(currentUser.uid, imageUrl, deleteUrl)

                    Toast.makeText(requireContext(), getString(R.string.profile_photo_updated), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                Handler(Looper.getMainLooper()).post {
                    hideUploadProgressDialog()
                    Toast.makeText(requireContext(), getString(R.string.error_uploading_image) + ": $error", Toast.LENGTH_LONG).show()

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

    // Guarda la URL de la imagen de perfil en Firestore.
    private fun saveImageUrlToFirestore(userId: String, imageUrl: String, deleteUrl: String) {
        val userRef = firestore.collection("usuarios").document(userId)
        val imageData = mapOf(
            "profileImageUrl" to imageUrl,
            "profileImageDeleteUrl" to deleteUrl,
            "useDefaultImage" to false,
            "lastUpdated" to com.google.firebase.Timestamp.now()
        )

        userRef.update(imageData)
            .addOnSuccessListener {
            }
            .addOnFailureListener { e ->
            }
    }

    // Muestra un diálogo de progreso durante la subida de imagen.
    private fun showUploadProgressDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(android.R.layout.simple_list_item_1, null)
        val textView = dialogView.findViewById<TextView>(android.R.id.text1)
        textView.text = getString(R.string.uploading_image_progress, 0)

        uploadProgressDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.uploading_image_title))
            .setView(dialogView)
            .setCancelable(false)
            .create()

        uploadProgressDialog?.show()
    }

    // Actualiza el progreso mostrado en el diálogo de subida.
    private fun updateUploadProgress(progress: Int) {
        uploadProgressDialog?.let { dialog ->
            val textView = dialog.findViewById<TextView>(android.R.id.text1)
            textView?.text = getString(R.string.uploading_image_progress, progress)
        }
    }

    // Oculta el diálogo de progreso de subida.
    private fun hideUploadProgressDialog() {
        uploadProgressDialog?.dismiss()
        uploadProgressDialog = null
    }

    // Muestra un diálogo de error si falla la subida de imagen.
    private fun showUploadErrorDialog(bitmap: Bitmap) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.error_uploading_image_title))
            .setMessage(getString(R.string.error_upload_imgbb_message))
            .setPositiveButton(getString(R.string.retry)) { _, _ -> uploadProfileImage(bitmap) }
            .setNegativeButton(getString(R.string.use_locally_upload)) { _, _ ->
                profileImage.setImageBitmap(bitmap)
                profileImage.visibility = View.VISIBLE
                profileInitials.visibility = View.GONE
                Toast.makeText(requireContext(), getString(R.string.image_set_locally_upload), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(getString(R.string.cancel), null)
            .show()
    }

    // Muestra un selector de hora para configurar la notificación diaria.
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

    // Guarda la hora seleccionada para la notificación diaria.
    private fun saveNotificationTime() {
        sharedPreferences.edit()
            .putInt("notification_hour", selectedNotificationHour)
            .putInt("notification_minute", selectedNotificationMinute)
            .apply()
    }

    // Programa la notificación diaria con la hora seleccionada.
    private fun scheduleNotifications() {
        val context = requireContext()
        val hour = selectedNotificationHour
        val minute = selectedNotificationMinute

        try {
            val notificationHelper = NotificationHelper(context)
            notificationHelper.programarNotificacionDiaria(hour, minute)
            Toast.makeText(context, getString(R.string.reminders_scheduled, hour, String.format("%02d", minute)), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.error_scheduling_reminders), Toast.LENGTH_SHORT).show()
        }
    }

    // Muestra un diálogo para seleccionar el idioma de la app.
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

    // Guarda la preferencia de idioma seleccionada.
    private fun saveLanguagePreference() {
        LanguageHelper.setAppLanguage(requireContext(), languageCodes[selectedLanguageIndex])
    }

    // Cambia el idioma de la aplicación y reinicia la actividad.
    private fun changeAppLanguage(languageCode: String) {
        LanguageHelper.setAppLanguage(requireContext(), languageCode)
        requireActivity().recreate()
    }

    // Muestra un diálogo de confirmación para cerrar sesión.
    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logout_confirmation))
            .setPositiveButton(getString(R.string.logout)) { _, _ -> performLogout() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Realiza el cierre de sesión y limpia las preferencias.
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

    // Muestra la imagen de perfil en un diálogo ampliado.
    private fun mostrarImagenPerfilEnDialogo() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show()
            return
        }

        val savedImageUrl = sharedPreferences.getString("profile_image_url", null)
        val useDefaultImage = sharedPreferences.getBoolean("use_default_image", false)

        if (useDefaultImage || savedImageUrl == null) {
            Toast.makeText(requireContext(), getString(R.string.no_profile_image_show), Toast.LENGTH_SHORT).show()
            return
        }

        mostrarImagenEnDialogo(savedImageUrl)
    }

    // Descarga y muestra una imagen en un diálogo a partir de una URL.
    private fun mostrarImagenEnDialogo(url: String) {
        try {
            val imageView = ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val dialog = AlertDialog.Builder(requireContext())
                .setView(imageView)
                .setPositiveButton(getString(R.string.close_dialog)) { dialog, _ -> dialog.dismiss() }
                .setNeutralButton(getString(R.string.change_photo_dialog)) { dialog, _ ->
                    dialog.dismiss()
                    showImagePickerDialog()
                }
                .create()

            imageView.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_gallery)
            )

            dialog.show()

            Thread {
                try {
                    val connection = URL(url).openConnection()
                    connection.doInput = true
                    connection.connect()
                    val inputStream = connection.getInputStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    requireActivity().runOnUiThread {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                        } else {
                            imageView.setImageDrawable(
                                ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_delete)
                            )
                            Toast.makeText(context, getString(R.string.error_loading_image_dialog_message), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        imageView.setImageDrawable(
                            ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_delete)
                        )
                        Toast.makeText(context, getString(R.string.error_loading_image_with_message, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.error_showing_image_message, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}