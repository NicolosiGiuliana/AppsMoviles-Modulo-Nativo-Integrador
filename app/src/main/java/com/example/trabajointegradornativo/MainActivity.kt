package com.example.trabajointegradornativo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.trabajointegradornativo.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar el binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtener el NavController del NavHostFragment
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Verificar si el usuario está autenticado
        checkUserStatus()
    }

    private fun checkUserStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            // Si no está logueado, permanecer en LoginFragment
            navController.navigate(R.id.loginFragment)

        } else {
            // Si el usuario está logueado, navegar al InicioFragment
            val intent = Intent(this, ItemDetailHostActivity::class.java)
            startActivity(intent)
            finish() // opcional pero recomendado para no volver a esta actividad
        }
    }
}
