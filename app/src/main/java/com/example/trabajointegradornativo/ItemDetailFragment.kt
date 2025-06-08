package com.example.trabajointegradornativo

import android.content.ClipData
import android.content.Context
import android.os.Bundle
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

class ItemDetailFragment : Fragment() {

    private var item: PlaceholderContent.PlaceholderItem? = null
    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

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
            if (it.containsKey(ARG_ITEM_ID)) {
                item = PlaceholderContent.ITEM_MAP[it.getString(ARG_ITEM_ID)]
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

        updateContent()
        return rootView
    }

    private fun updateContent() {
        item?.let {
            val context = requireContext()
            val prefs = context.getSharedPreferences("completed_days", Context.MODE_PRIVATE)

            val totalDays = 75
            val totalVisibleDays = 30
            var completedDays = 0

            // Contar cuántos días fueron marcados como completados (de los visibles)
            for (day in 1..totalVisibleDays) {
                if (prefs.getBoolean("day_completed_$day", false)) {
                    completedDays++
                }
            }

            val percentage = (completedDays * 100) / totalDays
            binding.circularProgress!!.progress = percentage
            binding.progressText!!.text = "$completedDays/$totalDays"
            binding.progressTitle!!.text = "Excelente progreso"
            binding.progressSubtitle!!.text = "$completedDays días completados\nconsecutivamente"

            // Agregar dinámicamente días del 1 al 30
            val dayListContainer = binding.root.findViewById<LinearLayout>(R.id.day_list_container)
            dayListContainer.removeAllViews()

            val inflater = LayoutInflater.from(context)
            val currentDay = it.id.toIntOrNull() ?: 1

            for (day in 1..totalVisibleDays) {
                val dayCard = inflater.inflate(R.layout.day_card, dayListContainer, false)

                val title = dayCard.findViewById<TextView>(R.id.day_title)
                val subtitle = dayCard.findViewById<TextView>(R.id.day_subtitle)
                val checkIcon = dayCard.findViewById<ImageView>(R.id.day_check_icon)

                title.text = "Día $day"

                val isCompleted = prefs.getBoolean("day_completed_$day", false)

                when {
                    isCompleted && day == currentDay -> {
                        subtitle.text = "Hoy • Completado"
                        checkIcon.visibility = View.VISIBLE
                    }
                    isCompleted && day == currentDay - 1 -> {
                        subtitle.text = "Ayer • Completado"
                        checkIcon.visibility = View.VISIBLE
                    }
                    isCompleted -> {
                        subtitle.text = "Completado"
                        checkIcon.visibility = View.VISIBLE
                    }
                    else -> {
                        subtitle.text = "Pendiente"
                        checkIcon.visibility = View.GONE
                    }
                }

                dayCard.setOnClickListener {
                    val bundle = Bundle().apply {
                        putInt("day_number", day)
                    }
                    // Usar la acción correcta del nav_graph
                    findNavController().navigate(R.id.action_itemDetailFragment_to_dayDetailFragment, bundle)
                }

                dayListContainer.addView(dayCard)
            }
        }
    }

    companion object {
        const val ARG_ITEM_ID = "item_id"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
