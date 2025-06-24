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

    companion object {
        private const val TAG = "ItemDetailHostActivity"

        // Constantes para claves técnicas (NO localizadas)
        private const val PREF_USER_PREFS = "user_prefs"
        private const val KEY_DESAFIO_ID = "desafio_id"
        private const val KEY_ITEM_ID = "item_id"
        private const val KEY_NAVIGATE_TO = "navigate_to"
        private const val KEY_FROM_NOTIFICATION = "from_notification"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"

        // Constantes para valores de navegación
        private const val VALUE_TODAY_FRAGMENT = "today_fragment"

        // Delays para navegación
        private const val NAVIGATION_DELAY_LONG = 300L
        private const val NAVIGATION_DELAY_SHORT = 100L
        private const val NAVIGATION_DELAY_RETRY = 200L
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_item_detail)

        // CORREGIDO: Usar constante para SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_USER_PREFS, Context.MODE_PRIVATE)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_item_detail) as NavHostFragment
        val navController = navHostFragment.navController

        // CORREGIDO: Usar constante para Intent extra
        val desafioId = intent.getStringExtra(KEY_DESAFIO_ID)

        if (desafioId != null) {
            val bundle = Bundle().apply {
                // CORREGIDO: Usar constantes para Bundle keys
                putString(KEY_DESAFIO_ID, desafioId)
                putString(KEY_ITEM_ID, desafioId)
            }
            navController.setGraph(R.navigation.primary_details_nav_graph, bundle)
        } else {
            navController.setGraph(R.navigation.primary_details_nav_graph)
        }

        checkAuthentication()
        handleNotificationNavigation(navController)
    }

    private fun handleNotificationNavigation(navController: androidx.navigation.NavController) {
        if (intent.hasExtra(KEY_NAVIGATE_TO) &&
            intent.getStringExtra(KEY_NAVIGATE_TO) == VALUE_TODAY_FRAGMENT) {

            findViewById<View>(android.R.id.content).postDelayed({
                try {
                    if (navController.graph.id != 0) {
                        navController.navigate(R.id.todayFragment)
                    } else {
                        findViewById<View>(android.R.id.content).postDelayed({
                            navController.navigate(R.id.todayFragment)
                        }, NAVIGATION_DELAY_RETRY)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error navegando a Today Fragment", e)
                }
            }, NAVIGATION_DELAY_LONG)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        // CORREGIDO: Usar constantes para comparaciones
        if (intent?.hasExtra(KEY_NAVIGATE_TO) == true &&
            intent.getStringExtra(KEY_NAVIGATE_TO) == VALUE_TODAY_FRAGMENT) {

            val navController = findNavController(R.id.nav_host_fragment_item_detail)
            findViewById<View>(android.R.id.content).postDelayed({
                try {
                    navController.navigate(R.id.todayFragment)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en onNewIntent navegando a Today Fragment", e)
                }
            }, NAVIGATION_DELAY_SHORT)
        }
    }

    private fun checkAuthentication() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        // CORREGIDO: Usar constante para SharedPreferences key
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)

        // CORREGIDO: Usar constante para Intent extra
        val fromNotification = intent.getBooleanExtra(KEY_FROM_NOTIFICATION, false)

        if ((currentUser == null || !isLoggedIn) && !fromNotification) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_item_detail)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}