package com.example.trabajointegradornativo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreateChallengeFragment : Fragment() {

    private lateinit var nombreInput: EditText
    private lateinit var descripcionInput: EditText
    private lateinit var diasInput: EditText
    private lateinit var crearButton: Button

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_create_challenge, container, false)

        nombreInput = view.findViewById(R.id.inputChallengeName)
        descripcionInput = view.findViewById(R.id.inputChallengeDescription)
        diasInput = view.findViewById(R.id.inputChallengeDays)
        crearButton = view.findViewById(R.id.buttonCreateChallenge)

        crearButton.setOnClickListener {
            guardarDesafio()
        }

        return view
    }

    private fun guardarDesafio() {
        val nombre = nombreInput.text.toString().trim()
        val descripcion = descripcionInput.text.toString().trim()
        val dias = diasInput.text.toString().toIntOrNull()
        val uid = auth.currentUser?.uid

        if (nombre.isEmpty() || descripcion.isEmpty() || dias == null || dias <= 0 || uid == null) {
            Toast.makeText(context, "Completa todos los campos correctamente", Toast.LENGTH_SHORT).show()
            return
        }

        val desafio = Desafio(
            nombre = nombre,
            descripcion = descripcion,
            dias = dias,
            creadoPor = uid
        )

        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .add(desafio)
            .addOnSuccessListener {
                Toast.makeText(context, "Desafío creado", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireContext(), ItemDetailHostActivity::class.java))
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
