package com.example.trabajointegradornativo

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import java.io.FileNotFoundException

class ProfileFragment : Fragment() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val PICK_IMAGE_REQUEST = 101
        private const val TAG = "ProfileFragment"
    }

    private lateinit var profileImage: ImageView
    private lateinit var profileInitials: TextView
    private lateinit var cameraIcon: ImageView
    private lateinit var dailyNotificationsSwitch: SwitchCompat
    private lateinit var motivationalPhrasesSwitch: SwitchCompat
    private lateinit var exportDataLayout: LinearLayout
    private lateinit var logoutLayout: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.profile_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupClickListeners()
        setupSwitches()
        setupBottomNavigation()
    }

    private fun initViews(view: View) {
        profileImage = view.findViewById(R.id.profileImage)
        profileInitials = view.findViewById(R.id.profileInitials)
        cameraIcon = view.findViewById(R.id.cameraIcon)
        dailyNotificationsSwitch = view.findViewById(R.id.dailyNotificationsSwitch)
        motivationalPhrasesSwitch = view.findViewById(R.id.motivationalPhrasesSwitch)
        exportDataLayout = view.findViewById(R.id.exportDataLayout)
        logoutLayout = view.findViewById(R.id.logoutLayout)
    }

    private fun setupClickListeners() {
        cameraIcon.setOnClickListener { checkPermissionAndOpenGallery() }
        profileImage.setOnClickListener { checkPermissionAndOpenGallery() }

        exportDataLayout.setOnClickListener {
            Toast.makeText(requireContext(), "Navegando a: Exportar datos", Toast.LENGTH_SHORT).show()
        }

        logoutLayout.setOnClickListener {
            Toast.makeText(requireContext(), "Navegando a: Cerrar sesión", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwitches() {
        dailyNotificationsSwitch.isChecked = true
        motivationalPhrasesSwitch.isChecked = true

        dailyNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "Activadas" else "Desactivadas"
            Toast.makeText(requireContext(), "Notificaciones diarias: $status", Toast.LENGTH_SHORT).show()
        }

        motivationalPhrasesSwitch.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "Activadas" else "Desactivadas"
            Toast.makeText(requireContext(), "Frases motivacionales: $status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = view?.findViewById<LinearLayout>(R.id.bottom_navigation)

        // Home
        val homeLayout = bottomNav?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            navigateToHome()
        }

        // Hoy
        val todayLayout = bottomNav?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            navigateToToday()
        }

        // Profile (ya estamos aquí)
        val profileLayout = bottomNav?.getChildAt(2) as? LinearLayout
        profileLayout?.setOnClickListener {
            Log.d(TAG, "Already in Profile")
            Toast.makeText(requireContext(), "Ya estás en Perfil", Toast.LENGTH_SHORT).show()
            updateBottomNavigationColors("profile")
        }

        // Establecer colores iniciales
        updateBottomNavigationColors("profile")
    }

    private fun navigateToHome() {
        try {
            Log.d(TAG, "Attempting to navigate to Home")

            // Primero intentar con la acción
            try {
                findNavController().navigate(R.id.action_profileFragment_to_itemListFragment)
                Log.d(TAG, "Navigation to Home successful via action")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Action navigation failed, trying direct navigation: ${e.message}")
            }

            // Si falla, intentar navegación directa
            findNavController().navigate(R.id.itemListFragment)
            Log.d(TAG, "Navigation to Home successful via direct ID")

        } catch (e: Exception) {
            Log.e(TAG, "All navigation attempts to Home failed: ${e.message}")
            Toast.makeText(requireContext(), "Error al navegar a Home: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToToday() {
        try {
            Log.d(TAG, "Attempting to navigate to Today")

            // Primero intentar con la acción
            try {
                findNavController().navigate(R.id.action_profileFragment_to_todayFragment)
                Log.d(TAG, "Navigation to Today successful via action")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Action navigation failed, trying direct navigation: ${e.message}")
            }

            // Si falla, intentar navegación directa
            findNavController().navigate(R.id.todayFragment)
            Log.d(TAG, "Navigation to Today successful via direct ID")

        } catch (e: Exception) {
            Log.e(TAG, "All navigation attempts to Today failed: ${e.message}")
            Toast.makeText(requireContext(), "Error al navegar a Hoy: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBottomNavigationColors(activeTab: String) {
        val bottomNav = view?.findViewById<LinearLayout>(R.id.bottom_navigation)

        // Home
        val homeLayout = bottomNav?.getChildAt(0) as? LinearLayout
        val homeIcon = homeLayout?.getChildAt(0) as? ImageView
        val homeText = homeLayout?.getChildAt(1) as? TextView

        // Hoy
        val todayLayout = bottomNav?.getChildAt(1) as? LinearLayout
        val todayIcon = todayLayout?.getChildAt(0) as? ImageView
        val todayText = todayLayout?.getChildAt(1) as? TextView

        // Profile
        val profileLayout = bottomNav?.getChildAt(2) as? LinearLayout
        val profileIcon = profileLayout?.getChildAt(0) as? ImageView
        val profileText = profileLayout?.getChildAt(1) as? TextView

        // Colores
        val activeColor = try {
            ContextCompat.getColor(requireContext(), R.color.primary_green)
        } catch (e: Exception) {
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
        }

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

    private fun checkPermissionAndOpenGallery() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        } else {
            openGallery()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK
            && data != null && data.data != null
        ) {
            val imageUri: Uri = data.data!!
            try {
                val imageStream = requireContext().contentResolver.openInputStream(imageUri)
                val selectedImage = BitmapFactory.decodeStream(imageStream)

                profileImage.setImageBitmap(selectedImage)
                profileImage.scaleType = ImageView.ScaleType.CENTER_CROP
                profileInitials.visibility = View.GONE

                Toast.makeText(requireContext(), "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permiso necesario para cambiar la foto de perfil",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}