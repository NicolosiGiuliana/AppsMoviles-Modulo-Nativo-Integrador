package com.example.trabajointegradornativo

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    // Inicializa la actividad, oculta la barra de acción y configura el idioma de la app.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        LanguageHelper.setAppLanguage(this, LanguageHelper.getAppLanguage(this))
    }

    // Aplica el contexto de idioma personalizado antes de adjuntar el contexto base.
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LanguageHelper.applyLanguageContext(it) })
    }

    // Se llama al reanudar la actividad, oculta la barra de acción y verifica si el idioma ha cambiado.
    override fun onResume() {
        super.onResume()

        supportActionBar?.hide()

        val currentLanguage = LanguageHelper.getAppLanguage(this)
        val systemLanguage = resources.configuration.locales[0].language

        if (currentLanguage != systemLanguage) {
            recreate()
        }
    }

    // Se llama después de onCreate, oculta la barra de acción.
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        supportActionBar?.hide()
    }
}