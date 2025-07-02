package com.example.trabajointegradornativo

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trabajointegradornativo.databinding.FragmentItemListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat
import kotlinx.parcelize.Parcelize

class ItemListFragment : Fragment() {

    private var _binding: FragmentItemListBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var sharedPreferences: SharedPreferences

    private var activeChallenges = mutableListOf<Desafio>()
    private var defaultChallenges = mutableListOf<DefaultChallenge>()
    private var filteredActiveChallenges = mutableListOf<Desafio>()

    // Infla el layout y oculta la ActionBar
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        (activity as AppCompatActivity).supportActionBar?.hide()

        _binding = FragmentItemListBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Inicializa componentes UI, RecyclerViews, búsqueda y navegación
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences =
            requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        ViewCompat.addOnUnhandledKeyEventListener(view) { _, _ -> false }

        setupRecyclerViews()

        setupSearchBar()

        loadUserData()

        cargarDesafios()
        cargarDesafiosPorDefecto()
        setupBottomNavigation()

        binding.fab?.setOnClickListener {
            findNavController().navigate(R.id.action_itemListFragment_to_createDesafioFragment)
        }
    }

    // Verifica si el fragment está activo y vinculado antes de ejecutar actualizaciones de UI para prevenir crashes por referencias nulas
    private fun safeUpdateUI(action: () -> Unit) {
        if (isAdded && _binding != null && !isDetached) {
            try {
                action()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Carga los datos del usuario desde Firestore y actualiza el texto de bienvenida
    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        val savedName = sharedPreferences.getString("user_name", null)
        if (!savedName.isNullOrEmpty()) {
            updateWelcomeText(savedName)
        }

        firestore.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || binding == null) return@addOnSuccessListener

                if (document.exists()) {
                    val userName = document.getString("nombre") ?: document.getString("name")
                    ?: document.getString("displayName") ?: auth.currentUser?.displayName
                    ?: getString(R.string.default_name)

                    sharedPreferences.edit()
                        .putString("user_name", userName)
                        .apply()

                    updateWelcomeText(userName)
                } else {
                    val userName = auth.currentUser?.displayName ?: getString(R.string.default_name)
                    updateWelcomeText(userName)
                }
            }
            .addOnFailureListener {
                if (!isAdded || binding == null) return@addOnFailureListener

                val userName = auth.currentUser?.displayName ?: getString(R.string.default_name)
                updateWelcomeText(userName)
            }
    }

    // Actualiza el texto de bienvenida con el nombre del usuario
    private fun updateWelcomeText(userName: String) {
        safeUpdateUI {
            val helloText = getString(R.string.hello_user)
            val personalizedText = when {
                helloText.contains("Usuario") -> helloText.replace("Usuario", userName)
                helloText.contains("User") -> helloText.replace("User", userName)
                helloText.contains("Usuário") -> helloText.replace("Usuário", userName)
                else -> getString(R.string.hello_user_fallback, userName)
            }
            binding.welcomeText?.text = personalizedText
        }
    }

    // Configura los RecyclerViews para mostrar desafíos activos y desafíos por defecto
    private fun setupRecyclerViews() {
        binding.activeChallengesList?.layoutManager = LinearLayoutManager(requireContext())
        binding.activeChallengesList?.adapter = ActiveChallengesAdapter(filteredActiveChallenges)

        binding.defaultChallengesGrid?.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.defaultChallengesGrid?.adapter = DefaultChallengesAdapter(defaultChallenges)
    }

    // Configura la barra de búsqueda para filtrar desafíos activos
    private fun setupSearchBar() {
        binding.searchBar?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterChallenges(s.toString())
            }
        })
    }

    // Filtra los desafíos activos según la consulta de búsqueda
    private fun filterChallenges(query: String) {
        filteredActiveChallenges.clear()
        if (query.isEmpty()) {
            filteredActiveChallenges.addAll(activeChallenges)
        } else {
            val searchQuery = query.lowercase()
            filteredActiveChallenges.addAll(
                activeChallenges.filter {
                    it.nombre.lowercase().contains(searchQuery) ||
                            it.descripcion.lowercase().contains(searchQuery) ||
                            searchQuery.startsWith("#")
                }
            )
        }

        safeUpdateUI {
            binding.activeChallengesList?.adapter?.notifyDataSetChanged()
        }
    }

    @Parcelize
    data class Desafio(
        val nombre: String = "",
        val descripcion: String = "",
        val dias: Int = 0,
        val creadoPor: String = "",
        val id: String = "",
        val diaActual: Int = 0,
        val completados: Int = 0,
        val diasCompletados: Int = 0,
        val totalHabitos: Int = 5,
        val etiquetas: List<String> = emptyList(),
        val visibilidad: String = "privado"
    ) : Parcelable

    data class DefaultChallenge(
        val title: String,
        val icon: String,
        val duration: String,
        val type: String
    )

    // Carga los desafíos del usuario desde Firestore y actualiza la lista de desafíos activos
    private fun cargarDesafios() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .get()
            .addOnSuccessListener { result ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                activeChallenges.clear()
                filteredActiveChallenges.clear()

                val desafiosProcessed = mutableListOf<Desafio>()
                var contador = 0

                if (result.isEmpty) {
                    safeUpdateUI {
                        binding.activeChallengesList?.adapter?.notifyDataSetChanged()
                    }
                    return@addOnSuccessListener
                }

                result.forEach { doc ->
                    val desafioId = doc.id
                    val totalDias = doc.getLong("dias")?.toInt() ?: 0
                    val totalHabitos = doc.getLong("totalHabitos")?.toInt() ?: 5
                    val fechaInicio =
                        doc.getTimestamp("fechaInicio") ?: doc.getTimestamp("fechaCreacion")

                    val diaActual = calcularDiaActualPorFecha(fechaInicio)

                    firestore.collection("usuarios")
                        .document(uid)
                        .collection("desafios")
                        .document(desafioId)
                        .collection("dias")
                        .get()
                        .addOnSuccessListener { diasResult ->
                            if (!isAdded || _binding == null) return@addOnSuccessListener

                            var habitosCompletadosTotal = 0
                            var diasCompletados = 0

                            diasResult.documents.forEach { diaDoc ->
                                val habitos =
                                    diaDoc.get("habitos") as? List<Map<String, Any>> ?: emptyList()
                                val diaCompletado = diaDoc.getBoolean("completado") ?: false

                                if (diaCompletado) {
                                    diasCompletados++
                                }

                                habitos.forEach { habito ->
                                    val completado = habito["completado"] as? Boolean ?: false
                                    if (completado) {
                                        habitosCompletadosTotal++
                                    }
                                }
                            }

                            val desafio = Desafio(
                                nombre = doc.getString("nombre") ?: "",
                                descripcion = doc.getString("descripcion") ?: "",
                                dias = totalDias,
                                creadoPor = uid,
                                id = doc.id,
                                diaActual = minOf(diaActual, totalDias),
                                completados = habitosCompletadosTotal,
                                diasCompletados = diasCompletados,
                                totalHabitos = totalHabitos * totalDias,
                                etiquetas = doc.get("etiquetas") as? List<String> ?: emptyList(),
                                visibilidad = doc.getString("visibilidad") ?: "privado"
                            )

                            desafiosProcessed.add(desafio)
                            contador++

                            if (contador == result.size()) {
                                safeUpdateUI {
                                    activeChallenges.clear()
                                    activeChallenges.addAll(desafiosProcessed)
                                    filteredActiveChallenges.clear()
                                    filteredActiveChallenges.addAll(desafiosProcessed)
                                    binding.activeChallengesList?.adapter?.notifyDataSetChanged()
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            if (!isAdded || _binding == null) return@addOnFailureListener

                            val desafio = Desafio(
                                nombre = doc.getString("nombre") ?: "",
                                descripcion = doc.getString("descripcion") ?: "",
                                dias = totalDias,
                                creadoPor = uid,
                                id = doc.id,
                                diaActual = minOf(diaActual, totalDias),
                                completados = 0,
                                diasCompletados = 0,
                                totalHabitos = totalHabitos * totalDias,
                                etiquetas = doc.get("etiquetas") as? List<String> ?: emptyList(),
                                visibilidad = doc.getString("visibilidad") ?: "privado"
                            )

                            desafiosProcessed.add(desafio)
                            contador++

                            if (contador == result.size()) {
                                safeUpdateUI {
                                    activeChallenges.clear()
                                    activeChallenges.addAll(desafiosProcessed)
                                    filteredActiveChallenges.clear()
                                    filteredActiveChallenges.addAll(desafiosProcessed)
                                    binding.activeChallengesList?.adapter?.notifyDataSetChanged()
                                }
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                safeUpdateUI {
                    Toast.makeText(
                        context,
                        getString(R.string.error_format, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // Calcula el día actual basado en la fecha de inicio del desafío
    private fun calcularDiaActualPorFecha(fechaInicio: com.google.firebase.Timestamp?): Int {
        if (fechaInicio == null) return 1

        val fechaInicioDate = fechaInicio.toDate()
        val fechaActual = Date()

        val diferenciaMilisegundos = fechaActual.time - fechaInicioDate.time
        val diferenciaDias = (diferenciaMilisegundos / (1000 * 60 * 60 * 24)).toInt()

        return maxOf(1, diferenciaDias + 1)
    }

    // Adaptador para mostrar etiquetas en los desafíos activos
    inner class TagsAdapter(private val tags: List<String>) :
        RecyclerView.Adapter<TagsAdapter.TagViewHolder>() {

        // ViewHolder para cadaetiqueta
        inner class TagViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tagText: TextView = view.findViewById(R.id.tag_text)
        }

        // Crea una vista para cada etiqueta
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tag, parent, false)
            return TagViewHolder(view)
        }

        // Vincula el texto de la etiqueta a la vista
        override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
            holder.tagText.text = "#${tags[position]}"
        }

        // Devuelve el número total de etiquetas
        override fun getItemCount() = tags.size
    }

    // Carga desafíos por defecto para mostrar en la pantalla principal
    private fun cargarDesafiosPorDefecto() {
        defaultChallenges.clear()
        defaultChallenges.addAll(
            listOf(
                DefaultChallenge(
                    getString(R.string.fitness),
                    "💪",
                    getString(R.string.days_30),
                    getString(R.string.fitness_type)
                ),
                DefaultChallenge(
                    getString(R.string.reading),
                    "📚",
                    "21 ${getString(R.string.days_30).split(" ")[1]}",
                    getString(R.string.reading_type)
                ),
                DefaultChallenge(
                    getString(R.string.mindfulness_challenge_name),
                    "🧘",
                    getString(R.string.days_30),
                    getString(R.string.mindfulness_type)
                ),
                DefaultChallenge(
                    getString(R.string.hydration),
                    "💧",
                    "15 ${getString(R.string.days_30).split(" ")[1]}",
                    getString(R.string.hydration_type)
                )
            )
        )

        safeUpdateUI {
            binding.defaultChallengesGrid?.adapter?.notifyDataSetChanged()
        }
    }

    // Adaptador para mostrar desafíos activos en una lista
    inner class ActiveChallengesAdapter(private val challenges: List<Desafio>) :
        RecyclerView.Adapter<ActiveChallengesAdapter.ViewHolder>() {

        // ViewHolder para cada desafío activo
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val challengeName: TextView = view.findViewById(R.id.challenge_name)
            val progressBar: ProgressBar = view.findViewById(R.id.progress_bar)
            val progressText: TextView = view.findViewById(R.id.progress_text)
            val visibilityTag: TextView = view.findViewById(R.id.visibility_tag)
            val tagsRecycler: RecyclerView = view.findViewById(R.id.tags_recycler)
        }

        // Crea una vista para cada desafío activo
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_active_challenge, parent, false)
            return ViewHolder(view)
        }

        // Vincula los datos del desafío activo a la vista
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val challenge = challenges[position]
            holder.challengeName.text = challenge.nombre

            val progressDias = if (challenge.dias > 0) {
                ((challenge.diasCompletados.toFloat() / challenge.dias.toFloat()) * 100).toInt()
            } else {
                0
            }

            val safeProgress = maxOf(0, minOf(progressDias, 100))
            holder.progressBar.progress = safeProgress

            holder.progressText.text =
                getString(R.string.completed_format, challenge.diasCompletados, challenge.dias)

            holder.visibilityTag.text = if (challenge.visibilidad == getString(R.string.publico))
                getString(R.string.public_challenge) else getString(R.string.private_challenge)

            val tagsLayoutManager =
                LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
            holder.tagsRecycler.layoutManager = tagsLayoutManager
            holder.tagsRecycler.adapter = TagsAdapter(challenge.etiquetas)

            holder.itemView.setOnClickListener {
                val bundle = Bundle().apply {
                    putParcelable("desafio", challenge)
                }
                findNavController().navigate(R.id.show_item_detail, bundle)
            }
        }

        // Devuelve el número total de desafíos activos
        override fun getItemCount() = challenges.size
    }

    // Obtiene el desafío inicial para un objetivo específico
    private fun getInitialChallengeForObjective(objective: String): HashMap<String, Any> {
        val currentTime = com.google.firebase.Timestamp.now()
        return when (objective) {
            getString(R.string.fitness_type) -> hashMapOf(
                "nombre" to getString(R.string.initial_fitness_challenge),
                "descripcion" to getString(R.string.fitness_challenge_description),
                "tipo" to getString(R.string.fitness_type),
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to getString(R.string.active_status),
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.walk_30_minutes), "completado" to false),
                    mapOf(
                        "nombre" to getString(R.string.stretch_15_minutes),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.drink_2_liters_water),
                        "completado" to false
                    ),
                    mapOf("nombre" to getString(R.string.sleep_7_8_hours), "completado" to false),
                    mapOf(
                        "nombre" to getString(R.string.eat_vegetables_portion),
                        "completado" to false
                    )
                )
            )

            getString(R.string.reading_type) -> hashMapOf(
                "nombre" to getString(R.string.initial_reading_challenge),
                "descripcion" to getString(R.string.reading_challenge_description),
                "tipo" to getString(R.string.reading_type),
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 21,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to getString(R.string.active_status),
                "habitos" to listOf(
                    mapOf(
                        "nombre" to getString(R.string.take_reading_notes),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.reflect_on_reading),
                        "completado" to false
                    ),
                    mapOf("nombre" to getString(R.string.share_learned), "completado" to false),
                    mapOf("nombre" to getString(R.string.read_quiet_place), "completado" to false),
                    mapOf("nombre" to getString(R.string.habit_5), "completado" to false)
                )
            )

            getString(R.string.mindfulness_type) -> hashMapOf(
                "nombre" to getString(R.string.initial_mindfulness_challenge),
                "descripcion" to getString(R.string.mindfulness_challenge_description),
                "tipo" to getString(R.string.mindfulness_type),
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to getString(R.string.active_status),
                "habitos" to listOf(
                    mapOf(
                        "nombre" to getString(R.string.meditate_10_minutes),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.practice_conscious_breathing),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.write_gratitude_journal),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.take_conscious_pause),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.observe_environment),
                        "completado" to false
                    )
                )
            )

            getString(R.string.hydration_type) -> hashMapOf(
                "nombre" to getString(R.string.initial_hydration_challenge),
                "descripcion" to getString(R.string.hydration_challenge_description),
                "tipo" to getString(R.string.hydration_type),
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 15,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to getString(R.string.active_status),
                "habitos" to listOf(
                    mapOf(
                        "nombre" to getString(R.string.drink_2_liters_water),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.carry_water_bottle),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.drink_water_before_meals),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.avoid_sugary_drinks),
                        "completado" to false
                    ),
                    mapOf(
                        "nombre" to getString(R.string.drink_water_wake_up),
                        "completado" to false
                    )
                )
            )

            else -> hashMapOf(
                "nombre" to getString(R.string.initial_generic_challenge),
                "descripcion" to getString(R.string.generic_challenge_description),
                "tipo" to getString(R.string.custom_type),
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to getString(R.string.active_status),
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.habit_1), "completado" to false),
                    mapOf("nombre" to getString(R.string.habit_2), "completado" to false),
                    mapOf("nombre" to getString(R.string.habit_3), "completado" to false),
                    mapOf("nombre" to getString(R.string.habit_4), "completado" to false),
                    mapOf("nombre" to getString(R.string.habit_5), "completado" to false)
                )
            )
        }
    }

    // Adaptador para mostrar desafíos por defecto en una cuadrícula
    inner class DefaultChallengesAdapter(private val challenges: List<DefaultChallenge>) :
        RecyclerView.Adapter<DefaultChallengesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val challengeIcon: TextView = view.findViewById(R.id.challenge_icon)
            val challengeTitle: TextView = view.findViewById(R.id.challenge_title)
            val challengeDuration: TextView = view.findViewById(R.id.challenge_duration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_default_challenge, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val challenge = challenges[position]
            holder.challengeIcon.text = challenge.icon
            holder.challengeTitle.text = challenge.title
            holder.challengeDuration.text = challenge.duration

            holder.itemView.setOnClickListener {
                openDefaultChallengePreview(challenge)
            }
        }

        override fun getItemCount() = challenges.size
    }

    // Abre una vista previa del desafío por defecto seleccionado
    private fun openDefaultChallengePreview(defaultChallenge: DefaultChallenge) {
        val challengeData = getInitialChallengeForObjective(defaultChallenge.type)

        val previewChallenge = DesafioPublico(
            id = "",
            nombre = challengeData["nombre"] as? String ?: "",
            autorNombre = getString(R.string.autor_nombre_default),
            descripcion = challengeData["descripcion"] as? String ?: "",
            dias = challengeData["dias"] as? Int ?: 30,
            etiquetas = listOf(
                challengeData["tipo"] as? String ?: getString(R.string.general_type)
            ),
            habitos = (challengeData["habitos"] as? List<Map<String, Any>>)?.map { habitMap ->
                Habito(
                    nombre = habitMap["nombre"] as? String ?: "",
                    completado = false
                )
            } ?: emptyList(),
            fechaCreacion = com.google.firebase.Timestamp.now()
        )

        val bundle = Bundle().apply {
            putString("challengeName", previewChallenge.nombre)
            putString("challengeAuthor", previewChallenge.autorNombre)
            putString("challengeDescription", previewChallenge.descripcion)
            putInt("challengeDuration", previewChallenge.dias)
            putStringArrayList("challengeTags", ArrayList(previewChallenge.etiquetas))
            putStringArrayList(
                "challengeHabits",
                ArrayList(previewChallenge.habitos.map { it.nombre })
            )
            putBoolean("isDefaultChallenge", true)
            putString("defaultChallengeType", defaultChallenge.type)
        }

        findNavController().navigate(
            R.id.action_itemListFragment_to_challengePreviewFragment,
            bundle
        )
    }

    // Configura la navegación por la barra inferior
    private fun setupBottomNavigation() {
        val homeLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            updateBottomNavigationColors(getString(R.string.home))
        }

        val todayLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            navigateToToday()
        }

        val exploreLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(2) as? LinearLayout
        exploreLayout?.setOnClickListener {
            findNavController().navigate(R.id.action_itemListFragment_to_publicChallengeFragment)
            updateBottomNavigationColors(getString(R.string.explore))
        }

        val profileLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(3) as? LinearLayout
        profileLayout?.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
            updateBottomNavigationColors(getString(R.string.profile))
        }

        updateBottomNavigationColors(getString(R.string.home))
    }

    // Navega a la pantalla de hoy con el día actual
    private fun navigateToToday() {
        try {
            val bundle = Bundle().apply {
                putInt(DayDetailFragment.ARG_DAY_NUMBER, getCurrentDayNumber())
            }
            findNavController().navigate(R.id.action_itemListFragment_to_todayFragment, bundle)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                getString(R.string.error_navigating_today, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Obtiene el número del día actual
    private fun getCurrentDayNumber(): Int {
        return if (activeChallenges.isNotEmpty()) {
            activeChallenges.first().diaActual
        } else {
            1
        }
    }

    // Actualiza los colores de la barra de navegación inferior según la pestaña activa
    private fun updateBottomNavigationColors(activeTab: String) {
        val bottomNav = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)

        val homeLayout = bottomNav?.getChildAt(0) as? LinearLayout
        val homeIcon = homeLayout?.getChildAt(0) as? ImageView
        val homeText = homeLayout?.getChildAt(1) as? TextView

        val todayLayout = bottomNav?.getChildAt(1) as? LinearLayout
        val todayIcon = todayLayout?.getChildAt(0) as? ImageView
        val todayText = todayLayout?.getChildAt(1) as? TextView

        val profileLayout = bottomNav?.getChildAt(2) as? LinearLayout
        val profileIcon = profileLayout?.getChildAt(0) as? ImageView
        val profileText = profileLayout?.getChildAt(1) as? TextView

        val activeColor = ContextCompat.getColor(requireContext(), R.color.primary_green)
        val inactiveColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)

        when (activeTab) {
            getString(R.string.home_navigation) -> {
                homeIcon?.setColorFilter(activeColor)
                homeText?.setTextColor(activeColor)
                todayIcon?.setColorFilter(inactiveColor)
                todayText?.setTextColor(inactiveColor)
                profileIcon?.setColorFilter(inactiveColor)
                profileText?.setTextColor(inactiveColor)
            }

            getString(R.string.today) -> {
                homeIcon?.setColorFilter(inactiveColor)
                homeText?.setTextColor(inactiveColor)
                todayIcon?.setColorFilter(activeColor)
                todayText?.setTextColor(activeColor)
                profileIcon?.setColorFilter(inactiveColor)
                profileText?.setTextColor(inactiveColor)
            }

            getString(R.string.profile_navigation) -> {
                homeIcon?.setColorFilter(inactiveColor)
                homeText?.setTextColor(inactiveColor)
                todayIcon?.setColorFilter(inactiveColor)
                todayText?.setTextColor(inactiveColor)
                profileIcon?.setColorFilter(activeColor)
                profileText?.setTextColor(activeColor)
            }
        }
    }

    // Metodo de inicialización del fragmento
    override fun onResume() {
        super.onResume()
        cargarDesafios()
        loadUserData()
    }

    // Metodo de creación de la vista del fragmento
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}