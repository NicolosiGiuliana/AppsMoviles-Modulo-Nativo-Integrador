package com.example.trabajointegradornativo

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
    }

    private lateinit var profileImage: ImageView
    private lateinit var profileInitials: TextView
    private lateinit var cameraIcon: ImageView
    private lateinit var dailyNotificationsSwitch: SwitchCompat
    private lateinit var motivationalPhrasesSwitch: SwitchCompat
    private lateinit var exportDataLayout: LinearLayout
    private lateinit var logoutLayout: LinearLayout
    private lateinit var navHome: LinearLayout
    private lateinit var navToday: LinearLayout
    private lateinit var navConfig: LinearLayout

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
        navHome = view.findViewById(R.id.navHome)
        navToday = view.findViewById(R.id.navToday)
        navConfig = view.findViewById(R.id.navConfig)
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
        navHome.setOnClickListener {
            findNavController().navigate(R.id.action_itemListFragment_to_createDesafioFragment)
        }

        navToday.setOnClickListener {
            findNavController().navigate(R.id.action_itemListFragment_to_todayFragment)
        }

        navConfig.setOnClickListener {
            Toast.makeText(requireContext(), "Ya estás en Configuración", Toast.LENGTH_SHORT).show()
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
