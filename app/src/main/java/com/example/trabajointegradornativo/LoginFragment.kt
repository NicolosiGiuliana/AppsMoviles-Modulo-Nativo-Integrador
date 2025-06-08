package com.example.trabajointegradornativo

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import java.text.SimpleDateFormat
import java.util.*

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private var isLoginMode = true // true = login, false = register

    // Views
    private lateinit var tvTitle: TextView
    private lateinit var usernameInput: EditText
    private lateinit var nameInputLayout: TextInputLayout

    // New register fields
    private lateinit var dateTitle: TextView
    private lateinit var dateInput: EditText
    private lateinit var dateInputLayout: TextInputLayout
    private lateinit var objectiveTitle: TextView
    private lateinit var objectiveSpinner: AutoCompleteTextView
    private lateinit var objectiveInputLayout: TextInputLayout
    private lateinit var confirmPasswordTitle: TextView
    private lateinit var confirmPasswordInput: EditText
    private lateinit var confirmPasswordInputLayout: TextInputLayout

    // Existing fields
    private lateinit var emailTitle: TextView
    private lateinit var nameTitle: TextView
    private lateinit var passwordTitle: TextView
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var actionButton: Button
    private lateinit var toggleModeButton: Button

    // Data - Actualizados los 4 objetivos principales
    private val objectives = arrayOf(
        "Fitness",
        "Lectura",
        "Mindfulness",
        "Hidratación",
        "Otro"
    )

    private var selectedDate: String = ""

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

        // Titles
        nameTitle = view.findViewById(R.id.nameTitle)
        dateTitle = view.findViewById(R.id.dateTitle)
        objectiveTitle = view.findViewById(R.id.objectiveTitle)
        emailTitle = view.findViewById(R.id.emailTitle)
        passwordTitle = view.findViewById(R.id.passwordTitle)
        confirmPasswordTitle = view.findViewById(R.id.confirmPasswordTitle)

        // Input fields
        usernameInput = view.findViewById(R.id.usernameInput)
        nameInputLayout = view.findViewById(R.id.nameInputLayout)
        dateInput = view.findViewById(R.id.dateInput)
        dateInputLayout = view.findViewById(R.id.dateInputLayout)
        objectiveSpinner = view.findViewById(R.id.objectiveSpinner)
        objectiveInputLayout = view.findViewById(R.id.objectiveInputLayout)
        emailInput = view.findViewById(R.id.emailInput)
        passwordInput = view.findViewById(R.id.passwordInput)
        confirmPasswordInput = view.findViewById(R.id.confirmPasswordInput)
        confirmPasswordInputLayout = view.findViewById(R.id.confirmPasswordInputLayout)

        // Buttons
        actionButton = view.findViewById(R.id.actionButton)
        toggleModeButton = view.findViewById(R.id.toggleModeButton)
    }

    private fun setupUI() {
        setupObjectiveSpinner()
        setupDatePicker()
        setupPasswordConfirmation()
        updateUIMode()
    }

    private fun setupObjectiveSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, objectives)
        objectiveSpinner.setAdapter(adapter)
        objectiveSpinner.setOnClickListener { objectiveSpinner.showDropDown() }
    }

    private fun setupDatePicker() {
        dateInput.setOnClickListener {
            showDatePicker()
        }
        dateInput.isFocusable = false
        dateInput.isClickable = true
    }

    private fun setupPasswordConfirmation() {
        // Disable copy/paste for confirm password
        confirmPasswordInput.setOnLongClickListener { true } // Block long click
        confirmPasswordInput.setTextIsSelectable(false)
        confirmPasswordInput.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                selectedDate = dateFormat.format(selectedCalendar.time)
                dateInput.setText(selectedDate)
            },
            year, month, day
        )

        // Set max date to today (user must be born before today)
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun setupClickListeners() {
        // Botón principal (Login/Register)
        actionButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()
            val objective = objectiveSpinner.text.toString().trim()

            if (validateInputs(email, password, username, confirmPassword, objective, selectedDate)) {
                if (isLoginMode) {
                    loginUser(email, password)
                } else {
                    registerUser(email, password, username, selectedDate, objective)
                }
            }
        }

        // Botón para cambiar entre Login y Register
        toggleModeButton.setOnClickListener {
            isLoginMode = !isLoginMode
            clearInputs()
            updateUIMode()
        }
    }

    private fun updateUIMode() {
        if (isLoginMode) {
            // Login Mode
            tvTitle.text = "Iniciar Sesión"
            actionButton.text = "Iniciar Sesión"
            toggleModeButton.text = "¿No tienes cuenta? Regístrate"

            // Hide register-only fields
            nameTitle.visibility = View.GONE
            nameInputLayout.visibility = View.GONE
            dateTitle.visibility = View.GONE
            dateInputLayout.visibility = View.GONE
            objectiveTitle.visibility = View.GONE
            objectiveInputLayout.visibility = View.GONE
            confirmPasswordTitle.visibility = View.GONE
            confirmPasswordInputLayout.visibility = View.GONE

        } else {
            // Register Mode
            tvTitle.text = "Registrarse"
            actionButton.text = "Registrarse"
            toggleModeButton.text = "¿Ya tienes cuenta? Inicia sesión"

            // Show register-only fields
            nameTitle.visibility = View.VISIBLE
            nameInputLayout.visibility = View.VISIBLE
            dateTitle.visibility = View.VISIBLE
            dateInputLayout.visibility = View.VISIBLE
            objectiveTitle.visibility = View.VISIBLE
            objectiveInputLayout.visibility = View.VISIBLE
            confirmPasswordTitle.visibility = View.VISIBLE
            confirmPasswordInputLayout.visibility = View.VISIBLE
        }
    }

    private fun validateInputs(
        email: String,
        password: String,
        username: String,
        confirmPassword: String,
        objective: String,
        birthDate: String
    ): Boolean {

        // Clear previous errors
        clearErrors()

        var isValid = true

        // Email validation
        if (email.isEmpty()) {
            emailInput.error = "El email es requerido"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Ingrese un email válido"
            isValid = false
        }

        // Password validation
        if (password.isEmpty()) {
            passwordInput.error = "La contraseña es requerida"
            isValid = false
        } else if (password.length < 6) {
            passwordInput.error = "La contraseña debe tener al menos 6 caracteres"
            isValid = false
        }

        // Register mode validations
        if (!isLoginMode) {
            // Username validation
            if (username.isEmpty()) {
                usernameInput.error = "El nombre completo es requerido"
                isValid = false
            } else if (username.length < 3) {
                usernameInput.error = "El nombre debe tener al menos 3 caracteres"
                isValid = false
            }

            // Birth date validation
            if (birthDate.isEmpty()) {
                dateInput.error = "La fecha de nacimiento es requerida"
                isValid = false
            } else if (!isValidAge(birthDate)) {
                dateInput.error = "Debe ser mayor de 10 años para registrarse"
                isValid = false
            }

            // Objective validation
            if (objective.isEmpty()) {
                objectiveSpinner.error = "Debe seleccionar un objetivo"
                isValid = false
            }

            // Confirm password validation
            if (confirmPassword.isEmpty()) {
                confirmPasswordInput.error = "Debe confirmar la contraseña"
                isValid = false
            } else if (password != confirmPassword) {
                confirmPasswordInput.error = "Las contraseñas no coinciden"
                isValid = false
            }
        }

        return isValid
    }

    private fun isValidAge(birthDate: String): Boolean {
        return try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val birth = dateFormat.parse(birthDate)
            val today = Calendar.getInstance()
            val birthCalendar = Calendar.getInstance()
            birthCalendar.time = birth!!

            val age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)

            if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                age - 1 >= 10
            } else {
                age >= 10
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun clearErrors() {
        emailInput.error = null
        passwordInput.error = null
        usernameInput.error = null
        dateInput.error = null
        objectiveSpinner.error = null
        confirmPasswordInput.error = null
    }

    private fun clearInputs() {
        emailInput.setText("")
        passwordInput.setText("")
        usernameInput.setText("")
        dateInput.setText("")
        objectiveSpinner.setText("")
        confirmPasswordInput.setText("")
        selectedDate = ""
        clearErrors()
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

    private fun registerUser(email: String, password: String, username: String, birthDate: String, objective: String) {
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
                            saveUserToFirestore(
                                email = email,
                                username = username,
                                birthDate = birthDate,
                                objective = objective,
                                onSuccess = {
                                    Toast.makeText(context, "Registro exitoso", Toast.LENGTH_SHORT).show()
                                    navigateToMainApp()
                                },
                                onError = { e ->
                                    Toast.makeText(context, "Error al guardar datos: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                } else {
                    Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveUserToPreferences(
        email: String,
        username: String,
        birthDate: String = "",
        objective: String = "",
        isLoggedIn: Boolean
    ) {
        with(sharedPreferences.edit()) {
            putString("user_email", email)
            putString("user_name", username)
            putString("user_birth_date", birthDate)
            putString("user_objective", objective)
            putBoolean("is_logged_in", isLoggedIn)
            apply()
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(requireContext(), ItemDetailHostActivity::class.java)
        startActivity(intent)
        activity?.finish()
    }

    private fun saveUserToFirestore(
        email: String,
        username: String,
        birthDate: String = "",
        objective: String = "",
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val userData = hashMapOf(
            "uid" to user.uid,
            "email" to email,
            "nombre" to username,
            "fechaNacimiento" to birthDate,
            "objetivo" to objective
        )

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // Crear el documento del usuario
        db.collection("usuarios").document(user.uid).set(userData)
            .addOnSuccessListener {
                // Solo crear el desafío inicial si NO eligió "Otro"
                if (objective != "Otro") {
                    createInitialChallenge(db, user.uid, objective, onSuccess, onError)
                } else {
                    onSuccess()
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    private fun createInitialChallenge(
        db: com.google.firebase.firestore.FirebaseFirestore,
        userId: String,
        objective: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val challenge = getInitialChallengeForObjective(objective)
        val challengesRef = db.collection("usuarios").document(userId).collection("desafios")

        challengesRef.add(challenge)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    private fun getInitialChallengeForObjective(objective: String): HashMap<String, Any> {
        val currentTime = com.google.firebase.Timestamp.now()

        return when (objective) {
            "Fitness" -> hashMapOf(
                "nombre" to "Rutina de Fitness",
                "descripcion" to "Desafío de 30 días para mejorar tu condición física",
                "tipo" to "fitness",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "habitos" to listOf(
                    mapOf("nombre" to "30 minutos de ejercicio", "completado" to false),
                    mapOf("nombre" to "10 flexiones", "completado" to false),
                    mapOf("nombre" to "15 sentadillas", "completado" to false),
                    mapOf("nombre" to "Caminar 5000 pasos", "completado" to false),
                    mapOf("nombre" to "Estiramientos", "completado" to false)
                )
            )

            "Lectura" -> hashMapOf(
                "nombre" to "Hábito de Lectura",
                "descripcion" to "Desafío de 21 días para crear el hábito de lectura diaria",
                "tipo" to "lectura",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 21,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "habitos" to listOf(
                    mapOf("nombre" to "Leer 20 minutos", "completado" to false),
                    mapOf("nombre" to "Elegir libro del día", "completado" to false),
                    mapOf("nombre" to "Tomar notas importantes", "completado" to false),
                    mapOf("nombre" to "Buscar lugar cómodo para leer", "completado" to false),
                    mapOf("nombre" to "Reflexionar sobre lo leído", "completado" to false)
                )
            )

            "Mindfulness" -> hashMapOf(
                "nombre" to "Mindfulness Diario",
                "descripcion" to "Desafío de 30 días de meditación y mindfulness",
                "tipo" to "mindfulness",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "habitos" to listOf(
                    mapOf("nombre" to "10 minutos de meditación", "completado" to false),
                    mapOf("nombre" to "Respiración consciente", "completado" to false),
                    mapOf("nombre" to "Gratitud del día", "completado" to false),
                    mapOf("nombre" to "Momento presente", "completado" to false),
                    mapOf("nombre" to "Reflexión personal", "completado" to false)
                )
            )

            "Hidratación" -> hashMapOf(
                "nombre" to "Hidratación Saludable",
                "descripcion" to "Desafío de 15 días para mejorar tus hábitos de hidratación",
                "tipo" to "hidratacion",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 15,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "habitos" to listOf(
                    mapOf("nombre" to "2 litros de agua", "completado" to false),
                    mapOf("nombre" to "Vaso al despertar", "completado" to false),
                    mapOf("nombre" to "Agua antes de cada comida", "completado" to false),
                    mapOf("nombre" to "Evitar bebidas azucaradas", "completado" to false),
                    mapOf("nombre" to "Infusión nocturna", "completado" to false)
                )
            )

            else -> hashMapOf(
                "nombre" to "Desafío Personal",
                "descripcion" to "Define tu propio desafío personal",
                "tipo" to "general",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 7,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 1,
                "habitos" to listOf(
                    mapOf("nombre" to "Define tu meta", "completado" to false)
                )
            )
        }
    }
}