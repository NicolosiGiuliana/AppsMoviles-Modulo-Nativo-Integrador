package com.example.trabajointegradornativo

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    // Esta clase sirve como base para todas las actividades de la aplicacion
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        LanguageHelper.setAppLanguage(this, LanguageHelper.getAppLanguage(this))
    }

    // Sobreescribimos el metodo attachBaseContext para aplicar el idioma seleccionado
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LanguageHelper.applyLanguageContext(it) })
    }

    // Sobreescribimos el metodo onResume para ocultar la barra de accion
    override fun onResume() {
        super.onResume()

        supportActionBar?.hide()

        val currentLanguage = LanguageHelper.getAppLanguage(this)
        val systemLanguage = resources.configuration.locales[0].language

        if (currentLanguage != systemLanguage) {
            recreate()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        supportActionBar?.hide()
    }
}