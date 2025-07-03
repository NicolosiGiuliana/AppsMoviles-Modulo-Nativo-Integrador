package com.example.trabajointegradornativo

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var sharedPreferences: SharedPreferences

    // Metodo principal que se llama al crear la actividad. Inicializa la navegación y verifica el estado del usuario.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        checkUserStatus()
    }

    // Verifica si el usuario está autenticado y navega a la pantalla correspondiente.
    private fun checkUserStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val isLoggedInPrefs = sharedPreferences.getBoolean("is_logged_in", false)

        if (currentUser != null && isLoggedInPrefs) {
            navigateToMainApp()
        } else {
            navController.navigate(R.id.loginFragment)
        }
    }

    // Navega a la actividad principal de la aplicación si el usuario está autenticado.
    private fun navigateToMainApp() {
        val intent = Intent(this, ItemDetailHostActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Al reanudar la actividad, vuelve a verificar el estado del usuario.
    override fun onResume() {
        super.onResume()
        checkUserStatus()
    }

    // Clase Application para obtener el contexto global de la app.
    class App : Application() {
        // Inicializa la instancia de la aplicación.
        override fun onCreate() {
            super.onCreate()
            instance = this
        }

        companion object {
            private var instance: App? = null
            // Proporciona el contexto de la aplicación de forma global.
            val context: Context
                get() = instance?.applicationContext
                    ?: throw IllegalStateException("Application is not initialized.")
        }
    }
}