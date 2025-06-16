package com.example.trabajointegradornativo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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

    // Remover onCreateOptionsMenu para que no aparezca ningún botón en la toolbar
    // override fun onCreateOptionsMenu(menu: Menu): Boolean {
    //     // No crear ningún menú
    //     return false
    // }

    // Remover onOptionsItemSelected ya que no hay menú
    // override fun onOptionsItemSelected(item: MenuItem): Boolean {
    //     return super.onOptionsItemSelected(item)
    // }

    // Remover la función logoutUser ya que el logout se manejará solo desde ProfileFragment
    // private fun logoutUser() { ... }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_item_detail)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}