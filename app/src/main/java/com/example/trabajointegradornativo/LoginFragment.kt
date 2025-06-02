package com.example.trabajointegradornativo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private var isLoginMode = true // true = login, false = register

    // Views
    private lateinit var tvTitle: TextView
    private lateinit var usernameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var actionButton: Button
    private lateinit var toggleModeButton: Button
    private lateinit var logoutButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        auth = FirebaseAuth.getInstance()

        // Inicializar SharedPreferences
        sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        initViews(view)
        setupUI()
        setupClickListeners()

        return view
    }

    private fun initViews(view: View) {
        tvTitle = view.findViewById(R.id.tvTitle)
        usernameInput = view.findViewById(R.id.usernameInput)
        emailInput = view.findViewById(R.id.emailInput)
        passwordInput = view.findViewById(R.id.passwordInput)
        actionButton = view.findViewById(R.id.actionButton)
        toggleModeButton = view.findViewById(R.id.toggleModeButton)
        logoutButton = view.findViewById(R.id.logoutButton)
    }

    private fun setupUI() {
        updateUIMode()
    }

    private fun setupClickListeners() {
        // Botón principal (Login/Register)
        actionButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()

            if (validateInputs(email, password, username)) {
                if (isLoginMode) {
                    loginUser(email, password)
                } else {
                    registerUser(email, password, username)
                }
            }
        }

        // Botón para cambiar entre Login y Register
        toggleModeButton.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUIMode()
        }

        // Botón de logout (solo para debugging)
        logoutButton.setOnClickListener {
            logoutUser()
        }
    }

    private fun updateUIMode() {
        if (isLoginMode) {
            tvTitle.text = "Iniciar Sesión"
            actionButton.text = "Iniciar Sesión"
            toggleModeButton.text = "¿No tienes cuenta? Regístrate"
            usernameInput.visibility = View.GONE
        } else {
            tvTitle.text = "Registrarse"
            actionButton.text = "Registrarse"
            toggleModeButton.text = "¿Ya tienes cuenta? Inicia sesión"
            usernameInput.visibility = View.VISIBLE
        }
    }

    private fun validateInputs(email: String, password: String, username: String): Boolean {
        if (email.isEmpty()) {
            emailInput.error = "El email es requerido"
            return false
        }

        if (password.isEmpty()) {
            passwordInput.error = "La contraseña es requerida"
            return false
        }

        if (password.length < 6) {
            passwordInput.error = "La contraseña debe tener al menos 6 caracteres"
            return false
        }

        if (!isLoginMode && username.isEmpty()) {
            usernameInput.error = "El nombre de usuario es requerido"
            return false
        }

        return true
    }

    private fun loginUser(email: String, password: String) {
        actionButton.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                actionButton.isEnabled = true

                if (task.isSuccessful) {
                    val user = auth.currentUser

                    // Guardar datos en SharedPreferences
                    saveUserToPreferences(
                        email = email,
                        username = user?.displayName ?: "Usuario",
                        isLoggedIn = true
                    )

                    Toast.makeText(context, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                    navigateToMainApp()
                } else {
                    Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun registerUser(email: String, password: String, username: String) {
        actionButton.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                actionButton.isEnabled = true

                if (task.isSuccessful) {
                    val user = auth.currentUser

                    // Actualizar el perfil del usuario con el nombre
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()

                    user?.updateProfile(profileUpdates)?.addOnCompleteListener { profileTask ->
                        if (profileTask.isSuccessful) {
                            // Guardar datos en SharedPreferences
                            saveUserToPreferences(
                                email = email,
                                username = username,
                                isLoggedIn = true
                            )

                            Toast.makeText(context, "Registro exitoso", Toast.LENGTH_SHORT).show()
                            navigateToMainApp()
                        }
                    }
                } else {
                    Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun logoutUser() {
        auth.signOut()

        // Limpiar SharedPreferences
        saveUserToPreferences(
            email = "",
            username = "",
            isLoggedIn = false
        )

        Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()

        // Opcional: recargar el fragment para mostrar la pantalla de login
        updateUIMode()
    }

    private fun saveUserToPreferences(email: String, username: String, isLoggedIn: Boolean) {
        with(sharedPreferences.edit()) {
            putString("user_email", email)
            putString("user_name", username)
            putBoolean("is_logged_in", isLoggedIn)
            apply()
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(requireContext(), ItemDetailHostActivity::class.java)
        startActivity(intent)
        activity?.finish()
    }
}