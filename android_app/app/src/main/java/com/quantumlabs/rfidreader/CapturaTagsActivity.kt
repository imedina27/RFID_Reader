package com.quantumlabs.rfidreader

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.csl.rfidsdk.callbacks.RfidInventoryCallback
import com.csl.rfidsdk.callbacks.TriggerCallback
import com.csl.rfidsdk.config.RfidStopReason
import com.csl.rfidsdk.models.BatteryInfo
import com.csl.rfidsdk.models.RfidError
import com.csl.rfidsdk.models.RfidInventoryStats
import com.csl.rfidsdk.models.RfidTag

/**
 * Captura de Tags, modo pallet (docs/funcional.md, Procedimiento B): un EPC
 * a la vez -- se lee, se elige el producto y se guarda con POST
 * /api/tags/batch. El producto se queda seleccionado entre lecturas (pensado
 * para capturar varios pallets seguidos del mismo producto); "Cancelar" es
 * la unica forma de volver a elegir producto desde cero.
 */
class CapturaTagsActivity : AppCompatActivity() {

    private lateinit var rfidManager: com.csl.rfidsdk.RfidManager
    private lateinit var tarjetaConexion: View
    private lateinit var tvEstadoTarjeta: TextView
    private lateinit var chipConexion: TextView
    private lateinit var etTag: EditText
    private lateinit var contenedorProducto: View
    private lateinit var spProducto: Spinner
    private lateinit var adapterProductos: ArrayAdapter<String>
    private lateinit var btnAceptar: Button

    private var productos: List<Producto> = emptyList()
    private var leyendo = false

    private val triggerCallback = object : TriggerCallback {
        override fun onTriggerStateChanged(pressed: Boolean) {
            runOnUiThread {
                if (pressed) iniciarLectura() else detenerLectura()
            }
        }
    }

    private val inventoryCallback = object : RfidInventoryCallback {
        override fun onTagRead(tag: RfidTag) {
            runOnUiThread {
                rfidManager.stopInventory()
                marcarLeyendo(false)
                etTag.setText(tag.epc)
                contenedorProducto.visibility = View.VISIBLE
            }
        }

        override fun onInventoryRound(stats: RfidInventoryStats) = Unit
        override fun onInventoryStopped(reason: RfidStopReason) = Unit

        override fun onInventoryError(error: RfidError) {
            runOnUiThread { Toast.makeText(this@CapturaTagsActivity, "Error de lectura: ${error.message}", Toast.LENGTH_LONG).show() }
        }

        override fun onBatteryUpdate(batteryInfo: BatteryInfo) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_captura_tags)

        rfidManager = (application as RfidApplication).rfidManager
        tarjetaConexion = findViewById(R.id.tarjetaConexion)
        tvEstadoTarjeta = findViewById(R.id.tvEstadoTarjeta)
        chipConexion = findViewById(R.id.chipConexion)
        etTag = findViewById(R.id.etTag)
        contenedorProducto = findViewById(R.id.contenedorProducto)
        spProducto = findViewById(R.id.spProducto)

