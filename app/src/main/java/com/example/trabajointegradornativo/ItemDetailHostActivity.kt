package com.example.trabajointegradornativo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
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

        // Verificar si viene de una notificación y navegar al Today Fragment
        handleNotificationNavigation(navController)
    }

    private fun handleNotificationNavigation(navController: androidx.navigation.NavController) {
        if (intent.hasExtra("navigate_to") && intent.getStringExtra("navigate_to") == "today_fragment") {
            // CAMBIAR: Aumentar el delay y asegurar navegación
            findViewById<View>(android.R.id.content).postDelayed({
                try {
                    // Verificar que el grafo esté configurado
                    if (navController.graph.id != 0) {
                        navController.navigate(R.id.todayFragment)
                        Log.d("Navigation", "Navegando a Today Fragment desde notificación")
                    } else {
                        // Si el grafo no está listo, intentar de nuevo
                        findViewById<View>(android.R.id.content).postDelayed({
                            navController.navigate(R.id.todayFragment)
                        }, 200)
                    }
                } catch (e: Exception) {
                    Log.e("Navigation", "Error navegando a Today Fragment", e)
                }
            }, 300) // Aumentar delay a 300ms
        }
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent?.hasExtra("navigate_to") == true && intent.getStringExtra("navigate_to") == "today_fragment") {
            val navController = findNavController(R.id.nav_host_fragment_item_detail)
            // CAMBIAR: Agregar delay también aquí
            findViewById<View>(android.R.id.content).postDelayed({
                try {
                    navController.navigate(R.id.todayFragment)
                } catch (e: Exception) {
                    Log.e("Navigation", "Error en onNewIntent navegando a Today Fragment", e)
                }
            }, 100)
        }
    }

    private fun checkAuthentication() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)

        // CAMBIAR: No verificar si viene de notificación
        val fromNotification = intent.getBooleanExtra("from_notification", false)

        if ((currentUser == null || !isLoggedIn) && !fromNotification) {
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