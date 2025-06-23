package com.example.trabajointegradornativo

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.trabajointegradornativo.LanguageHelper


abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageHelper.setAppLanguage(this, LanguageHelper.getAppLanguage(this))
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LanguageHelper.applyLanguageContext(it) })
    }

    override fun onResume() {
        super.onResume()
        val currentLanguage = LanguageHelper.getAppLanguage(this)
        val systemLanguage = resources.configuration.locales[0].language

        if (currentLanguage != systemLanguage) {
            recreate()
        }
    }
}
