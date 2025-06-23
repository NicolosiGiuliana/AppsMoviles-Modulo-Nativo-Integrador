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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_preview_challenge, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        initializeViews()
        setupButtons()
        loadChallengeData()
    }

    private fun initializeViews() {
        challengeTitlePreview = view?.findViewById(R.id.challenge_title_preview) ?: return
        challengeAuthorPreview = view?.findViewById(R.id.challenge_author_preview) ?: return
        challengeDescriptionPreview = view?.findViewById(R.id.challenge_description_preview) ?: return
        challengeDurationPreview = view?.findViewById(R.id.challenge_duration_preview) ?: return
        habitsContainerPreview = view?.findViewById(R.id.habits_container_preview) ?: return
        tagsContainerPreview = view?.findViewById(R.id.tags_container_preview) ?: return
        btnClosePreview = view?.findViewById(R.id.btn_close_preview) ?: return
        btnUseChallenge = view?.findViewById(R.id.btn_use_challenge) ?: return

    }

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

    private fun loadFullChallengeFromFirestore(challengeId: String) {
        db.collection("desafiosPublicos")
            .document(challengeId)
            .get()
            .addOnSuccessListener { document ->
                try {
                    if (document.exists()) {
                        val data = document.data
                        if (data != null) {
                            val habitos = (data["habitos"] as? List<Map<String, Any>>)?.map { habitMap ->
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
                        Toast.makeText(requireContext(), "Desafío no encontrado", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error al cargar el desafío", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
    }

    private fun displayHabits(habitos: List<Habito>) {
        habitsContainerPreview.removeAllViews()

        if (habitos.isEmpty()) {
            val noHabitsText = TextView(requireContext()).apply {
                text = "Este desafío no tiene hábitos específicos definidos"
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

    private fun displayChallengeData() {
        currentChallenge?.let { challenge ->
            challengeTitlePreview.text = challenge.nombre
            challengeAuthorPreview.text = "Por @${challenge.autorNombre}"
            challengeDescriptionPreview.text = challenge.descripcion
            challengeDurationPreview.text = "${challenge.dias} días"
            displayHabits(challenge.habitos)
            displayTags(challenge.etiquetas)
        }
    }

    private fun displayHabitNames(habitos: List<String>) {
        habitsContainerPreview.removeAllViews()

        if (habitos.isEmpty()) {
            val noHabitsText = TextView(requireContext()).apply {
                text = "Este desafío no tiene hábitos específicos definidos"
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


    private fun createHabitPreviewItem(habitText: String): View {
        val inflater = LayoutInflater.from(requireContext())

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

        val iconView = android.widget.ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_circle_empty)
            setColorFilter(resources.getColor(R.color.gray_text, null))
            layoutParams = LinearLayout.LayoutParams(
                24.dpToPx(),
                24.dpToPx()
            ).apply {
                setMargins(0, 0, 12.dpToPx(), 0)
            }
        }

        val textView = TextView(requireContext()).apply {
            text = habitText
            textSize = 16f
            setTextColor(resources.getColor(R.color.dark_text, null))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        habitLayout.addView(iconView)
        habitLayout.addView(textView)

        return habitLayout
    }

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

    private fun useChallengeAsTemplate(challenge: DesafioPublico) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(requireContext(), "Debes iniciar sesión para usar este desafío", Toast.LENGTH_LONG).show()
            return
        }

        if (isDefaultChallenge) {
            createDefaultChallengeForUser()
        } else {
            checkIfChallengeExists(challenge) { exists ->
                if (exists) {
                    Toast.makeText(
                        requireContext(),
                        "Ya tienes este desafío en tu colección",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    savePersonalChallenge(challenge)
                }
            }
        }
    }

    private fun createDefaultChallengeForUser() {
        Toast.makeText(requireContext(), "Creando desafío...", Toast.LENGTH_SHORT).show()

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
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

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
                            "¡Desafío '${challengeData["nombre"]}' creado exitosamente!",
                            Toast.LENGTH_LONG
                        ).show()
                        findNavController().popBackStack()
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        Toast.makeText(
                            requireContext(),
                            "Error al crear los días del desafío.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Error al crear el desafío.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun getInitialChallengeForObjective(objective: String): HashMap<String, Any> {
        val currentTime = com.google.firebase.Timestamp.now()
        return when (objective) {
            "fitness" -> hashMapOf(
                "nombre" to "Desafío de Fitness Inicial",
                "descripcion" to "Mejora tu condición física con hábitos diarios",
                "tipo" to "fitness",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to "activo",
                "habitos" to listOf(
                    mapOf("nombre" to "Caminar 30 minutos", "completado" to false),
                    mapOf("nombre" to "Estirar 15 minutos", "completado" to false),
                    mapOf("nombre" to "Beber 2 litros de agua", "completado" to false),
                    mapOf("nombre" to "Dormir 7-8 horas", "completado" to false),
                    mapOf("nombre" to "Comer una porción de vegetales", "completado" to false)
                )
            )
            "lectura" -> hashMapOf(
                "nombre" to "Desafío de Lectura Inicial",
                "descripcion" to "Desarrolla el hábito de la lectura diaria",
                "tipo" to "lectura",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 21,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 4,
                "estado" to "activo",
                "habitos" to listOf(
                    mapOf("nombre" to "Leer 20 páginas", "completado" to false),
                    mapOf("nombre" to "Tomar notas de lectura", "completado" to false),
                    mapOf("nombre" to "Reflexionar sobre lo leído", "completado" to false),
                    mapOf("nombre" to "Leer en un lugar tranquilo", "completado" to false)
                )
            )
            "mindfulness" -> hashMapOf(
                "nombre" to "Desafío de Mindfulness",
                "descripcion" to "Cultiva la atención plena y reduce el estrés",
                "tipo" to "mindfulness",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to "activo",
                "habitos" to listOf(
                    mapOf("nombre" to "Meditar 10 minutos", "completado" to false),
                    mapOf("nombre" to "Practicar respiración consciente", "completado" to false),
                    mapOf("nombre" to "Escribir diario de gratitud", "completado" to false),
                    mapOf("nombre" to "Hacer una pausa consciente", "completado" to false),
                    mapOf("nombre" to "Observar el entorno mindfully", "completado" to false)
                )
            )
            "hidratacion" -> hashMapOf(
                "nombre" to "Desafío de Hidratación",
                "descripcion" to "Mantente hidratado para una mejor salud",
                "tipo" to "hidratacion",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 15,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to "activo",
                "habitos" to listOf(
                    mapOf("nombre" to "Beber 2 litros de agua", "completado" to false),
                    mapOf("nombre" to "Llevar botella de agua", "completado" to false),
                    mapOf("nombre" to "Beber agua antes de comidas", "completado" to false),
                    mapOf("nombre" to "Evitar bebidas azucaradas", "completado" to false),
                    mapOf("nombre" to "Beber agua al despertar", "completado" to false)
                )
            )
            else -> hashMapOf(
                "nombre" to "Desafío Personalizado",
                "descripcion" to "Un desafío adaptado a tus necesidades",
                "tipo" to "personalizado",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 3,
                "estado" to "activo",
                "habitos" to listOf(
                    mapOf("nombre" to "Hábito personalizado 1", "completado" to false),
                    mapOf("nombre" to "Hábito personalizado 2", "completado" to false),
                    mapOf("nombre" to "Hábito personalizado 3", "completado" to false)
                )
            )
        }
    }

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

    private fun savePersonalChallenge(challenge: DesafioPublico) {
        Toast.makeText(requireContext(), "Guardando desafío...", Toast.LENGTH_SHORT).show()

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
            "estado" to "activo",
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
                    "Error al guardar el desafío. Inténtalo de nuevo.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun createDaysForChallenge(desafioId: String, duracionDias: Int, habitos: List<Habito>) {
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
                    "¡Desafío agregado a tus desafíos con $duracionDias días creados!",
                    Toast.LENGTH_LONG
                ).show()
                findNavController().popBackStack()
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Error al crear los días del desafío. Inténtalo de nuevo.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}