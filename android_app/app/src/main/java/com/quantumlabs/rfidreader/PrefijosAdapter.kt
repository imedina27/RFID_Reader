package com.quantumlabs.rfidreader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PrefijosAdapter(
    private val prefijos: MutableList<String>,
    private val onEliminar: (String) -> Unit,
) : RecyclerView.Adapter<PrefijosAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val texto: TextView = view.findViewById(R.id.tvPrefijo)
        val eliminar: TextView = view.findViewById(R.id.tvEliminarPrefijo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_prefijo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val prefijo = prefijos[position]
        holder.texto.text = prefijo
        holder.eliminar.setOnClickListener { onEliminar(prefijo) }
    }

    override fun getItemCount(): Int = prefijos.size
}
