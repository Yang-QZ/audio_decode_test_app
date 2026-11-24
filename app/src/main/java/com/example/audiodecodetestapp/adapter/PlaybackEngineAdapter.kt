package com.example.audiodecodetestapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.audiodecodetestapp.R
import com.example.audiodecodetestapp.model.PlaybackEngine

class PlaybackEngineAdapter(
    private val engines: List<PlaybackEngine>,
    private val playable: Boolean,
    private val onClick: (PlaybackEngine) -> Unit
) : RecyclerView.Adapter<PlaybackEngineAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val index: TextView = view.findViewById(R.id.engine_index)
        val title: TextView = view.findViewById(R.id.engine_title)
        val subtitle: TextView = view.findViewById(R.id.engine_subtitle)
        val action: TextView = view.findViewById(R.id.engine_action)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playback_engine, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val engine = engines[position]
        holder.index.text = (position + 1).toString()
        holder.title.text = engine.title
        holder.subtitle.text = engine.subtitle
        holder.action.text = holder.itemView.context.getString(
            if (playable) R.string.detail_action_enter else R.string.detail_action_pending
        )
        holder.itemView.setOnClickListener { onClick(engine) }
    }

    override fun getItemCount(): Int = engines.size
}
