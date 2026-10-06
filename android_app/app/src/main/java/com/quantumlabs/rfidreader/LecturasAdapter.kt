package com.quantumlabs.rfidreader

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

enum class EstadoEnvio { ENVIANDO, ENVIADO, ERROR }

data class Lectura(
    val epc: String,
    var hora: String,
    var estado: EstadoEnvio,
    var detalleError: String? = null,
    var conteo: Int = 1,
)

class LecturasAdapter(private val lecturas: MutableList<Lectura>) :
    RecyclerView.Adapter<LecturasAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val punto: android.view.View = view.findViewById(R.id.puntoEstado)
        val epc: TextView = view.findViewById(R.id.tvEpc)
        val hora: TextView = view.findViewById(R.id.tvHora)
        val conteo: TextView = view.findViewById(R.id.tvConteo)
        val estado: TextView = view.findViewById(R.id.tvEstadoEnvio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lectura, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lecturas[position]
        val context = holder.itemView.context
        holder.epc.text = item.epc
        holder.hora.text = item.hora
        holder.conteo.text = "×${item.conteo}"
        holder.conteo.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_border))

        val (colorRes, texto) = when (item.estado) {
            EstadoEnvio.ENVIANDO -> R.color.color_warn to "enviando…"
            EstadoEnvio.ENVIADO -> R.color.color_ok to "enviado"
            EstadoEnvio.ERROR -> R.color.color_fail to "error"
        }
        val color = ContextCompat.getColor(context, colorRes)
        holder.punto.backgroundTintList = ColorStateList.valueOf(color)
        holder.estado.text = texto
        holder.estado.setTextColor(color)
    }

    override fun getItemCount(): Int = lecturas.size
}
