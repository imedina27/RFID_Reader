package com.quantumlabs.rfidreader

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.csl.rfidsdk.models.BatteryInfo

/**
 * Pinta la píldora de batería de la lectora (ícono con nivel + porcentaje) en
 * la tarjeta de conexión. El color/ícono cambia según el nivel: verde (>=50%),
 * naranja (20-49%) o rojo (<20%). El SDK del CS108-2 no reporta si está
 * cargando (ni por BLE ni en ningún otro lado de CsLibrary4A), así que no se
 * muestra ese estado -- solo nivel + porcentaje (pedido del usuario, 2026-10-09).
 * Se usa en las 4 pantallas que tienen esta tarjeta: Menú, Captura de Tags,
 * Salida a Ruta e Inventario RFID.
 */
object BateriaPildora {

    fun actualizar(context: Context, pildora: View, icono: ImageView, texto: TextView, info: BatteryInfo?) {
        if (info == null || !info.isValid) {
            pildora.visibility = View.GONE
            return
        }
        val color = ContextCompat.getColor(context, when {
            info.percentage >= 50 -> R.color.color_ok
            info.percentage >= 20 -> R.color.color_warn
            else -> R.color.color_fail
        })
        val recursoIcono = when {
            info.percentage >= 50 -> R.drawable.ic_battery_full
            info.percentage >= 20 -> R.drawable.ic_battery_medium
            else -> R.drawable.ic_battery_low
        }
        icono.setImageResource(recursoIcono)
        icono.setColorFilter(color)
        texto.text = "${info.percentage}%"
        texto.setTextColor(color)
        pildora.backgroundTintList = ColorStateList.valueOf(withAlpha(color, 40))
        pildora.visibility = View.VISIBLE
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)
}
