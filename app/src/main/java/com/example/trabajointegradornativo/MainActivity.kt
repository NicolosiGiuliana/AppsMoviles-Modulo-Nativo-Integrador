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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Usar el layout existente sin binding
        setContentView(R.layout.activity_main)


        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // Obtener el NavController del NavHostFragment
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Verificar si el usuario está autenticado
        checkUserStatus()
    }

    private fun checkUserStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val isLoggedInPrefs = sharedPreferences.getBoolean("is_logged_in", false)

        // Verificar tanto Firebase Auth como SharedPreferences
        if (currentUser != null && isLoggedInPrefs) {
            // Usuario está logueado, ir a la app principal
            navigateToMainApp()
        } else {
            // Usuario no está logueado, mostrar pantalla de login
            navController.navigate(R.id.loginFragment)
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, ItemDetailHostActivity::class.java)
        startActivity(intent)
        finish() // Cerrar MainActivity para que no pueda volver con back
    }

    override fun onResume() {
        super.onResume()
        // Verificar el estado del usuario cada vez que la actividad se reanuda
        checkUserStatus()
    }

    class App : Application() {
        override fun onCreate() {
            super.onCreate()
            instance = this
        }

        companion object {
            private var instance: App? = null
            val context: Context
                get() = instance?.applicationContext ?: throw IllegalStateException("Application is not initialized.")
        }
    }
}
