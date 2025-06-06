package com.example.trabajointegradornativo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class DayDetailFragment : Fragment() {

    companion object {
        const val ARG_DAY_NUMBER = "day_number"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_day_detail, container, false)

        val dayNumber = arguments?.getInt(ARG_DAY_NUMBER) ?: 1
        val dayTextView = view.findViewById<TextView>(R.id.day_detail_text)
        dayTextView.text = "Día $dayNumber"

        return view
    }
}
