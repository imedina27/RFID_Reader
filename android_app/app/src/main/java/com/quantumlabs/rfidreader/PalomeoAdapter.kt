package com.quantumlabs.rfidreader

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class ProductoPalomeo(
    val productId: Int,
    val nombre: String,
    val esperado: Int,
    var leido: Int,
    var diferencia: Int,
)

/** Palomeo de Salida a Ruta (paso 3, docs/funcional.md): una linea por
 * producto esperado, con color segun la diferencia (leido - esperado):
 * verde completo, amarillo falta, rojo sobra/no solicitado. */
class PalomeoAdapter(private val productos: MutableList<ProductoPalomeo>) :
    RecyclerView.Adapter<PalomeoAdapter.ProductoViewHolder>() {

    class ProductoViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val raiz: android.view.View = view.findViewById(R.id.raizFila)
        val badge: FrameLayout = view.findViewById(R.id.badge)
        val tvBadgeIcono: TextView = view.findViewById(R.id.tvBadgeIcono)
        val tvProducto: TextView = view.findViewById(R.id.tvProducto)
        val tvDetalle: TextView = view.findViewById(R.id.tvDetalle)
        val tvLeido: TextView = view.findViewById(R.id.tvLeido)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_producto_palomeo, parent, false)
        return ProductoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        val contexto = holder.raiz.context
        val p = productos[position]
        holder.tvProducto.text = p.nombre

        when {
            p.diferencia == 0 -> {
                holder.raiz.setBackgroundResource(R.drawable.shape_row_ok)
                holder.badge.setBackgroundResource(R.drawable.shape_badge_ok)
                holder.tvBadgeIcono.text = "✓"
                holder.tvDetalle.text = "Esperado ${p.esperado}"
                holder.tvLeido.setTextColor(ContextCompat.getColor(contexto, R.color.color_ok))
            }
            p.diferencia < 0 -> {
                holder.raiz.setBackgroundResource(R.drawable.shape_row_warn)
                holder.badge.setBackgroundResource(R.drawable.shape_badge_warn)
                holder.tvBadgeIcono.text = "!"
                holder.tvDetalle.text = "Esperado ${p.esperado} — falta ${-p.diferencia}"
                holder.tvLeido.setTextColor(ContextCompat.getColor(contexto, R.color.color_warn))
            }
            else -> {
                holder.raiz.setBackgroundResource(R.drawable.shape_row_fail)
                holder.badge.setBackgroundResource(R.drawable.shape_badge_fail)
                holder.tvBadgeIcono.text = "+"
                holder.tvDetalle.text = if (p.esperado == 0) "No solicitado — sobran ${p.diferencia}" else "Esperado ${p.esperado} — sobran ${p.diferencia}"
                holder.tvLeido.setTextColor(ContextCompat.getColor(contexto, R.color.color_fail))
            }
        }
        holder.tvLeido.text = p.leido.toString()
    }

    override fun getItemCount(): Int = productos.size

    fun actualizar(nuevos: List<ProductoPalomeo>) {
        productos.clear()
        productos.addAll(nuevos)
        notifyDataSetChanged()
    }
}
