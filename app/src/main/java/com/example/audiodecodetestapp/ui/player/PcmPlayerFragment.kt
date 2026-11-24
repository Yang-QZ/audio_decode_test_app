package com.example.audiodecodetestapp.ui.player

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.audiodecodetestapp.R
import com.example.audiodecodetestapp.audio.EnginePlayer
import com.example.audiodecodetestapp.audio.EnginePlayerFactory
import com.example.audiodecodetestapp.audio.WavInfo
import com.example.audiodecodetestapp.audio.WavParser
import com.example.audiodecodetestapp.model.PlaybackEngine
import com.google.android.material.button.MaterialButton

/**
 * PCM / WAV 播放页：选择 WAV 文件 → 解析并显示格式信息 → 用指定播放接口播放。
 */
class PcmPlayerFragment : Fragment() {

    companion object {
        private const val ARG_ENGINE = "engine"

        fun newInstance(engine: PlaybackEngine): PcmPlayerFragment {
            return PcmPlayerFragment().apply {
                arguments = Bundle().apply { putString(ARG_ENGINE, engine.name) }
            }
        }
    }

    private lateinit var engine: PlaybackEngine
    private var player: EnginePlayer? = null
    private var currentUri: Uri? = null
    private var wavInfo: WavInfo? = null

    private lateinit var fileNameView: TextView
    private lateinit var infoCard: View
    private lateinit var statusView: TextView
    private lateinit var playButton: MaterialButton
    private lateinit var stopButton: MaterialButton

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) handlePickedFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = arguments?.getString(ARG_ENGINE)
            ?.let { runCatching { PlaybackEngine.valueOf(it) }.getOrNull() }
            ?: PlaybackEngine.AUDIO_TRACK
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pcm_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.pcm_engine_label).text =
            getString(R.string.pcm_engine_label, engine.title)
        fileNameView = view.findViewById(R.id.pcm_file_name)
        infoCard = view.findViewById(R.id.card_info)
        statusView = view.findViewById(R.id.pcm_status)
        playButton = view.findViewById(R.id.btn_play)
        stopButton = view.findViewById(R.id.btn_stop)

        view.findViewById<MaterialButton>(R.id.btn_select_file).setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }
        playButton.setOnClickListener { startPlayback() }
        stopButton.setOnClickListener { stopPlayback() }

        wavInfo?.let { bindInfo(it) }
    }

    private fun handlePickedFile(uri: Uri) {
        currentUri = uri
        fileNameView.text = getString(R.string.pcm_file_picked, queryDisplayName(uri))
        val info = WavParser.parse(requireContext(), uri)
        if (info == null) {
            wavInfo = null
            infoCard.visibility = View.GONE
            playButton.isEnabled = false
            stopButton.isEnabled = false
            statusView.text = getString(R.string.pcm_file_invalid)
            return
        }
        wavInfo = info
        bindInfo(info)
        infoCard.visibility = View.VISIBLE
        playButton.isEnabled = true
        statusView.text = getString(R.string.pcm_status_idle)
    }

    private fun bindInfo(info: WavInfo) {
        val view = view ?: return
        view.findViewById<TextView>(R.id.pcm_info_sample_rate).text =
            "${getString(R.string.pcm_info_sample_rate)}：${info.sampleRate} Hz"
        view.findViewById<TextView>(R.id.pcm_info_channels).text =
            "${getString(R.string.pcm_info_channels)}：${info.channelLabel}"
        view.findViewById<TextView>(R.id.pcm_info_bits).text =
            "${getString(R.string.pcm_info_bits)}：${info.bitsPerSample} bit"
        view.findViewById<TextView>(R.id.pcm_info_format).text =
            "${getString(R.string.pcm_info_format)}：${info.formatLabel}"
        view.findViewById<TextView>(R.id.pcm_info_duration).text =
            "${getString(R.string.pcm_info_duration)}：${formatDuration(info.durationMs)}"
        view.findViewById<TextView>(R.id.pcm_info_size).text =
            "${getString(R.string.pcm_info_size)}：${formatSize(info.dataSize)}"
    }

    private fun startPlayback() {
        val uri = currentUri ?: return
        val info = wavInfo ?: return
        stopPlayback(silent = true)
        val created = EnginePlayerFactory.create(engine, requireContext())
        try {
            created.play(uri, info)
            player = created
            statusView.text = getString(R.string.pcm_status_playing)
            stopButton.isEnabled = true
        } catch (e: Exception) {
            created.release()
            player = null
            statusView.text =
                getString(R.string.pcm_status_error, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun stopPlayback(silent: Boolean = false) {
        player?.release()
        player = null
        stopButton.isEnabled = false
        if (!silent) statusView.text = getString(R.string.pcm_status_stopped)
    }

    private fun queryDisplayName(uri: Uri): String {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: uri.lastPathSegment ?: "unknown"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "unknown"
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return if (kb >= 1024) "%.2f MB".format(kb / 1024) else "%.1f KB".format(kb)
    }

    override fun onDestroyView() {
        player?.release()
        player = null
        super.onDestroyView()
    }
}
