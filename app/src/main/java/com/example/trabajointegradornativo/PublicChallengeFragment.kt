package com.example.trabajointegradornativo

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PublicChallengeFragment: Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var challengesContainer: LinearLayout
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var searchEditText: EditText

    private var allChallenges = mutableListOf<DesafioPublico>()
    private var filteredChallenges = mutableListOf<DesafioPublico>()

    companion object {
        private const val TAG = "PublicChallengeFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        return inflater.inflate(R.layout.fragment_public_challenge, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        initializeViews()
        setupFirestore()
        setupBottomNavigation()
        setupSearchFunctionality()
        loadPublicChallenges()
    }

    private fun initializeViews() {
        Log.d(TAG, "Initializing views...")
        challengesContainer = view?.findViewById(R.id.challenges_container) ?: return
        loadingProgress = view?.findViewById(R.id.loading_progress) ?: return
        emptyState = view?.findViewById(R.id.empty_state) ?: return
        searchEditText = view?.findViewById(R.id.search_edit_text) ?: return
        Log.d(TAG, "Views initialized successfully")
    }

    private fun setupFirestore() {
        Log.d(TAG, "Setting up Firestore...")
        db = FirebaseFirestore.getInstance()
        Log.d(TAG, "Firestore instance created: ${db != null}")
    }

    private fun setupSearchFunctionality() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                Log.d(TAG, "Search query changed: '$s'")
                filterChallenges(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterChallenges(query: String) {
        Log.d(TAG, "Filtering challenges with query: '$query'")
        Log.d(TAG, "Total challenges before filter: ${allChallenges.size}")

        if (query.isBlank()) {
            filteredChallenges.clear()
            filteredChallenges.addAll(allChallenges)
        } else {
            filteredChallenges = allChallenges.filter { challenge ->
                challenge.nombre.contains(query, ignoreCase = true) ||
                        challenge.autorNombre.contains(query, ignoreCase = true) ||
                        challenge.descripcion.contains(query, ignoreCase = true) ||
                        challenge.etiquetas.any { it.contains(query, ignoreCase = true) }
            }.toMutableList()
        }

        Log.d(TAG, "Filtered challenges count: ${filteredChallenges.size}")
        displayChallenges()
    }

    private fun loadPublicChallenges() {
        Log.d(TAG, "Starting to load public challenges...")
        showLoading(true)

        // Log de la consulta que se va a ejecutar
        Log.d(TAG, "Querying collection: 'desafiosPublicos'")
        Log.d(TAG, "Query order: fechaCreacion DESC")

        db.collection("desafiosPublicos")
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Firestore query SUCCESS")
                Log.d(TAG, "Documents received: ${documents.size()}")

                allChallenges.clear()
                filteredChallenges.clear()

                var totalCount = 0
                var errorCount = 0

                for ((index, document) in documents.withIndex()) {
                    Log.d(TAG, "Processing document $index: ${document.id}")

                    try {
                        val challenge = document.toObject(DesafioPublico::class.java)

                        // Log detallado de cada desafío
                        Log.d(TAG, "Challenge parsed - ID: ${document.id}")
                        Log.d(TAG, "  - Nombre: '${challenge.nombre}'")
                        Log.d(TAG, "  - AutorNombre: '${challenge.autorNombre}'")
                        Log.d(TAG, "  - Descripción: '${challenge.descripcion}'")
                        Log.d(TAG, "  - Días: ${challenge.dias}")
                        Log.d(TAG, "  - Etiquetas: ${challenge.etiquetas}")
                        Log.d(TAG, "  - Activo: ${challenge.activo}")
                        Log.d(TAG, "  - Estado: '${challenge.estado}'")

                        // CAMBIO: Agregamos TODOS los desafíos, sin filtrar por activo
                        challenge.id = document.id
                        allChallenges.add(challenge)
                        totalCount++
                        Log.d(TAG, "Challenge added to list: ${challenge.nombre} (Active: ${challenge.activo})")

                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "Error parsing challenge ${document.id}: ${e.message}")
                        Log.e(TAG, "Document data: ${document.data}")
                        e.printStackTrace()
                    }
                }

                // Resumen final
                Log.d(TAG, "=== LOADING SUMMARY ===")
                Log.d(TAG, "Total documents: ${documents.size()}")
                Log.d(TAG, "Total challenges added: $totalCount")
                Log.d(TAG, "Parsing errors: $errorCount")
                Log.d(TAG, "Final challenges list size: ${allChallenges.size}")

                filteredChallenges.addAll(allChallenges)
                showLoading(false)
                displayChallenges()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "=== FIRESTORE QUERY FAILED ===")
                Log.e(TAG, "Error loading challenges: ${exception.message}")
                Log.e(TAG, "Exception type: ${exception.javaClass.simpleName}")
                exception.printStackTrace()

                showLoading(false)
                showEmptyState(true)
            }
    }

    private fun displayChallenges() {
        Log.d(TAG, "Displaying challenges...")
        Log.d(TAG, "Challenges to display: ${filteredChallenges.size}")

        challengesContainer.removeAllViews()

        if (filteredChallenges.isEmpty()) {
            Log.d(TAG, "No challenges to display - showing empty state")
            showEmptyState(true)
            return
        }

        Log.d(TAG, "Creating challenge cards...")
        showEmptyState(false)

        for ((index, challenge) in filteredChallenges.withIndex()) {
            Log.d(TAG, "Creating card $index for challenge: ${challenge.nombre}")
            val challengeCard = createChallengeCard(challenge)
            challengesContainer.addView(challengeCard)
        }

        Log.d(TAG, "All challenge cards created and added to container")
    }

    private fun createChallengeCard(challenge: DesafioPublico): View {
        Log.d(TAG, "Creating card for challenge: ${challenge.nombre}")

        val inflater = LayoutInflater.from(requireContext())
        val cardView = inflater.inflate(R.layout.item_challenge_card, challengesContainer, false)

        // Referencias a las vistas
        val tvChallengeName = cardView.findViewById<TextView>(R.id.tv_challenge_name)
        val tvChallengeCreator = cardView.findViewById<TextView>(R.id.tv_challenge_creator)
        val tvChallengeDescription = cardView.findViewById<TextView>(R.id.tv_challenge_description)
        val tvDuration = cardView.findViewById<TextView>(R.id.tv_duration)
        val cvDurationTag = cardView.findViewById<CardView>(R.id.cv_duration_badge)
        val llTagsContainer = cardView.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.ll_tags_container)
        val cvJoinButton = cardView.findViewById<CardView>(R.id.cv_join_button)

        // Configurar datos
        tvChallengeName.text = challenge.nombre
        tvChallengeCreator.text = "Por @${challenge.autorNombre}"
        tvChallengeDescription.text = challenge.descripcion

        // Configurar duración (usando días)
        if (challenge.dias > 0) {
            tvDuration.text = "${challenge.dias} días"
            cvDurationTag.visibility = View.VISIBLE
        } else {
            cvDurationTag.visibility = View.GONE
        }

        // Limpiar el contenedor de etiquetas antes de agregar nuevas
        llTagsContainer.removeAllViews()

        // Crear etiquetas dinámicamente
        val etiquetas = challenge.etiquetas
        if (etiquetas.isNotEmpty()) {
            for (etiqueta in etiquetas) {
                val tagCard = createTagView(etiqueta)
                llTagsContainer.addView(tagCard)
            }
            llTagsContainer.visibility = View.VISIBLE
        } else {
            llTagsContainer.visibility = View.GONE
        }

        // Configurar botón de unirse
        cvJoinButton.setOnClickListener {
            onJoinChallenge(challenge)
        }

        Log.d(TAG, "Card created successfully for: ${challenge.nombre}")
        return cardView
    }

    private fun createTagView(tagText: String): View {
        val inflater = LayoutInflater.from(requireContext())
        val tagView = inflater.inflate(R.layout.item_tag, null, false)

        // Configurar los LayoutParams para FlexboxLayout
        tagView.layoutParams = com.google.android.flexbox.FlexboxLayout.LayoutParams(
            com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
            com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 8.dpToPx(), 4.dpToPx()) // Margin derecho y abajo
        }

        // Configurar el texto
        val tvTagText = tagView.findViewById<TextView>(R.id.tag_text)
        tvTagText.text = "#${tagText}"

        return tagView
    }

    // Función auxiliar para convertir dp a px (si no la tienes ya)
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }


    private fun onJoinChallenge(challenge: DesafioPublico) {
        Log.d(TAG, "Joining challenge: ${challenge.nombre} (ID: ${challenge.id})")

        // Ejemplo de navegación (ajusta según tu navigation graph)
        // val bundle = Bundle().apply {
        //     putString("challengeId", challenge.id)
        // }
        // findNavController().navigate(R.id.action_to_challenge_detail, bundle)
    }

    private fun showLoading(show: Boolean) {
        Log.d(TAG, "Show loading: $show")
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        challengesContainer.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showEmptyState(show: Boolean) {
        Log.d(TAG, "Show empty state: $show")
        emptyState.visibility = if (show) View.VISIBLE else View.GONE
        challengesContainer.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = view?.findViewById<LinearLayout>(R.id.bottom_navigation)

        // Home (índice 0) - Navega a itemListFragment
        val homeLayout = bottomNavigation?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_publicChallengeFragment_to_itemListFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Today (índice 1)
        val todayLayout = bottomNavigation?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_publicChallengeFragment_to_todayFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Explorar (índice 2) - ya estamos aquí
        val exploreLayout = bottomNavigation?.getChildAt(2) as? LinearLayout
        exploreLayout?.setOnClickListener {
            // Ya estamos en Explorar, solo actualizar colores
        }

        // Profile (índice 3)
        val profileLayout = bottomNavigation?.getChildAt(3) as? LinearLayout
        profileLayout?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_publicChallengeFragment_to_profileFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}