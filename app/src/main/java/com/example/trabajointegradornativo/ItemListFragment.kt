package com.example.trabajointegradornativo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trabajointegradornativo.databinding.FragmentItemListBinding
import com.example.trabajointegradornativo.databinding.ItemListContentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ItemListFragment : Fragment() {

    private var _binding: FragmentItemListBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.addOnUnhandledKeyEventListener(view) { _, _ -> false }

        binding.itemList.layoutManager = LinearLayoutManager(requireContext())
        cargarDesafios()

        // Navegar al fragmento de creación
        binding.fab!!.setOnClickListener {
            findNavController().navigate(R.id.action_itemListFragment_to_createDesafioFragment)
        }
    }

    data class Desafio(
        val nombre: String = "",
        val descripcion: String = "",
        val dias: Int = 0,
        val creadoPor: String = "",
        val id: String = "" // ID de documento Firestore
    )

    private fun cargarDesafios() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .get()
            .addOnSuccessListener { result ->
                val desafios = result.map { doc ->
                    Desafio(
                        nombre = doc.getString("nombre") ?: "",
                        descripcion = doc.getString("descripcion") ?: "",
                        dias = doc.getLong("dias")?.toInt() ?: 0,
                        creadoPor = uid,
                        id = doc.id
                    )
                }
                binding.itemList.adapter = DesafioAdapter(desafios)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al cargar desafíos: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    inner class DesafioAdapter(private val values: List<Desafio>) :
        RecyclerView.Adapter<DesafioAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemListContentBinding) :
            RecyclerView.ViewHolder(binding.root) {
            val contentView: TextView = binding.content
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemListContentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val desafio = values[position]
            holder.contentView.text = desafio.nombre

            holder.itemView.setOnClickListener {
                val bundle = Bundle().apply {
                    putString(ItemDetailFragment.ARG_ITEM_ID, desafio.id)
                }
                findNavController().navigate(R.id.show_item_detail, bundle)
            }
        }

        override fun getItemCount() = values.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
