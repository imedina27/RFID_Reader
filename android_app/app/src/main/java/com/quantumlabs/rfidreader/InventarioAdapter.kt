package com.quantumlabs.rfidreader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class LecturaInventario(val epc: String, var conteo: Int = 1)

class InventarioAdapter(private val lecturas: MutableList<LecturaInventario>) :
    RecyclerView.Adapter<InventarioAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val epc: TextView = view.findViewById(R.id.tvEpc)
        val conteo: TextView = view.findViewById(R.id.tvConteo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inventario, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lecturas[position]
        holder.epc.text = item.epc
        holder.conteo.text = "×${item.conteo}"
    }

    override fun getItemCount(): Int = lecturas.size
}
