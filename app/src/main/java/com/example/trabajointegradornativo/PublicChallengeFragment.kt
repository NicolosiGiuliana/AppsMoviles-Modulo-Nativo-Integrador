package com.example.trabajointegradornativo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class PublicChallengeFragment: Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_public_challenge, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = view?.findViewById<LinearLayout>(R.id.bottom_navigation)

        // Home (índice 0) - Navega a itemListFragment
        val homeLayout = bottomNavigation?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_publicChallengeFragment_to_itemListFragment)
                updateBottomNavigationColors("home")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Today (índice 1)
        val todayLayout = bottomNavigation?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_publicChallengeFragment_to_todayFragment)
                updateBottomNavigationColors("today")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Explorar (índice 2) - ya estamos aquí
        val exploreLayout = bottomNavigation?.getChildAt(2) as? LinearLayout
        exploreLayout?.setOnClickListener {
            // Ya estamos en Explorar, solo actualizar colores
            updateBottomNavigationColors("explore")
        }

        // Profile (índice 3)
        val profileLayout = bottomNavigation?.getChildAt(3) as? LinearLayout
        profileLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_publicChallengeFragment_to_profileFragment)
                updateBottomNavigationColors("profile")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Establecer colores iniciales (Explorar activo)
        updateBottomNavigationColors("explore")
    }

    private fun updateBottomNavigationColors(activeTab: String) {
        val bottomNavigation = view?.findViewById<LinearLayout>(R.id.bottom_navigation)
        val activeColor = ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark) // Verde
        val inactiveColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray) // Gris

        // Definir los tabs
        val tabs = listOf("home", "today", "explore", "profile")

        for (i in tabs.indices) {
            val tabLayout = bottomNavigation?.getChildAt(i) as? LinearLayout
            val imageView = tabLayout?.getChildAt(0) as? ImageView
            val textView = tabLayout?.getChildAt(1) as? TextView

            if (tabs[i] == activeTab) {
                // Tab activo - Verde
                imageView?.setColorFilter(activeColor)
                textView?.setTextColor(activeColor)
            } else {
                // Tab inactivo - Gris
                imageView?.setColorFilter(inactiveColor)
                textView?.setTextColor(inactiveColor)
            }
        }
    }
}