package com.example.audiodecodetestapp.ui.detail

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audiodecodetestapp.R
import com.example.audiodecodetestapp.adapter.PlaybackEngineAdapter
import com.example.audiodecodetestapp.model.CodecFormat
import com.example.audiodecodetestapp.model.PlaybackEngine
import com.example.audiodecodetestapp.ui.player.PcmPlayerFragment

/**
 * 格式详情（二级页面）：列出该编码格式在默认配置下可用的播放接口。
 *
 * - WAV/PCM：每个接口都可以进入实际播放页面；
 * - 其他格式：当前仅展示可用接口，播放实现待后续版本。
 */
class FormatDetailFragment : Fragment() {

    companion object {
        private const val ARG_FORMAT_NAME = "format_name"

        fun newInstance(format: CodecFormat): FormatDetailFragment {
            return FormatDetailFragment().apply {
                arguments = Bundle().apply { putString(ARG_FORMAT_NAME, format.name) }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_format_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val name = arguments?.getString(ARG_FORMAT_NAME).orEmpty()
        val format = CodecFormat.findByName(name)
        if (format == null) {
            parentFragmentManager.popBackStack()
            return
        }

        view.findViewById<TextView>(R.id.detail_name).text = format.name
        view.findViewById<TextView>(R.id.detail_meta).text =
            format.container + " · " + format.mime
        view.findViewById<TextView>(R.id.detail_desc).text = format.description

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_engines)
        val empty = view.findViewById<TextView>(R.id.detail_empty)

        // AAudio 需要 Android 8.0(API 26) 及以上
        val engines = format.engines.filter { engine ->
            engine != PlaybackEngine.AAUDIO || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        }

        if (engines.isEmpty()) {
            recycler.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }

        empty.visibility = View.GONE
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = PlaybackEngineAdapter(engines, format.playable) { engine ->
            if (format.name == "WAV / PCM") {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, PcmPlayerFragment.newInstance(engine))
                    .addToBackStack(null)
                    .commit()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.detail_engine_pending, engine.title),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
