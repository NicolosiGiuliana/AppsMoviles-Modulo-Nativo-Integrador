package com.example.trabajointegradornativo


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
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
        // Obtener el ID del desafío desde los argumentos
        arguments?.let { bundle ->
            val challengeId = bundle.getString("challengeId")

            if (challengeId != null) {
                loadFullChallengeFromFirestore(challengeId)
            } else {
                // Fallback: usar los datos pasados directamente (compatibilidad con código anterior)
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

        // Ícono del hábito (círculo vacío)
//        val iconView = android.widget.ImageView(requireContext()).apply {
//            setImageResource(R.drawable.ic_circle_empty)
//            setColorFilter(resources.getColor(R.color.gray_text, null))
//            layoutParams = LinearLayout.LayoutParams(
//                24.dpToPx(),
//                24.dpToPx()
//            ).apply {
//                setMargins(0, 0, 12.dpToPx(), 0)
//            }
//        }

        // Texto del hábito - SOLO EL NOMBRE
        val textView = TextView(requireContext()).apply {
            text = habito.nombre  // Solo el nombre, sin emojis
            textSize = 16f
            setTextColor(resources.getColor(R.color.dark_text, null))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

//        habitLayout.addView(iconView)
        habitLayout.addView(textView)

        return habitLayout
    }

    private fun loadChallengeFromArguments(bundle: Bundle) {
        // Método de respaldo para mantener compatibilidad
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

        // Crear un LinearLayout para el hábito
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

        // Ícono del hábito (círculo sin check)
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

        // Texto del hábito
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

        // Configurar los LayoutParams para FlexboxLayout
        tagView.layoutParams = FlexboxLayout.LayoutParams(
            FlexboxLayout.LayoutParams.WRAP_CONTENT,
            FlexboxLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 8.dpToPx(), 4.dpToPx())
        }

        // Configurar el texto
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

        // Primero verificar si ya existe el desafío
        checkIfChallengeExists(challenge) { exists ->
            if (exists) {
                Toast.makeText(
                    requireContext(),
                    "Ya tienes este desafío en tu colección",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Si no existe, guardarlo
                savePersonalChallenge(challenge)
            }
        }
    }

    // Método adicional para verificar si el usuario ya tiene este desafío
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

        // Convertir los hábitos al formato Habit de tu estructura
        val habitosList = challenge.habitos.map { habito ->
            hashMapOf("nombre" to habito.nombre)
        }

        // Determinar el tipo basado en las etiquetas o usar un valor por defecto
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
            "fechaInicio" to null, // null inicialmente hasta que el usuario lo inicie
            "estado" to "inactivo", // El usuario debe activarlo manualmente
            "completado" to false,
            "completados" to 0, // Ningún hábito completado inicialmente
            "tipo" to tipo,
            "totalHabitos" to challenge.habitos.size
        )

        db.collection("usuarios")
            .document(userId)
            .collection("desafios")
            .add(personalChallenge)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(
                    requireContext(),
                    "¡Desafío '${challenge.nombre}' agregado a tus desafíos!",
                    Toast.LENGTH_LONG
                ).show()
                findNavController().popBackStack()
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





    // Función auxiliar para convertir dp a px
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}