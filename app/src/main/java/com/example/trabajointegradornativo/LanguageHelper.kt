package com.example.trabajointegradornativo

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LanguageHelper {

    private const val PREF_LANGUAGE = "selected_language"
    private const val DEFAULT_LANGUAGE = "es"

    fun setAppLanguage(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        }

        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        // Guardar preferencia
        saveLanguagePreference(context, languageCode)
    }

    fun getAppLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREF_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    private fun saveLanguagePreference(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANGUAGE, languageCode).apply()
    }

    fun applyLanguageContext(context: Context): Context {
        val languageCode = getAppLanguage(context)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    fun getLanguageDisplayName(languageCode: String, context: Context): String {
        return when (languageCode) {
            "es" -> context.getString(R.string.language_spanish)
            "en" -> context.getString(R.string.language_english)
            "pt" -> context.getString(R.string.language_portuguese)
            else -> context.getString(R.string.language_spanish)
        }
    }

    // Nuevo método para cambiar idioma sin recrear inmediatamente
    fun changeLanguageWithoutRecreate(context: Context, languageCode: String) {
        saveLanguagePreference(context, languageCode)
        setAppLanguage(context, languageCode)
    }
}