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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
//        (activity as AppCompatActivity).supportActionBar?.hide()

        _binding = FragmentItemListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        ViewCompat.addOnUnhandledKeyEventListener(view) { _, _ -> false }

        // Setup RecyclerViews
        setupRecyclerViews()

        // Setup search functionality
        setupSearchBar()

        // Load user data and setup welcome message
        loadUserData()

        // Load data
        cargarDesafios()
        cargarDesafiosPorDefecto()
        setupCurrentDayCard()
        setupBottomNavigation()

        // Setup FAB
        binding.fab?.setOnClickListener {
            findNavController().navigate(R.id.action_itemListFragment_to_createDesafioFragment)
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        // First try to get from SharedPreferences (faster)
        val savedName = sharedPreferences.getString("user_name", null)
        if (!savedName.isNullOrEmpty()) {
            updateWelcomeText(savedName)
        }

        // Then get from Firestore (more up-to-date)
        firestore.collection("usuarios")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userName = document.getString("nombre") ?:
                    document.getString("name") ?:
                    document.getString("displayName") ?:
                    auth.currentUser?.displayName ?:
                    getString(R.string.default_name)

                    // Save to SharedPreferences for future use
                    sharedPreferences.edit()
                        .putString("user_name", userName)
                        .apply()

                    updateWelcomeText(userName)
                } else {
                    // If no user document exists, try to get from Firebase Auth
                    val userName = auth.currentUser?.displayName ?: getString(R.string.default_name)
                    updateWelcomeText(userName)
                }
            }
            .addOnFailureListener {
                // Fallback to Firebase Auth or default
                val userName = auth.currentUser?.displayName ?: getString(R.string.default_name)
                updateWelcomeText(userName)
            }
    }

    private fun updateWelcomeText(userName: String) {
        // Crear el texto de saludo personalizado usando el formato de string
        val helloText = getString(R.string.hello_user)
        // Reemplazar "Usuario" o "User" o "Usuário" con el nombre real
        val personalizedText = when {
            helloText.contains("Usuario") -> helloText.replace("Usuario", userName)
            helloText.contains("User") -> helloText.replace("User", userName)
            helloText.contains("Usuário") -> helloText.replace("Usuário", userName)
            else -> "Hola, $userName" // Fallback
        }
        binding.welcomeText?.text = personalizedText
    }

    private fun setupRecyclerViews() {
        // Active challenges RecyclerView
        binding.activeChallengesList?.layoutManager = LinearLayoutManager(requireContext())
        binding.activeChallengesList?.adapter = ActiveChallengesAdapter(filteredActiveChallenges)

        // Default challenges RecyclerView (Grid)
        binding.defaultChallengesGrid?.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.defaultChallengesGrid?.adapter = DefaultChallengesAdapter(defaultChallenges)
    }

    private fun setupSearchBar() {
        binding.searchBar?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterChallenges(s.toString())
            }
        })
    }

    private fun filterChallenges(query: String) {
        filteredActiveChallenges.clear()
        if (query.isEmpty()) {
            filteredActiveChallenges.addAll(activeChallenges)
        } else {
            // Filter by hashtags or name
            val searchQuery = query.lowercase()
            filteredActiveChallenges.addAll(
                activeChallenges.filter {
                    it.nombre.lowercase().contains(searchQuery) ||
                            it.descripcion.lowercase().contains(searchQuery) ||
                            searchQuery.startsWith("#")
                }
            )
        }
        binding.activeChallengesList?.adapter?.notifyDataSetChanged()
    }

    private fun setupCurrentDayCard() {
        // Get current date
        val dateFormat = SimpleDateFormat("EEEE d MMM", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        binding.currentDate?.text = currentDate

        // Setup current day card click
        binding.currentDayCard?.setOnClickListener {
            findNavController().navigate(R.id.action_itemListFragment_to_todayFragment)
//            navigateToToday()
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
        val totalHabitos: Int = 5
    ) : Parcelable

    data class DefaultChallenge(
        val title: String,
        val icon: String,
        val duration: String,
        val type: String
    )

    private fun cargarDesafios() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .get()
            .addOnSuccessListener { result ->
                activeChallenges.clear()
                activeChallenges.addAll(result.map { doc ->
                    Desafio(
                        nombre = doc.getString("nombre") ?: "",
                        descripcion = doc.getString("descripcion") ?: "",
                        dias = doc.getLong("dias")?.toInt() ?: 0,
                        creadoPor = uid,
                        id = doc.id,
                        diaActual = doc.getLong("diaActual")?.toInt() ?: 1,
                        completados = doc.getLong("completados")?.toInt() ?: 0,
                        totalHabitos = doc.getLong("totalHabitos")?.toInt() ?: 5
                    )
                })
                filteredActiveChallenges.clear()
                filteredActiveChallenges.addAll(activeChallenges)
                binding.activeChallengesList?.adapter?.notifyDataSetChanged()

                updateCurrentProgress()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, getString(R.string.error_format, e.message), Toast.LENGTH_LONG).show()
            }
    }

    private fun updateCurrentProgress() {
        if (activeChallenges.isNotEmpty()) {
            val currentChallenge = activeChallenges.first()
            binding.currentProgress?.text = getString(R.string.completed_format, currentChallenge.completados, currentChallenge.totalHabitos)
        }
    }

    private fun cargarDesafiosPorDefecto() {
        defaultChallenges.clear()
        defaultChallenges.addAll(listOf(
            DefaultChallenge(getString(R.string.fitness), "💪", getString(R.string.days_30), "fitness"),
            DefaultChallenge(getString(R.string.reading), "📚", "21 ${getString(R.string.days_30).split(" ")[1]}", "lectura"),
            DefaultChallenge("Mindfulness", "🧘", getString(R.string.days_30), "mindfulness"),
            DefaultChallenge(getString(R.string.hydration), "💧", "15 ${getString(R.string.days_30).split(" ")[1]}", "hidratacion")
        ))
        binding.defaultChallengesGrid?.adapter?.notifyDataSetChanged()
    }

    // Active Challenges Adapter
    inner class ActiveChallengesAdapter(private val challenges: List<Desafio>) :
        RecyclerView.Adapter<ActiveChallengesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val challengeName: TextView = view.findViewById(R.id.challenge_name)
            val progressBar: ProgressBar = view.findViewById(R.id.progress_bar)
            val progressText: TextView = view.findViewById(R.id.progress_text)
            val menuButton: ImageView = view.findViewById(R.id.menu_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_active_challenge, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val challenge = challenges[position]
            holder.challengeName.text = challenge.nombre

            val progress = ((challenge.diaActual.toFloat() / challenge.dias.toFloat()) * 100).toInt()
            holder.progressBar.progress = progress
            holder.progressText.text = getString(R.string.day_format, challenge.diaActual, challenge.dias)

            holder.itemView.setOnClickListener {
                val bundle = Bundle().apply {
                    putParcelable("desafio", challenge)
                }
                findNavController().navigate(R.id.show_item_detail, bundle)
            }

            // MENÚ DE TRES PUNTITOS
            holder.menuButton.setOnClickListener {
                val popup = PopupMenu(holder.itemView.context, holder.menuButton)
                popup.menuInflater.inflate(R.menu.menu_challenge_options, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit -> {
                            val bundle = Bundle().apply {
                                putParcelable("desafio", challenge)
                            }
//                            findNavController().navigate(R.id.action_itemListFragment_to_editDesafioFragment, bundle)
                            true
                        }
                        R.id.action_delete -> {
                            eliminarDesafio(challenge.id)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }

        override fun getItemCount() = challenges.size
    }


    private fun createChallengeFromDefault(defaultChallenge: DefaultChallenge) {
        val uid = auth.currentUser?.uid ?: return

        val challenge = getInitialChallengeForObjective(defaultChallenge.type)
        val challengesRef = firestore.collection("usuarios").document(uid).collection("desafios")

        // 1. Crear el desafío principal
        challengesRef.add(challenge)
            .addOnSuccessListener { documentRef ->

                // 2. Crear la estructura de días usando batch
                val batch = firestore.batch()
                val dias = challenge["dias"] as? Int ?: 30
                val habitos = challenge["habitos"] as? List<Map<String, Any>> ?: emptyList()

                // Convertir hábitos a la estructura que necesita cada día
                val habitosParaDias = habitos.map { habito ->
                    mapOf(
                        "nombre" to (habito["nombre"] ?: ""),
                        "completado" to false
                    )
                }

                // Obtener la fecha actual para calcular las fechas de cada día
                val fechaInicio = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                // Crear cada día del desafío
                for (i in 1..dias) {
                    val diaRef = documentRef.collection("dias").document("dia_$i")

                    // Calcular la fecha para este día (día 1 = hoy, día 2 = mañana, etc.)
                    val fechaDia = Calendar.getInstance().apply {
                        time = fechaInicio.time
                        add(Calendar.DAY_OF_YEAR, i - 1) // día 1 = +0 días, día 2 = +1 día, etc.
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

                // 3. Ejecutar el batch
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(context, getString(R.string.registration_successful).replace("Registro", "Desafío '${defaultChallenge.title}'"), Toast.LENGTH_SHORT).show()
                        cargarDesafios() // Recargar la lista de desafíos activos
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, getString(R.string.error_saving_data, e.message), Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, getString(R.string.error_saving_data, e.message), Toast.LENGTH_LONG).show()
            }
    }

    private fun getInitialChallengeForObjective(objective: String): HashMap<String, Any> {
        val currentTime = com.google.firebase.Timestamp.now()
        return when (objective) {
            "fitness" -> hashMapOf(
                "nombre" to getString(R.string.initial_fitness_challenge),
                "descripcion" to getString(R.string.fitness_challenge_description),
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
                    mapOf("nombre" to getString(R.string.walk_30_minutes), "completado" to false),
                    mapOf("nombre" to getString(R.string.stretch_15_minutes), "completado" to false),
                    mapOf("nombre" to getString(R.string.drink_2_liters_water), "completado" to false),
                    mapOf("nombre" to getString(R.string.sleep_7_8_hours), "completado" to false),
                    mapOf("nombre" to getString(R.string.eat_vegetables_portion), "completado" to false)
                )
            )
            "lectura" -> hashMapOf(
                "nombre" to getString(R.string.initial_reading_challenge),
                "descripcion" to getString(R.string.reading_challenge_description),
                "tipo" to "lectura",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to "activo",
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.take_reading_notes), "completado" to false),
                    mapOf("nombre" to getString(R.string.reflect_on_reading), "completado" to false),
                    mapOf("nombre" to getString(R.string.share_learned), "completado" to false),
                    mapOf("nombre" to getString(R.string.read_quiet_place), "completado" to false),
                    mapOf("nombre" to getString(R.string.habit_5), "completado" to false)
                )
            )
            "mindfulness" -> hashMapOf(
                "nombre" to getString(R.string.initial_mindfulness_challenge),
                "descripcion" to getString(R.string.mindfulness_challenge_description),
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
                    mapOf("nombre" to getString(R.string.meditate_10_minutes), "completado" to false),
                    mapOf("nombre" to getString(R.string.practice_conscious_breathing), "completado" to false),
                    mapOf("nombre" to getString(R.string.write_gratitude_journal), "completado" to false),
                    mapOf("nombre" to getString(R.string.take_conscious_pause), "completado" to false),
                    mapOf("nombre" to getString(R.string.observe_environment), "completado" to false)
                )
            )
            "hidratacion" -> hashMapOf(
                "nombre" to getString(R.string.initial_hydration_challenge),
                "descripcion" to getString(R.string.hydration_challenge_description),
                "tipo" to "hidratacion",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to "activo",
                "habitos" to listOf(
                    mapOf("nombre" to getString(R.string.drink_2_liters_water), "completado" to false),
                    mapOf("nombre" to getString(R.string.carry_water_bottle), "completado" to false),
                    mapOf("nombre" to getString(R.string.drink_water_before_meals), "completado" to false),
                    mapOf("nombre" to getString(R.string.avoid_sugary_drinks), "completado" to false),
                    mapOf("nombre" to getString(R.string.drink_water_wake_up), "completado" to false)
                )
            )
            else -> hashMapOf(
                "nombre" to getString(R.string.initial_generic_challenge),
                "descripcion" to getString(R.string.generic_challenge_description),
                "tipo" to "personalizado",
                "completado" to false,
                "fechaCreacion" to currentTime,
                "fechaInicio" to currentTime,
                "dias" to 30,
                "diaActual" to 1,
                "completados" to 0,
                "totalHabitos" to 5,
                "estado" to "activo",
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

    // Default Challenges Adapter
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
                // Crear nuevo desafío basado en el por defecto
                createChallengeFromDefault(challenge)
            }
        }

        override fun getItemCount() = challenges.size
    }

    private fun eliminarDesafio(id: String) {
        val context = requireContext()

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(getString(R.string.remove))
            .setMessage(getString(R.string.logout_confirmation).replace("cerrar sesión", "eliminar este desafío"))
            .setPositiveButton(getString(R.string.login).replace("Iniciar Sesión", "Sí")) { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                firestore.collection("usuarios")
                    .document(uid)
                    .collection("desafios")
                    .document(id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, getString(R.string.remove), Toast.LENGTH_SHORT).show()
                        cargarDesafios() // Recargar la lista después de eliminar
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, getString(R.string.error_saving_data, "eliminar desafío"), Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Context Menu handling
    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        requireActivity().menuInflater.inflate(R.menu.challenge_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                Toast.makeText(context, getString(R.string.options), Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_delete -> {
                Toast.makeText(context, getString(R.string.remove), Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_share -> {
                Toast.makeText(context, "Compartir desafío", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun setupBottomNavigation() {
        // Home (ya estamos aquí)
        val homeLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(0) as? LinearLayout
        homeLayout?.setOnClickListener {
            // Ya estamos en Home, solo actualizar colores
            updateBottomNavigationColors("home")
        }

        // Hoy
        val todayLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(1) as? LinearLayout
        todayLayout?.setOnClickListener {
            navigateToToday()
        }

        // Profile (cambiar de "Configuración" a "Profile")
        val profileLayout = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)
            ?.getChildAt(2) as? LinearLayout
        profileLayout?.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        // Establecer colores iniciales
        updateBottomNavigationColors("home")
    }

    private fun navigateToToday() {
        try {
            val bundle = Bundle().apply {
                putInt(DayDetailFragment.ARG_DAY_NUMBER, getCurrentDayNumber())
            }
            findNavController().navigate(R.id.action_itemListFragment_to_todayFragment, bundle)
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.error_navigating_today, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentDayNumber(): Int {
        // Obtener el día actual del desafío activo
        return if (activeChallenges.isNotEmpty()) {
            activeChallenges.first().diaActual
        } else {
            1
        }
    }

    private fun updateBottomNavigationColors(activeTab: String) {
        val bottomNav = binding.root.findViewById<LinearLayout>(R.id.bottom_navigation)

        // Home
        val homeLayout = bottomNav?.getChildAt(0) as? LinearLayout
        val homeIcon = homeLayout?.getChildAt(0) as? ImageView
        val homeText = homeLayout?.getChildAt(1) as? TextView

        // Hoy
        val todayLayout = bottomNav?.getChildAt(1) as? LinearLayout
        val todayIcon = todayLayout?.getChildAt(0) as? ImageView
        val todayText = todayLayout?.getChildAt(1) as? TextView

        // Profile
        val profileLayout = bottomNav?.getChildAt(2) as? LinearLayout
        val profileIcon = profileLayout?.getChildAt(0) as? ImageView
        val profileText = profileLayout?.getChildAt(1) as? TextView

        // Colores
        val activeColor = ContextCompat.getColor(requireContext(), R.color.primary_green)
        val inactiveColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)

        when (activeTab) {
            "home" -> {
                homeIcon?.setColorFilter(activeColor)
                homeText?.setTextColor(activeColor)
                todayIcon?.setColorFilter(inactiveColor)
                todayText?.setTextColor(inactiveColor)
                profileIcon?.setColorFilter(inactiveColor)
                profileText?.setTextColor(inactiveColor)
            }
            "today" -> {
                homeIcon?.setColorFilter(inactiveColor)
                homeText?.setTextColor(inactiveColor)
                todayIcon?.setColorFilter(activeColor)
                todayText?.setTextColor(activeColor)
                profileIcon?.setColorFilter(inactiveColor)
                profileText?.setTextColor(inactiveColor)
            }
            "profile" -> {
                homeIcon?.setColorFilter(inactiveColor)
                homeText?.setTextColor(inactiveColor)
                todayIcon?.setColorFilter(inactiveColor)
                todayText?.setTextColor(inactiveColor)
                profileIcon?.setColorFilter(activeColor)
                profileText?.setTextColor(activeColor)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to fragment
        cargarDesafios()
        // Refresh user data too
        loadUserData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}