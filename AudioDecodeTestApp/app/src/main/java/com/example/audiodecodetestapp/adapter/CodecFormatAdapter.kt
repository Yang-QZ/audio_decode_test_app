package com.example.audiodecodetestapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.audiodecodetestapp.R
import com.example.audiodecodetestapp.model.CodecFormat

class CodecFormatAdapter(
    private val formats: List<CodecFormat>
) : RecyclerView.Adapter<CodecFormatAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.format_name)
        val meta: TextView = view.findViewById(R.id.format_meta)
        val desc: TextView = view.findViewById(R.id.format_desc)
        val badge: TextView = view.findViewById(R.id.format_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_codec_format, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val format = formats[position]
        holder.name.text = format.name
        holder.meta.text = format.container + " · " + format.mime
        holder.desc.text = format.description
        holder.badge.text = if (format.encoder) {
            holder.itemView.context.getString(R.string.badge_encode)
        } else {
            holder.itemView.context.getString(R.string.badge_decode)
        }
    }

    override fun getItemCount(): Int = formats.size
}
