package com.example.trabajointegradornativo

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_public_challenge, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews()
        setupFirestore()
        setupBottomNavigation()
        setupSearchFunctionality()
        loadPublicChallenges()
    }

    private fun initializeViews() {
        challengesContainer = view?.findViewById(R.id.challenges_container) ?: return
        loadingProgress = view?.findViewById(R.id.loading_progress) ?: return
        emptyState = view?.findViewById(R.id.empty_state) ?: return
        searchEditText = view?.findViewById(R.id.search_edit_text) ?: return
    }

    private fun setupFirestore() {
        db = FirebaseFirestore.getInstance()
    }

    private fun setupSearchFunctionality() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChallenges(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterChallenges(query: String) {
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

        displayChallenges()
    }

    private fun loadPublicChallenges() {
        showLoading(true)

        db.collection("desafiosPublicos")
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                allChallenges.clear()
                filteredChallenges.clear()

                for (document in documents) {
                    try {
                        val challenge = document.toObject(DesafioPublico::class.java)
                        challenge.id = document.id
                        allChallenges.add(challenge)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                filteredChallenges.addAll(allChallenges)
                showLoading(false)
                displayChallenges()
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                showLoading(false)
                showEmptyState(true)
            }
    }

    private fun displayChallenges() {
        challengesContainer.removeAllViews()

        if (filteredChallenges.isEmpty()) {
            showEmptyState(true)
            return
        }

        showEmptyState(false)

        for (challenge in filteredChallenges) {
            val challengeCard = createChallengeCard(challenge)
            challengesContainer.addView(challengeCard)
        }
    }

    private fun createChallengeCard(challenge: DesafioPublico): View {
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

        return cardView
    }

    private fun createTagView(tagText: String): View {
        val inflater = LayoutInflater.from(requireContext())
        val tagView = inflater.inflate(R.layout.item_tag_public, null, false)

        // Configurar los LayoutParams para FlexboxLayout
        tagView.layoutParams = com.google.android.flexbox.FlexboxLayout.LayoutParams(
            com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
            com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 8.dpToPx(), 4.dpToPx()) // Margin derecho y abajo
        }

        // Configurar el texto
        val tvTagText = tagView.findViewById<TextView>(R.id.tag_text_public)
        tvTagText.text = "#${tagText}"

        return tagView
    }

    // Función auxiliar para convertir dp a px (si no la tienes ya)
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun onJoinChallenge(challenge: DesafioPublico) {
        // Ejemplo de navegación (ajusta según tu navigation graph)
        // val bundle = Bundle().apply {
        //     putString("challengeId", challenge.id)
        // }
        // findNavController().navigate(R.id.action_to_challenge_detail, bundle)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        challengesContainer.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showEmptyState(show: Boolean) {
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