        adapterProductos = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf(getString(R.string.selecciona_producto)))
        adapterProductos.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spProducto.adapter = adapterProductos

        marcarLeyendo(false)
        cargarProductos()

        btnAceptar = findViewById(R.id.btnAceptar)
        findViewById<ImageButton>(R.id.btnRegresar).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCancelar).setOnClickListener { limpiarFormulario() }
        btnAceptar.setOnClickListener { aceptar() }

        etTag.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (rfidManager.isConnected) {
            rfidManager.enableTrigger(triggerCallback, false)
        } else {
            Toast.makeText(this, "La lectora se desconectó", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (rfidManager.isConnected) {
            rfidManager.disableTrigger()
        }
        if (leyendo) {
            rfidManager.stopInventory()
            marcarLeyendo(false)
        }
    }

    private fun cargarProductos() {
        val servidor = ServerClient("${Preferencias.ip(this)}:${Preferencias.puerto(this)}")
        servidor.obtenerProductos { ok, lista ->
            runOnUiThread {
                if (!ok) {
                    Toast.makeText(this, R.string.no_se_pudieron_cargar_productos, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                productos = lista
                adapterProductos.clear()
                adapterProductos.add(getString(R.string.selecciona_producto))
                adapterProductos.addAll(lista.map { it.nombre })
            }
        }
    }

    private fun iniciarLectura() {
        if (etTag.text.isNotBlank()) return
        marcarLeyendo(true)
        rfidManager.startInventory(inventoryCallback)
    }

    private fun detenerLectura() {
        if (!leyendo) return
        rfidManager.stopInventory()
        marcarLeyendo(false)
    }

    private fun marcarLeyendo(activo: Boolean) {
        leyendo = activo
        if (activo) {
            tarjetaConexion.setBackgroundResource(R.drawable.shape_card_leyendo)
            tvEstadoTarjeta.text = getString(R.string.leyendo_punto)
            tvEstadoTarjeta.setTextColor(ContextCompat.getColor(this, R.color.color_on_reading))
            chipConexion.text = getString(R.string.leyendo_punto)
        } else {
            tarjetaConexion.setBackgroundResource(R.drawable.shape_card)
            tvEstadoTarjeta.text = getString(R.string.lectora_conectada)
            tvEstadoTarjeta.setTextColor(ContextCompat.getColor(this, R.color.color_text))
            chipConexion.text = "Conectado"
        }
        val color = ContextCompat.getColor(this, if (activo) R.color.color_on_reading else R.color.color_ok)
        chipConexion.setTextColor(color)
        chipConexion.backgroundTintList = ColorStateList.valueOf(
            if (activo) withAlpha(ContextCompat.getColor(this, R.color.color_on_reading), 60)
            else withAlpha(ContextCompat.getColor(this, R.color.color_ok), 40)
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    /** Borra todo, incluido el producto (botón "Cancelar": el usuario quiere
     * empezar de cero, por ejemplo si leyó la etiqueta equivocada). */
    private fun limpiarFormulario() {
        spProducto.setSelection(0)
        contenedorProducto.visibility = View.GONE
        limpiarTag()
    }

    /** Borra solo el EPC leído; el producto se queda seleccionado para la
     * siguiente etiqueta (lo normal es capturar varios pallets seguidos del
     * mismo producto, pedido del usuario 2026-10-06). */
    private fun limpiarTag() {
        etTag.setText("")
        etTag.requestFocus()
    }

    private fun aceptar() {
        val epc = etTag.text.toString().trim().uppercase()
        if (epc.isEmpty()) {
            Toast.makeText(this, R.string.falta_leer_tag, Toast.LENGTH_SHORT).show()
            return
        }
        val posicion = spProducto.selectedItemPosition
        if (posicion <= 0 || posicion > productos.size) {
            Toast.makeText(this, R.string.falta_seleccionar_producto, Toast.LENGTH_SHORT).show()
            return
        }
        val producto = productos[posicion - 1]
        val servidor = ServerClient("${Preferencias.ip(this)}:${Preferencias.puerto(this)}")
        btnAceptar.isEnabled = false
        servidor.capturarTagPallet(producto.id, epc) { ok, resultado, detalle ->
            runOnUiThread {
                btnAceptar.isEnabled = true
                if (!ok) {
                    // Error de red/servidor: no se sabe si se guardo o no, asi
                    // que no se borra el formulario -- el usuario puede volver
                    // a intentar sin tener que releer la etiqueta.
                    Toast.makeText(this, "No se pudo guardar: $detalle", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                if (resultado == "created") {
                    mostrarAvisoGuardado()
                    limpiarTag()
                } else {
                    mostrarAdvertenciaTagExistente(resultado)
                }
            }
        }
    }

    /** "Guardado correcto": aviso que desaparece solo despues de 3 segundos. */
    private fun mostrarAvisoGuardado() {
        val aviso = Toast.makeText(this, R.string.tag_guardado, Toast.LENGTH_LONG)
        aviso.show()
        Handler(Looper.getMainLooper()).postDelayed({ aviso.cancel() }, 3000)
    }

    /** El tag ya existe en la base de datos (en cualquiera de sus formas):
     * ventana de advertencia que el usuario debe cerrar con Aceptar; recien
     * ahi se limpia el formulario para la siguiente captura. */
    private fun mostrarAdvertenciaTagExistente(resultado: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.tag_ya_existe_titulo)
            .setMessage(mensajeTagExistente(resultado))
            .setPositiveButton(R.string.aceptar) { _, _ -> limpiarTag() }
            .setCancelable(false)
            .show()
    }

    private fun mensajeTagExistente(resultado: String): String = when (resultado) {
        "already_captured", "already_captured_other_product" -> "Etiqueta ya tiene asignado un producto"
        "is_truck_tag" -> "Esta etiqueta es de un camión, no de un pallet."
        "already_dispatched" -> "Esta etiqueta ya fue despachada en una salida a ruta."
        "invalid_prefix" -> "Esta etiqueta no pertenece a este proyecto (prefijo no válido)."
        else -> "Esta etiqueta ya está registrada en la base de datos."
    }
}
