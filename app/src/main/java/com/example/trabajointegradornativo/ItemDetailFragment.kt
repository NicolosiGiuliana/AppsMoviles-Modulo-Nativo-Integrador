package com.example.trabajointegradornativo

import android.content.ClipData
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.trabajointegradornativo.databinding.FragmentItemDetailBinding
import com.example.trabajointegradornativo.placeholder.PlaceholderContent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ItemDetailFragment : Fragment() {

    private var item: PlaceholderContent.PlaceholderItem? = null
    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var desafio: ItemListFragment.Desafio

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var diasCompletados = mutableSetOf<Int>()

    private val dragListener = View.OnDragListener { _, event ->
        if (event.action == DragEvent.ACTION_DROP) {
            val clipDataItem: ClipData.Item = event.clipData.getItemAt(0)
            val dragData = clipDataItem.text
            item = PlaceholderContent.ITEM_MAP[dragData]
            updateContent()
        }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            desafio = it.getParcelable("desafio") ?: throw IllegalStateException("Desafio no encontrado en los argumentos")
            if (it.containsKey(ARG_ITEM_ID)) {
                val id = it.getString(ARG_ITEM_ID)
                Log.d("ItemDetailFragment", "ARG_ITEM_ID recibido: $id")

                item = PlaceholderContent.ITEM_MAP[id]
                if (item != null) {
                    Log.d("ItemDetailFragment", "Item encontrado: ${item!!.content}")
                } else {
                    Log.d("ItemDetailFragment", "No se encontró item con ID: $id en ITEM_MAP")
                }
            } else {
                Log.d("ItemDetailFragment", "ARG_ITEM_ID no recibido")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        val rootView = binding.root
        rootView.setOnDragListener(dragListener)

        Log.d("ItemDetailFragment", "onCreateView: Llamando a updateContent")
        cargarDiasCompletados()
        return rootView
    }

    private fun cargarDiasCompletados() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .document(desafio.id)
            .collection("dias_completados")
            .get()
            .addOnSuccessListener { result ->
                diasCompletados.clear()
                for (document in result) {
                    val dia = document.getLong("dia")?.toInt() ?: 0
                    if (dia > 0) {
                        diasCompletados.add(dia)
                    }
                }
                Log.d("ItemDetailFragment", "Días completados cargados: $diasCompletados")
                updateContent()
            }
            .addOnFailureListener { e ->
                Log.e("ItemDetailFragment", "Error al cargar días completados: ${e.message}")
                updateContent() // Continuar sin días completados
            }
    }

    private fun updateContent() {
        // Validar que los días del desafío sean mayores a 0
        if (desafio.dias <= 0) {
            Log.e("ItemDetailFragment", "El desafío '${desafio.nombre}' no tiene días válidos (${desafio.dias}).")
            return
        }

        Log.d("ItemDetailFragment", "Ejecutando updateContent() para: ${desafio.nombre}")

        // Configurar el progreso basado en días completados reales
        val diasCompletadosCount = diasCompletados.size
        val percentage = if (desafio.dias > 0) {
            (diasCompletadosCount * 100) / desafio.dias
        } else {
            0
        }

        binding.circularProgress!!.progress = percentage
        binding.progressText!!.text = "$diasCompletadosCount/${desafio.dias}"
        binding.progressTitle!!.text = desafio.nombre
        binding.progressSubtitle!!.text = "$diasCompletadosCount días completados"

        // Configurar los días dinámicamente
        val dayListContainer = binding.root.findViewById<LinearLayout>(R.id.day_list_container)
        dayListContainer.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        for (day in 1..desafio.dias) {
            Log.d("ItemDetailFragment", "Generando tarjeta para el Día $day")

            val dayCard = inflater.inflate(R.layout.day_card, dayListContainer, false)

            val title = dayCard.findViewById<TextView>(R.id.day_title)
            val subtitle = dayCard.findViewById<TextView>(R.id.day_subtitle)
            val checkIcon = dayCard.findViewById<ImageView>(R.id.day_check_icon)

            title.text = "Día $day"

            // Verificar si el día está completado
            if (diasCompletados.contains(day)) {
                subtitle.text = "Completado"
                checkIcon.visibility = View.VISIBLE
            } else {
                subtitle.text = "Pendiente"
                checkIcon.visibility = View.GONE
            }

            dayCard.setOnClickListener {
                val bundle = Bundle().apply {
                    putInt(DayDetailFragment.ARG_DAY_NUMBER, day)
                    putParcelable("desafio", desafio)
                }
                findNavController().navigate(R.id.action_itemDetailFragment_to_dayDetailFragment, bundle)
            }

            dayListContainer.addView(dayCard)
        }

        // Log de verificación final
        Log.d("ItemDetailFragment", "Total de días generados: ${dayListContainer.childCount}")
    }

    companion object {
        const val ARG_ITEM_ID = "item_id"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}