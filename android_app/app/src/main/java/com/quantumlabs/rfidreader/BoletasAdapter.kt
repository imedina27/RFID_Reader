package com.quantumlabs.rfidreader

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class LineaBoleta(val productName: String, val pallets: Int)

data class BoletaUi(
    val id: Int,
    val folio: String,
    val customer: String,
    val lineas: List<LineaBoleta>,
    var seleccionada: Boolean = false,
)

/** Lista de boletas activas del camión (Salida a Ruta, paso 2): cada
 * renglón se puede marcar/desmarcar tocándolo completo (no solo la
 * casilla), para un toque más grande en campo. */
class BoletasAdapter(
    private val boletas: List<BoletaUi>,
    private val onCambioSeleccion: () -> Unit,
) : RecyclerView.Adapter<BoletasAdapter.BoletaViewHolder>() {

    class BoletaViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val raiz: android.view.View = view.findViewById(R.id.raizTicket)
        val checkbox: CheckBox = view.findViewById(R.id.chkBoleta)
        val tvFolio: TextView = view.findViewById(R.id.tvFolio)
        val tvCliente: TextView = view.findViewById(R.id.tvCliente)
        val contenedorLineas: LinearLayout = view.findViewById(R.id.contenedorLineas)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoletaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_boleta, parent, false)
        return BoletaViewHolder(view)
    }

    override fun onBindViewHolder(holder: BoletaViewHolder, position: Int) {
        val boleta = boletas[position]
        holder.tvFolio.text = boleta.folio
        holder.tvCliente.text = boleta.customer
        holder.checkbox.isChecked = boleta.seleccionada
        holder.raiz.setBackgroundResource(if (boleta.seleccionada) R.drawable.shape_ticket_selected else R.drawable.shape_ticket_normal)

        holder.contenedorLineas.removeAllViews()
        for (linea in boleta.lineas) {
            val tv = TextView(holder.contenedorLineas.context)
            tv.text = "${linea.productName} — ${linea.pallets} pallet${if (linea.pallets == 1) "" else "s"}"
            tv.setTextColor(holder.contenedorLineas.context.getColor(R.color.color_text_muted))
            tv.textSize = 13f
            holder.contenedorLineas.addView(tv)
        }

        holder.raiz.setOnClickListener {
            boleta.seleccionada = !boleta.seleccionada
            notifyItemChanged(position)
            onCambioSeleccion()
        }
    }

    override fun getItemCount(): Int = boletas.size
}
