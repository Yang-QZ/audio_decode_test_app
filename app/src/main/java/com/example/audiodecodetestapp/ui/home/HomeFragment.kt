package com.example.audiodecodetestapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audiodecodetestapp.R
import com.example.audiodecodetestapp.adapter.CodecFormatAdapter
import com.example.audiodecodetestapp.model.CodecFormat
import com.example.audiodecodetestapp.ui.detail.FormatDetailFragment

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_formats)
        recycler.layoutManager = GridLayoutManager(requireContext(), 1)
        recycler.adapter = CodecFormatAdapter(CodecFormat.SAMPLE_FORMATS) { format ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FormatDetailFragment.newInstance(format))
                .addToBackStack(null)
                .commit()
        }
    }
}
