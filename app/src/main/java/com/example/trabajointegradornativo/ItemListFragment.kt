package com.example.trabajointegradornativo

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
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

        // Load data
        cargarDesafios()
        cargarDesafiosPorDefecto()
        setupCurrentDayCard()

        // Setup FAB
        binding.fab?.setOnClickListener {
            findNavController().navigate(R.id.action_itemListFragment_to_createDesafioFragment)
        }
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
        val dateFormat = SimpleDateFormat("EEEE d MMM", Locale("es", "ES"))
        val currentDate = dateFormat.format(Date())
        binding.currentDate?.text = currentDate

        // Setup current day card click
        binding.currentDayCard?.setOnClickListener {
            // Navigate to today's screen
            // findNavController().navigate(R.id.action_to_today_fragment)
            Toast.makeText(context, "Navegar a pantalla de hoy", Toast.LENGTH_SHORT).show()
        }
    }

    data class Desafio(
        val nombre: String = "",
        val descripcion: String = "",
        val dias: Int = 0,
        val creadoPor: String = "",
        val id: String = "",
        val diaActual: Int = 0,
        val completados: Int = 0,
        val totalHabitos: Int = 5
    )

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
                Toast.makeText(context, "Error al cargar desafíos: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateCurrentProgress() {
        if (activeChallenges.isNotEmpty()) {
            val currentChallenge = activeChallenges.first()
            binding.currentProgress?.text = "${currentChallenge.completados}/${currentChallenge.totalHabitos} completados"
        }
    }

    private fun cargarDesafiosPorDefecto() {
        defaultChallenges.clear()
        defaultChallenges.addAll(listOf(
            DefaultChallenge("Fitness", "💪", "30 días", "fitness"),
            DefaultChallenge("Lectura", "📚", "45 días", "lectura"),
            DefaultChallenge("Mindfulness", "🧘", "75 días", "mindfulness"),
            DefaultChallenge("Hidratación", "💧", "30 días", "hidratacion")
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

            init {
                // Register for context menu
                view.setOnCreateContextMenuListener { menu, _, _ ->
                    requireActivity().menuInflater.inflate(R.menu.challenge_context_menu, menu)
                }
            }
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
            holder.progressText.text = "Día ${challenge.diaActual} de ${challenge.dias}"

            holder.itemView.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("ITEM_ID", challenge.id) // Cambiado para evitar errores con ItemDetailFragment.ARG_ITEM_ID
                }
                findNavController().navigate(R.id.show_item_detail, bundle)
            }

            // Register for context menu
            registerForContextMenu(holder.itemView)
        }

        override fun getItemCount() = challenges.size
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
                // Create new challenge based on default
                Toast.makeText(context, "Crear desafío: ${challenge.title}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount() = challenges.size
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
                Toast.makeText(context, "Editar desafío", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_delete -> {
                Toast.makeText(context, "Eliminar desafío", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_share -> {
                Toast.makeText(context, "Compartir desafío", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to fragment
        cargarDesafios()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}