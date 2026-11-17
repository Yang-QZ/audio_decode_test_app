package com.example.audiodecodetestapp.ui.codecs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audiodecodetestapp.R
import com.example.audiodecodetestapp.adapter.CodecFormatAdapter
import com.example.audiodecodetestapp.model.CodecFormat

class CodecsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_codecs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_codecs)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = CodecFormatAdapter(CodecFormat.SAMPLE_FORMATS)
    }
}
