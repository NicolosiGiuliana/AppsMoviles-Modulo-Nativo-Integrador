package com.example.trabajointegradornativo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreateChallengeFragment : Fragment() {

    // Views principales
    private lateinit var nombreInput: EditText
    private lateinit var habitoInput1: EditText
    private lateinit var habitoInput2: EditText
    private lateinit var habitoInput3: EditText
    private lateinit var crearButton: Button
    private lateinit var cancelarButton: TextView
    private lateinit var agregarHabitoButton: TextView

    // Opciones de duración
    private lateinit var option30Days: TextView
    private lateinit var option45Days: TextView
    private lateinit var option75Days: TextView

    // Ubicación
    private lateinit var layoutSelectLocation: LinearLayout

    // Variables para almacenar datos
    private var duracionSeleccionada = 30 // Por defecto 30 días
    private val habitosAdicionales = mutableListOf<EditText>()
    private var ubicacionSeleccionada: String? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_create_challenge, container, false)

        inicializarViews(view)
        configurarEventos()

        return view
    }

    private fun inicializarViews(view: View) {
        // Inputs principales
        nombreInput = view.findViewById(R.id.inputChallengeName)
        habitoInput1 = view.findViewById(R.id.inputHabit1)
        habitoInput2 = view.findViewById(R.id.inputHabit2)
        habitoInput3 = view.findViewById(R.id.inputHabit3)

        // Botones
        crearButton = view.findViewById(R.id.buttonCreateChallenge)
        cancelarButton = view.findViewById(R.id.buttonCancel)
        agregarHabitoButton = view.findViewById(R.id.buttonAddHabit)

        // Opciones de duración
        option30Days = view.findViewById(R.id.option30Days)
        option45Days = view.findViewById(R.id.option45Days)
        option75Days = view.findViewById(R.id.option75Days)

        // Ubicación
        layoutSelectLocation = view.findViewById(R.id.layoutSelectLocation)

        // Establecer 30 días como seleccionado por defecto
        seleccionarDuracion(option30Days, 30)
    }

    private fun configurarEventos() {
        // Eventos de duración
        option30Days.setOnClickListener { seleccionarDuracion(option30Days, 30) }
        option45Days.setOnClickListener { seleccionarDuracion(option45Days, 45) }
        option75Days.setOnClickListener { seleccionarDuracion(option75Days, 75) }

        // Agregar hábito
        agregarHabitoButton.setOnClickListener { agregarNuevoHabito() }

        // Seleccionar ubicación
        layoutSelectLocation.setOnClickListener { seleccionarUbicacion() }

        // Botones principales
        crearButton.setOnClickListener { guardarDesafio() }
        cancelarButton.setOnClickListener { cancelarCreacion() }
    }

    private fun seleccionarDuracion(opcionSeleccionada: TextView, dias: Int) {
        // Resetear todas las opciones
        resetearOpcionesDuracion()

        // Marcar la opción seleccionada
        opcionSeleccionada.setBackgroundResource(R.drawable.duration_selected_background)
        opcionSeleccionada.setTextColor(resources.getColor(R.color.green_primary, null))

        duracionSeleccionada = dias
    }

    private fun resetearOpcionesDuracion() {
        val opciones = listOf(option30Days, option45Days, option75Days)
        opciones.forEach { opcion ->
            opcion.setBackgroundResource(R.drawable.duration_unselected_background)
            opcion.setTextColor(resources.getColor(R.color.gray_text, null))
        }
    }

    private fun agregarNuevoHabito() {
        val containerPadre = view?.findViewById<LinearLayout>(R.id.habitosContainer)

        if (habitosAdicionales.size < 2) { // Máximo 5 hábitos (3 fijos + 2 adicionales)
            val nuevoHabito = EditText(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.edittext_height)
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.margin_small)
                }
                hint = "Hábito ${4 + habitosAdicionales.size}"
                setBackgroundResource(R.drawable.edittext_background)
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.padding_medium),
                    resources.getDimensionPixelSize(R.dimen.padding_medium),
                    resources.getDimensionPixelSize(R.dimen.padding_medium),
                    resources.getDimensionPixelSize(R.dimen.padding_medium)
                )
                setHintTextColor(resources.getColor(R.color.gray_hint, null))
            }

            habitosAdicionales.add(nuevoHabito)

            // Agregar antes del botón de agregar hábito
            val indexBotonAgregar = containerPadre?.indexOfChild(agregarHabitoButton) ?: 0
            containerPadre?.addView(nuevoHabito, indexBotonAgregar)

            // Ocultar botón si llegamos al máximo
            if (habitosAdicionales.size >= 2) {
                agregarHabitoButton.visibility = View.GONE
            }
        }
    }

    private fun seleccionarUbicacion() {
        // Aquí puedes implementar la lógica para abrir un selector de ubicación
        // Por ejemplo, abrir Google Maps o una actividad de selección de ubicación
        Toast.makeText(context, "Función de ubicación en desarrollo", Toast.LENGTH_SHORT).show()

        // Ejemplo de cómo guardar una ubicación
        ubicacionSeleccionada = "Ubicación ejemplo"
    }

    private fun recopilarHabitos(): List<String> {
        val habitos = mutableListOf<String>()

        // Agregar hábitos principales
        val habito1 = habitoInput1.text.toString().trim()
        val habito2 = habitoInput2.text.toString().trim()
        val habito3 = habitoInput3.text.toString().trim()

        if (habito1.isNotEmpty()) habitos.add(habito1)
        if (habito2.isNotEmpty()) habitos.add(habito2)
        if (habito3.isNotEmpty()) habitos.add(habito3)

        // Agregar hábitos adicionales
        habitosAdicionales.forEach { editText ->
            val habito = editText.text.toString().trim()
            if (habito.isNotEmpty()) habitos.add(habito)
        }

        return habitos
    }

    private fun validarFormulario(): Boolean {
        val nombre = nombreInput.text.toString().trim()
        val habitos = recopilarHabitos()

        when {
            nombre.isEmpty() -> {
                Toast.makeText(context, "El nombre del desafío es obligatorio", Toast.LENGTH_SHORT).show()
                return false
            }
            habitos.size < 3 -> {
                Toast.makeText(context, "Debes agregar al menos 3 hábitos", Toast.LENGTH_SHORT).show()
                return false
            }
            habitos.size > 5 -> {
                Toast.makeText(context, "No puedes agregar más de 5 hábitos", Toast.LENGTH_SHORT).show()
                return false
            }
            auth.currentUser?.uid == null -> {
                Toast.makeText(context, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        return true
    }

    private fun guardarDesafio() {
        if (!validarFormulario()) return

        val nombre = nombreInput.text.toString().trim()
        val habitos = recopilarHabitos()
        val uid = auth.currentUser?.uid!!
        val currentTime = com.google.firebase.Timestamp.now()

        // Crear estructura similar a getInitialChallengeForObjective
        val desafioBase = hashMapOf(
            "nombre" to nombre,
            "descripcion" to "Desafío personalizado de $duracionSeleccionada días",
            "tipo" to "personalizado",
            "completado" to false,
            "fechaCreacion" to currentTime,
            "fechaInicio" to currentTime,
            "dias" to duracionSeleccionada,
            "diaActual" to 1,
            "completados" to 0,
            "totalHabitos" to habitos.size,
            "ubicacion" to ubicacionSeleccionada,
            "creadoPor" to uid,
            "estado" to "activo",
            "habitos" to habitos.map { habito ->
                mapOf(
                    "nombre" to habito,
                    "completado" to false
                )
            },
            "progreso" to mapOf(
                "diasCompletados" to 0,
                "habitosCompletadosHoy" to emptyList<String>(),
                "ultimaActualizacion" to currentTime
            )
        )

        crearButton.isEnabled = false
        crearButton.text = "Creando..."

        // 1. Crear el desafío con la estructura completa
        firestore.collection("usuarios")
            .document(uid)
            .collection("desafios")
            .add(desafioBase)
            .addOnSuccessListener { documentRef ->

                val batch = firestore.batch()

                // 2. Crear los días dentro del desafío con sus hábitos
                // Convertir hábitos a la estructura con completado = false
                val habitosParaDias = habitos.map { habito ->
                    mapOf(
                        "nombre" to habito,
                        "completado" to false
                    )
                }

                for (i in 1..duracionSeleccionada) {
                    val diaRef = documentRef.collection("dias").document("dia_$i")
                    val dataDia = hashMapOf(
                        "dia" to i,
                        "habitos" to habitosParaDias,
                        "completado" to false,
                        "fecha_creacion" to currentTime
                    )
                    batch.set(diaRef, dataDia)
                }

                // 3. Commit de batch
                batch.commit().addOnSuccessListener {
                    Toast.makeText(context, "¡Desafío creado exitosamente!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(requireContext(), ItemDetailHostActivity::class.java))
                    activity?.finish()
                }.addOnFailureListener { e ->
                    Toast.makeText(context, "Error al crear los días: ${e.message}", Toast.LENGTH_LONG).show()
                    crearButton.isEnabled = true
                    crearButton.text = "Crear Desafío"
                }

            }.addOnFailureListener { e ->
                Toast.makeText(context, "Error al crear desafío: ${e.message}", Toast.LENGTH_LONG).show()
                crearButton.isEnabled = true
                crearButton.text = "Crear Desafío"
            }
    }


    private fun cancelarCreacion() {
        // Mostrar diálogo de confirmación si hay datos ingresados
        val hayDatos = nombreInput.text.toString().trim().isNotEmpty() ||
                recopilarHabitos().isNotEmpty()

        if (hayDatos) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Cancelar creación")
                .setMessage("¿Estás seguro de que quieres cancelar? Se perderán los datos ingresados.")
                .setPositiveButton("Sí, cancelar") { _, _ ->
                    activity?.onBackPressed()
                }
                .setNegativeButton("Continuar editando", null)
                .show()
        } else {
            activity?.onBackPressed()
        }
    }
}