package com.example.trabajointegradornativo

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        LanguageHelper.setAppLanguage(this, LanguageHelper.getAppLanguage(this))
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LanguageHelper.applyLanguageContext(it) })
    }

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