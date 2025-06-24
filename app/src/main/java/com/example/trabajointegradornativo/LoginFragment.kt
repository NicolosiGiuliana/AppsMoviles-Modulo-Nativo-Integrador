package com.example.trabajointegradornativo

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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
    private var isLoginMode = true

    private lateinit var tvTitle: TextView
    private lateinit var usernameInput: EditText
    private lateinit var nameInputLayout: TextInputLayout

    private lateinit var dateTitle: TextView
    private lateinit var dateInput: EditText
    private lateinit var dateInputLayout: TextInputLayout
    private lateinit var objectiveTitle: TextView
    private lateinit var objectiveSpinner: AutoCompleteTextView
    private lateinit var objectiveInputLayout: TextInputLayout
    private lateinit var confirmPasswordTitle: TextView
    private lateinit var confirmPasswordInput: EditText
    private lateinit var confirmPasswordInputLayout: TextInputLayout

    private lateinit var emailTitle: TextView
    private lateinit var nameTitle: TextView
    private lateinit var passwordTitle: TextView
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var actionButton: Button
    private lateinit var toggleModeButton: Button

    private var selectedDate: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        auth = FirebaseAuth.getInstance()

        sharedPreferences =
            requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        initViews(view)
        setupUI()
        setupClickListeners()

        return view
    }

    private fun initViews(view: View) {
        tvTitle = view.findViewById(R.id.tvTitle)

        nameTitle = view.findViewById(R.id.nameTitle)
        dateTitle = view.findViewById(R.id.dateTitle)
        objectiveTitle = view.findViewById(R.id.objectiveTitle)
        emailTitle = view.findViewById(R.id.emailTitle)
        passwordTitle = view.findViewById(R.id.passwordTitle)
        confirmPasswordTitle = view.findViewById(R.id.confirmPasswordTitle)

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

        actionButton = view.findViewById(R.id.actionButton)
        toggleModeButton = view.findViewById(R.id.toggleModeButton)
    }

    private fun setupUI() {
        setupObjectiveSpinner()
        setupDatePicker()
        setupPasswordConfirmation()
        updateUIMode()
    }

    private fun setupDatePicker() {
        dateInput.setOnClickListener {
            showDatePicker()
        }
        dateInput.isFocusable = false
        dateInput.isClickable = true
    }

    private fun setupPasswordConfirmation() {
        confirmPasswordInput.setOnLongClickListener { true }
        confirmPasswordInput.setTextIsSelectable(false)
        confirmPasswordInput.customSelectionActionModeCallback =
            object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(
                    mode: android.view.ActionMode?,
                    menu: android.view.Menu?
                ): Boolean = false

                override fun onPrepareActionMode(
                    mode: android.view.ActionMode?,
                    menu: android.view.Menu?
                ): Boolean = false

                override fun onActionItemClicked(
                    mode: android.view.ActionMode?,
                    item: android.view.MenuItem?
                ): Boolean = false

                override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
            }
    }

    private fun setupObjectiveSpinner() {
        val localizedObjectives = arrayOf(
            getString(R.string.fitness),
            getString(R.string.reading_objective),
            getString(R.string.mindfulness_objective),
            getString(R.string.hydration_objective),
            getString(R.string.other_objective)
        )
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            localizedObjectives
        )
        objectiveSpinner.setAdapter(adapter)
        objectiveSpinner.setOnClickListener { objectiveSpinner.showDropDown() }
    }

    private fun setupClickListeners() {
        actionButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()
            val objective = objectiveSpinner.text.toString().trim()

            if (validateInputs(
                    email,
                    password,
                    username,
                    confirmPassword,
                    objective,
                    selectedDate
                )
            ) {
                if (isLoginMode) {
                    loginUser(email, password)
                } else {
                    registerUser(email, password, username, selectedDate, objective)
                }
            }
        }

        toggleModeButton.setOnClickListener {
            isLoginMode = !isLoginMode
            clearInputs()
            updateUIMode()
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

        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun updateUIMode() {
        if (isLoginMode) {
            tvTitle.text = getString(R.string.login)
            actionButton.text = getString(R.string.login)
            toggleModeButton.text = getString(R.string.no_account)

            nameTitle.visibility = View.GONE
            nameInputLayout.visibility = View.GONE
            dateTitle.visibility = View.GONE
            dateInputLayout.visibility = View.GONE
            objectiveTitle.visibility = View.GONE
            objectiveInputLayout.visibility = View.GONE
            confirmPasswordTitle.visibility = View.GONE
            confirmPasswordInputLayout.visibility = View.GONE

        } else {
            tvTitle.text = getString(R.string.register)
            actionButton.text = getString(R.string.register)
            toggleModeButton.text = getString(R.string.have_account)

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

        clearErrors()

        var isValid = true

        if (email.isEmpty()) {
            emailInput.error = getString(R.string.email_required)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = getString(R.string.enter_valid_email)
            isValid = false
        }

        if (password.isEmpty()) {
            passwordInput.error = getString(R.string.password_required)
            isValid = false
        } else if (password.length < 6) {
            passwordInput.error = getString(R.string.password_min_length)
            isValid = false
        }

        if (!isLoginMode) {
            if (username.isEmpty()) {
                usernameInput.error = getString(R.string.full_name_required)
                isValid = false
            } else if (username.length < 3) {
                usernameInput.error = getString(R.string.name_min_length)
                isValid = false
            }

            if (birthDate.isEmpty()) {
                dateInput.error = getString(R.string.birth_date_required)
                isValid = false
            } else if (!isValidAge(birthDate)) {
                dateInput.error = getString(R.string.age_requirement)
                isValid = false
            }

            if (objective.isEmpty()) {
                objectiveSpinner.error = getString(R.string.objective_required)
                isValid = false
            }

            if (confirmPassword.isEmpty()) {
                confirmPasswordInput.error = getString(R.string.confirm_password_required)
                isValid = false
            } else if (password != confirmPassword) {
                confirmPasswordInput.error = getString(R.string.passwords_dont_match)
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

                    saveUserToPreferences(
                        email = email,
                        username = user?.displayName ?: getString(R.string.default_name),
                        isLoggedIn = true
                    )

                    Toast.makeText(
                        context,
                        getString(R.string.login_successful),
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMainApp()
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.login_error, task.exception?.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

    }

    private fun registerUser(
        email: String,
        password: String,
        username: String,
        birthDate: String,
        objective: String
    ) {
        actionButton.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                actionButton.isEnabled = true

                if (task.isSuccessful) {
                    val user = auth.currentUser

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
                                    Toast.makeText(
                                        context,
                                        getString(R.string.registration_successful),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navigateToMainApp()
                                },
                                onError = { e ->
                                    Toast.makeText(
                                        context,
                                        getString(R.string.error_saving_data, e.message ?: ""),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.registration_error, task.exception?.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
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
        try {
            val intent = Intent(requireContext(), MainActivity::class.java)
            startActivity(intent)
            activity?.finish()
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.navigation_successful), Toast.LENGTH_SHORT)
                .show()
        }
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

        db.collection("usuarios").document(user.uid).set(userData)
            .addOnSuccessListener {
                if (objective != getString(R.string.other_objective)) {
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
            .addOnSuccessListener { documentRef ->

                val batch = db.batch()
                val dias = challenge["dias"] as? Int ?: 30
                val habitos = challenge["habitos"] as? List<Map<String, Any>> ?: emptyList()

                val habitosParaDias = habitos.map { habito ->
                    mapOf(
                        "nombre" to (habito["nombre"] ?: ""),
                        "completado" to false
                    )
                }

                for (i in 1..dias) {
                    val diaRef = documentRef.collection("dias").document("dia_$i")
                    val dataDia = hashMapOf(
                        "dia" to i,
                        "habitos" to habitosParaDias,
                        "completado" to false,
                        "fecha_creacion" to com.google.firebase.Timestamp.now()
                    )
                    batch.set(diaRef, dataDia)
                }

                batch.commit()
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        onError(e)
                    }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    private fun getInitialChallengeForObjective(objective: String): Map<String, Any> {
        return when (objective) {
            getString(R.string.fitness) -> mapOf(
                "nombre" to getString(R.string.initial_fitness_challenge),
                "descripcion" to getString(R.string.fitness_challenge_description),
                "dias" to 30,
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.walk_30_minutes)),
                    mapOf("nombre" to getString(R.string.stretch_15_minutes)),
                    mapOf("nombre" to getString(R.string.drink_2_liters_water)),
                    mapOf("nombre" to getString(R.string.sleep_7_8_hours)),
                    mapOf("nombre" to getString(R.string.eat_vegetables_portion))
                ),
                "totalHabitos" to 5,
                "estado" to "activo",
                "progreso" to 0
            )

            getString(R.string.reading_objective) -> mapOf(
                "nombre" to getString(R.string.initial_reading_challenge),
                "descripcion" to getString(R.string.reading_challenge_description),
                "dias" to 30,
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.read_20_pages)),
                    mapOf("nombre" to getString(R.string.take_reading_notes)),
                    mapOf("nombre" to getString(R.string.reflect_on_reading)),
                    mapOf("nombre" to getString(R.string.share_learned)),
                    mapOf("nombre" to getString(R.string.read_quiet_place))
                ),
                "totalHabitos" to 5,
                "estado" to "activo",
                "progreso" to 0
            )

            getString(R.string.mindfulness_objective) -> mapOf(
                "nombre" to getString(R.string.initial_mindfulness_challenge),
                "descripcion" to getString(R.string.mindfulness_challenge_description),
                "dias" to 30,
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.meditate_10_minutes)),
                    mapOf("nombre" to getString(R.string.practice_conscious_breathing)),
                    mapOf("nombre" to getString(R.string.write_gratitude_journal)),
                    mapOf("nombre" to getString(R.string.take_conscious_pause)),
                    mapOf("nombre" to getString(R.string.observe_environment))
                ),
                "totalHabitos" to 5,
                "estado" to "activo",
                "progreso" to 0
            )

            getString(R.string.hydration_objective) -> mapOf(
                "nombre" to getString(R.string.initial_hydration_challenge),
                "descripcion" to getString(R.string.hydration_challenge_description),
                "dias" to 30,
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.drink_2_liters_water)),
                    mapOf("nombre" to getString(R.string.carry_water_bottle)),
                    mapOf("nombre" to getString(R.string.drink_water_wake_up)),
                    mapOf("nombre" to getString(R.string.drink_water_before_meals)),
                    mapOf("nombre" to getString(R.string.avoid_sugary_drinks))
                ),
                "totalHabitos" to 5,
                "estado" to "activo",
                "progreso" to 0
            )

            else -> mapOf(
                "nombre" to getString(R.string.initial_generic_challenge),
                "descripcion" to getString(R.string.generic_challenge_description),
                "dias" to 30,
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.habit_1)),
                    mapOf("nombre" to getString(R.string.habit_2)),
                    mapOf("nombre" to getString(R.string.habit_3)),
                    mapOf("nombre" to getString(R.string.habit_4)),
                    mapOf("nombre" to getString(R.string.habit_5))
                ),
                "totalHabitos" to 5,
                "estado" to "activo",
                "progreso" to 0
            )
        }
    }
}