package com.example.trabajointegradornativo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.firebase.auth.FirebaseAuth

class ItemDetailHostActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Usar el layout existente
        setContentView(R.layout.activity_item_detail)

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_item_detail) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // CORREGIR: Usar el navigation graph correcto
        val desafioId = intent.getStringExtra("desafio_id")
        Log.d("ItemDetailHostActivity", "ID recibido del Intent: $desafioId")

        if (desafioId != null) {
            val bundle = Bundle().apply {
                putString("desafio_id", desafioId)
                putString("item_id", desafioId) // También agregar como item_id para compatibilidad
            }
            // CAMBIAR: Usar primary_details_nav_graph en lugar de item_detail
            navController.setGraph(R.navigation.primary_details_nav_graph, bundle)
        } else {
            // Si no hay ID, usar el graph sin argumentos
            navController.setGraph(R.navigation.primary_details_nav_graph)
        }

        // Verificar que el usuario esté autenticado
        checkAuthentication()
    }

    private fun checkAuthentication() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)

        if (currentUser == null || !isLoggedIn) {
            // Usuario no autenticado, regresar a MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_item_detail)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}