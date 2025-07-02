package com.example.trabajointegradornativo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.flexbox.FlexboxLayout
import com.google.firebase.firestore.FirebaseFirestore

class ChallengePreviewFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var challengeTitlePreview: TextView
    private lateinit var challengeAuthorPreview: TextView
    private lateinit var challengeDescriptionPreview: TextView
    private lateinit var challengeDurationPreview: TextView
    private lateinit var habitsContainerPreview: LinearLayout
    private lateinit var tagsContainerPreview: FlexboxLayout
    private lateinit var btnClosePreview: CardView
    private lateinit var btnUseChallenge: CardView
    private var isDefaultChallenge: Boolean = false
    private var defaultChallengeType: String = ""
    private var currentChallenge: DesafioPublico? = null

    companion object {
        private const val TYPE_FITNESS = "fitness"
        private const val TYPE_READING = "lectura"
        private const val TYPE_MINDFULNESS = "mindfulness"
        private const val TYPE_HYDRATION = "hidratacion"
        private const val TYPE_CUSTOM = "personalizado"
        private const val STATUS_ACTIVE = "activo"
    }

    // Infla el layout del fragmento
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_preview_challenge, container, false)
    }

    // Inicializa vistas y carga datos al crear la vista
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        initializeViews()
        setupButtons()
        loadChallengeData()
    }

    // Inicializa las vistas del fragmento
    private fun initializeViews() {
        challengeTitlePreview = view?.findViewById(R.id.challenge_title_preview) ?: return
        challengeAuthorPreview = view?.findViewById(R.id.challenge_author_preview) ?: return
        challengeDescriptionPreview =
            view?.findViewById(R.id.challenge_description_preview) ?: return
        challengeDurationPreview = view?.findViewById(R.id.challenge_duration_preview) ?: return
        habitsContainerPreview = view?.findViewById(R.id.habits_container_preview) ?: return
        tagsContainerPreview = view?.findViewById(R.id.tags_container_preview) ?: return
        btnClosePreview = view?.findViewById(R.id.btn_close_preview) ?: return
        btnUseChallenge = view?.findViewById(R.id.btn_use_challenge) ?: return
    }

    // Configura los listeners de los botones
    private fun setupButtons() {
        btnClosePreview.setOnClickListener {
            findNavController().popBackStack()
        }

        btnUseChallenge.setOnClickListener {
            currentChallenge?.let { challenge ->
                useChallengeAsTemplate(challenge)
            }
        }
    }

    // Carga los datos del desafío desde los argumentos o Firestore
    private fun loadChallengeData() {
        arguments?.let { bundle ->
            val challengeId = bundle.getString("challengeId")
            isDefaultChallenge = bundle.getBoolean("isDefaultChallenge", false)
            defaultChallengeType = bundle.getString("defaultChallengeType", "")

            if (challengeId != null && !isDefaultChallenge) {
                loadFullChallengeFromFirestore(challengeId)
            } else {
                loadChallengeFromArguments(bundle)
            }
        }
    }

    // Obtiene el desafío completo desde Firestore por ID
    private fun loadFullChallengeFromFirestore(challengeId: String) {
        db.collection("desafiosPublicos")
            .document(challengeId)
            .get()
            .addOnSuccessListener { document ->
                try {
                    if (document.exists()) {
                        val data = document.data
                        if (data != null) {
                            val habitos =
                                (data["habitos"] as? List<Map<String, Any>>)?.map { habitMap ->
                                    Habito(
                                        completado = habitMap["completado"] as? Boolean ?: false,
                                        nombre = habitMap["nombre"] as? String ?: ""
                                    )
                                } ?: emptyList()

                            val challenge = DesafioPublico(
                                id = document.id,
                                nombre = data["nombre"] as? String ?: "",
                                autorNombre = data["autorNombre"] as? String ?: "",
                                descripcion = data["descripcion"] as? String ?: "",
                                dias = (data["dias"] as? Long)?.toInt() ?: 30,
                                etiquetas = data["etiquetas"] as? List<String> ?: emptyList(),
                                habitos = habitos,
                                fechaCreacion = data["fechaCreacion"] as? com.google.firebase.Timestamp
                            )
                            currentChallenge = challenge
                            displayChallengeData()
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.challenge_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                        findNavController().popBackStack()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_loading_challenge),
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.connection_error),
                    Toast.LENGTH_SHORT
                ).show()
                findNavController().popBackStack()
            }
    }

    // Muestra la lista de hábitos en el contenedor
    private fun displayHabits(habitos: List<Habito>) {
        habitsContainerPreview.removeAllViews()

        if (habitos.isEmpty()) {
            val noHabitsText = TextView(requireContext()).apply {
                text = getString(R.string.no_specific_habits)
                textSize = 14f
                setTextColor(resources.getColor(R.color.gray_text, null))
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            }
            habitsContainerPreview.addView(noHabitsText)
        } else {
            for (habito in habitos) {
                val habitView = createHabitPreviewItem(habito)
                habitsContainerPreview.addView(habitView)
            }
        }
    }

    // Crea la vista de un hábito para el preview
    private fun createHabitPreviewItem(habito: Habito): View {
        val habitLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            background = resources.getDrawable(R.drawable.habit_item_background, null)

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(0, 0, 0, 8.dpToPx())
            this.layoutParams = layoutParams
        }

        val textView = TextView(requireContext()).apply {
            text = habito.nombre
            textSize = 16f
            setTextColor(resources.getColor(R.color.dark_text, null))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        habitLayout.addView(textView)

        return habitLayout
    }

    // Carga los datos del desafío desde los argumentos del bundle
    private fun loadChallengeFromArguments(bundle: Bundle) {
        val challengeName = bundle.getString("challengeName")
        val challengeAuthor = bundle.getString("challengeAuthor")
        val challengeDescription = bundle.getString("challengeDescription")
        val challengeDuration = bundle.getInt("challengeDuration", 0)
        val challengeTags = bundle.getStringArrayList("challengeTags") ?: arrayListOf()
        val challengeHabitos = bundle.getStringArrayList("challengeHabits") ?: arrayListOf()

        currentChallenge = DesafioPublico(
            nombre = challengeName ?: "",
            autorNombre = challengeAuthor ?: "",
            descripcion = challengeDescription ?: "",
            dias = challengeDuration,
            etiquetas = challengeTags,
            habitos = challengeHabitos.map { habitoNombre ->
                Habito(nombre = habitoNombre)
            }
        )

        displayChallengeData()
    }

    // Muestra los datos del desafío en la interfaz
    private fun displayChallengeData() {
        currentChallenge?.let { challenge ->
            challengeTitlePreview.text = challenge.nombre
            challengeAuthorPreview.text =
                getString(R.string.author_prefix_at, challenge.autorNombre)
            challengeDescriptionPreview.text = challenge.descripcion
            challengeDurationPreview.text = getString(R.string.days_format, challenge.dias)
            displayHabits(challenge.habitos)
            displayTags(challenge.etiquetas)
        }
    }

    // Muestra las etiquetas del desafío
    private fun displayTags(etiquetas: List<String>) {
        tagsContainerPreview.removeAllViews()

        if (etiquetas.isEmpty()) {
            tagsContainerPreview.visibility = View.GONE
        } else {
            tagsContainerPreview.visibility = View.VISIBLE
            for (etiqueta in etiquetas) {
                val tagView = createTagPreviewView(etiqueta)
                tagsContainerPreview.addView(tagView)
            }
        }
    }

    // Crea la vista de una etiqueta para el preview
    private fun createTagPreviewView(tagText: String): View {
        val inflater = LayoutInflater.from(requireContext())
        val tagView = inflater.inflate(R.layout.item_tag_public, null, false)

        tagView.layoutParams = FlexboxLayout.LayoutParams(
            FlexboxLayout.LayoutParams.WRAP_CONTENT,
            FlexboxLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 8.dpToPx(), 4.dpToPx())
        }

        val tvTagText = tagView.findViewById<TextView>(R.id.tag_text_public)
        tvTagText.text = "#${tagText}"

        return tagView
    }

    // Usa el desafío como plantilla para el usuario actual
    private fun useChallengeAsTemplate(challenge: DesafioPublico) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.must_login_to_use_challenge),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (isDefaultChallenge) {
            createDefaultChallengeForUser()
        } else {
            checkIfChallengeExists(challenge) { exists ->
                if (exists) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.already_have_challenge),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    savePersonalChallenge(challenge)
                }
            }
        }
    }

    // Crea un desafío predeterminado para el usuario
    private fun createDefaultChallengeForUser() {
        Toast.makeText(requireContext(), getString(R.string.creating_challenge), Toast.LENGTH_SHORT)
            .show()

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: return

        val challengeData = getInitialChallengeForObjective(defaultChallengeType)
        val challengesRef = db.collection("usuarios").document(userId).collection("desafios")

        challengesRef.add(challengeData)
            .addOnSuccessListener { documentRef ->
                val batch = db.batch()
                val dias = challengeData["dias"] as? Int ?: 30
                val habitos = challengeData["habitos"] as? List<Map<String, Any>> ?: emptyList()

                val habitosParaDias = habitos.map { habito ->
                    mapOf(
                        "nombre" to (habito["nombre"] ?: ""),
                        "completado" to false
                    )
                }

                val fechaInicio = java.util.Calendar.getInstance()
                val dateFormat =
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

                for (i in 1..dias) {
                    val diaRef = documentRef.collection("dias").document("dia_$i")

                    val fechaDia = java.util.Calendar.getInstance().apply {
                        time = fechaInicio.time
                        add(java.util.Calendar.DAY_OF_YEAR, i - 1)
                    }

                    val dataDia = hashMapOf(
                        "dia" to i,
                        "habitos" to habitosParaDias,
                        "completado" to false,
                        "fecha_creacion" to com.google.firebase.Timestamp.now(),
                        "fechaRealizacion" to dateFormat.format(fechaDia.time)
                    )
                    batch.set(diaRef, dataDia)
                }

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.challenge_created_successfully),
                            Toast.LENGTH_LONG
                        ).show()
                        findNavController().popBackStack()
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.error_creating_challenge_days),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_creating_challenge),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // Obtiene los datos iniciales de un desafío según el objetivo
    private fun getInitialChallengeForObjective(objective: String): HashMap<String, Any> {
        val currentTime = com.google.firebase.Timestamp.now()

        val normalizedObjective = when (objective.lowercase()) {
            "fitness", "ejercicio" -> TYPE_FITNESS
            "reading", "lectura", "leitura" -> TYPE_READING
            "mindfulness", "atencion plena" -> TYPE_MINDFULNESS
            "hydration", "hidratacion", "hidratación", "hidratação" -> TYPE_HYDRATION
            else -> TYPE_CUSTOM
        }

        return when (normalizedObjective) {
            TYPE_FITNESS -> hashMapOf(
                "nombre" to getString(R.string.initial_fitness_challenge_name),
                "descripcion" to getString(R.string.fitness_challenge_description_text),
                "tipo" to TYPE_FITNESS,
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to STATUS_ACTIVE,
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.walk_30_min_habit), "completado" to false),
                    mapOf(
                        "nombre" to getString(R.string.stretch_15_min_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.drink_2l_water_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.sleep_7_8_hours_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.eat_vegetables_habit),
                        "completado" to false
                    )
                )
            )

            TYPE_READING -> hashMapOf(
                "nombre" to getString(R.string.initial_reading_challenge_name),
                "descripcion" to getString(R.string.reading_challenge_description_text),
                "tipo" to TYPE_READING,
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 21,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 4,
                "estado" to STATUS_ACTIVE,
                "habitos" to listOf(
                    mapOf(
                        "nombre" to getString(R.string.read_20_pages_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.take_reading_notes_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.reflect_on_reading_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.read_quiet_place_habit),
                        "completado" to false
                    )
                )
            )

            TYPE_MINDFULNESS -> hashMapOf(
                "nombre" to getString(R.string.mindfulness_challenge_name),
                "descripcion" to getString(R.string.mindfulness_challenge_description_text),
                "tipo" to TYPE_MINDFULNESS,
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to STATUS_ACTIVE,
                "habitos" to listOf(
                    mapOf(
                        "nombre" to getString(R.string.meditate_10_min_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.conscious_breathing_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.gratitude_journal_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.conscious_pause_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.mindful_observation_habit),
                        "completado" to false
                    )
                )
            )

            TYPE_HYDRATION -> hashMapOf(
                "nombre" to getString(R.string.hydration_challenge_name),
                "descripcion" to getString(R.string.hydration_challenge_description_text),
                "tipo" to TYPE_HYDRATION,
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 15,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to STATUS_ACTIVE,
                "habitos" to listOf(
                    mapOf(
                        "nombre" to getString(R.string.drink_2l_water_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.carry_water_bottle_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.drink_water_before_meals_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.avoid_sugary_drinks_habit),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.drink_water_on_wake_habit),
                        "completado" to false
                    )
                )
            )

            else -> hashMapOf(
                "nombre" to getString(R.string.custom_challenge_name),
                "descripcion" to getString(R.string.custom_challenge_description_text),
                "tipo" to TYPE_CUSTOM,
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 3,
                "estado" to STATUS_ACTIVE,
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.custom_habit_1), "completado" to false),
                    mapOf("nombre" to getString(R.string.custom_habit_2), "completado" to false),
                    mapOf("nombre" to getString(R.string.custom_habit_3), "completado" to false)
                )
            )
        }
    }

    // Verifica si el usuario ya tiene un desafío con el mismo nombre
    private fun checkIfChallengeExists(challenge: DesafioPublico, onResult: (Boolean) -> Unit) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            onResult(false)
            return
        }

        val userId = currentUser.uid

        db.collection("usuarios")
            .document(userId)
            .collection("desafios")
            .whereEqualTo("nombre", challenge.nombre)
            .get()
            .addOnSuccessListener { documents ->
                onResult(!documents.isEmpty)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    // Guarda un desafío personalizado para el usuario
    private fun savePersonalChallenge(challenge: DesafioPublico) {
        Toast.makeText(requireContext(), getString(R.string.saving_challenge), Toast.LENGTH_SHORT)
            .show()

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: return

        val habitosList = challenge.habitos.map { habito ->
            hashMapOf("nombre" to habito.nombre)
        }

        val tipo = if (challenge.etiquetas.isNotEmpty()) {
            challenge.etiquetas.first().lowercase()
        } else {
            "general"
        }

        val personalChallenge = hashMapOf(
            "nombre" to challenge.nombre,
            "descripcion" to challenge.descripcion,
            "dias" to challenge.dias,
            "habitos" to habitosList,
            "fechaCreacion" to com.google.firebase.Timestamp.now(),
            "fechaInicio" to null,
            "estado" to STATUS_ACTIVE,
            "etiquetas" to challenge.etiquetas,
            "completado" to false,
            "completados" to 0,
            "tipo" to tipo,
            "totalHabitos" to challenge.habitos.size
        )

        db.collection("usuarios")
            .document(userId)
            .collection("desafios")
            .add(personalChallenge)
            .addOnSuccessListener { documentReference ->
                createDaysForChallenge(documentReference.id, challenge.dias, challenge.habitos)
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_saving_challenge_retry),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // Crea los días del desafío en la base de datos
    private fun createDaysForChallenge(
        desafioId: String,
        duracionDias: Int,
        habitos: List<Habito>
    ) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: return

        val batch = db.batch()
        val diasCollection = db.collection("usuarios")
            .document(userId)
            .collection("desafios")
            .document(desafioId)
            .collection("dias")

        val calendar = java.util.Calendar.getInstance()

        for (i in 1..duracionDias) {
            val habitosDia = habitos.map { habito ->
                hashMapOf(
                    "nombre" to habito.nombre,
                    "completado" to false
                )
            }

            val fechaRealizacion = String.format(
                "%04d-%02d-%02d",
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )

            val diaData = hashMapOf(
                "dia" to i,
                "fechaRealizacion" to fechaRealizacion,
                "fecha_creacion" to com.google.firebase.Timestamp.now(),
                "completado" to false,
                "habitos" to habitosDia
            )
            val diaDocRef = diasCollection.document("dia_$i")
            batch.set(diaDocRef, diaData)

            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.challenge_added_with_days, duracionDias),
                    Toast.LENGTH_LONG
                ).show()
                findNavController().popBackStack()
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_creating_days_retry),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // Convierte dp a píxeles
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}