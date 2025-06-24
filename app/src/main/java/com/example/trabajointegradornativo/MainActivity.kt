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

        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        checkUserStatus()
    }

    private fun checkUserStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val isLoggedInPrefs = sharedPreferences.getBoolean("is_logged_in", false)

        if (currentUser != null && isLoggedInPrefs) {
            navigateToMainApp()
        } else {
            navController.navigate(R.id.loginFragment)
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, ItemDetailHostActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
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
                get() = instance?.applicationContext
                    ?: throw IllegalStateException("Application is not initialized.")
        }
    }
}