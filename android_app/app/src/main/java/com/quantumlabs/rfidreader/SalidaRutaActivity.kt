package com.quantumlabs.rfidreader

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
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
import org.json.JSONObject

/**
 * Salida a Ruta, paso 1 "Esperando parabrisas" (docs/funcional.md,
 * Procedimiento A, pasos 1-3): a diferencia de Captura de Tags, aqui se
 * sigue escuchando mientras el gatillo esta presionado y, al soltarlo, se
 * toma el EPC con mayor RSSI visto (puede haber varias etiquetas de
 * camiones vecinos cerca). Con ese EPC se busca el camion
 * (GET /api/dispatch/lookup/{epc}); si tiene boletas activas se muestran
 * sus datos y el operador confirma con Aceptar o Cancelar.
 */
class SalidaRutaActivity : AppCompatActivity() {

    private lateinit var rfidManager: com.csl.rfidsdk.RfidManager
    private lateinit var tarjetaConexion: View
    private lateinit var tvEstadoTarjeta: TextView
    private lateinit var chipConexion: TextView
    private lateinit var tvUnidad: TextView
    private lateinit var contenedorMensaje: View
    private lateinit var tvMensajeTitulo: TextView
    private lateinit var tvMensajeSub: TextView
    private lateinit var contenedorCamion: View
    private lateinit var tvCamionNumero: TextView
    private lateinit var tvCamionPlaca: TextView
    private lateinit var tvCamionBoletas: TextView
    private lateinit var btnAceptar: Button

    private var leyendo = false
    private var epcDetectado: String? = null
    private var ticketsEncontrados: Int = 0
    private var ticketsJson: String = "[]"
    private val rssiPorEpc = mutableMapOf<String, Double>()

    private val triggerCallback = object : TriggerCallback {
        override fun onTriggerStateChanged(pressed: Boolean) {
            runOnUiThread {
                if (pressed) iniciarLectura() else detenerLectura()
            }
        }
    }

    private val inventoryCallback = object : RfidInventoryCallback {
        override fun onTagRead(tag: RfidTag) {
            val actual = rssiPorEpc[tag.epc]
            if (actual == null || tag.rssi > actual) {
                rssiPorEpc[tag.epc] = tag.rssi
            }
        }

        override fun onInventoryRound(stats: RfidInventoryStats) = Unit
        override fun onInventoryStopped(reason: RfidStopReason) = Unit

        override fun onInventoryError(error: RfidError) {
            runOnUiThread { Toast.makeText(this@SalidaRutaActivity, "Error de lectura: ${error.message}", Toast.LENGTH_LONG).show() }
        }

        override fun onBatteryUpdate(batteryInfo: BatteryInfo) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_salida_ruta)
        // Evita que la pantalla se apague sola mientras se esta leyendo --
        // si se apaga a medio escaneo, Android pausa la lectura y hay que
        // volver a empezar (pedido del usuario, 2026-10-09).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rfidManager = (application as RfidApplication).rfidManager
        tarjetaConexion = findViewById(R.id.tarjetaConexion)
        tvEstadoTarjeta = findViewById(R.id.tvEstadoTarjeta)
        chipConexion = findViewById(R.id.chipConexion)
        tvUnidad = findViewById(R.id.tvUnidad)
        contenedorMensaje = findViewById(R.id.contenedorMensaje)
        tvMensajeTitulo = findViewById(R.id.tvMensajeTitulo)
        tvMensajeSub = findViewById(R.id.tvMensajeSub)
        contenedorCamion = findViewById(R.id.contenedorCamion)
        tvCamionNumero = findViewById(R.id.tvCamionNumero)
        tvCamionPlaca = findViewById(R.id.tvCamionPlaca)
        tvCamionBoletas = findViewById(R.id.tvCamionBoletas)
        btnAceptar = findViewById(R.id.btnAceptar)

        marcarLeyendo(false)

        findViewById<ImageButton>(R.id.btnRegresar).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCancelar).setOnClickListener { limpiarTodo() }
        btnAceptar.setOnClickListener { aceptar() }
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

    private fun iniciarLectura() {
        if (epcDetectado != null) return // ya hay una unidad esperando Aceptar/Cancelar
        rssiPorEpc.clear()
        marcarLeyendo(true)
        rfidManager.startInventory(inventoryCallback)
    }

    private fun detenerLectura() {
        if (!leyendo) return
        rfidManager.stopInventory()
        marcarLeyendo(false)
        if (rssiPorEpc.isEmpty()) {
            Toast.makeText(this, R.string.no_se_detecto_etiqueta, Toast.LENGTH_SHORT).show()
            return
        }
        val epc = rssiPorEpc.maxByOrNull { it.value }!!.key
        rssiPorEpc.clear()
        epcDetectado = epc
        tvUnidad.text = epc
        tvUnidad.setTextColor(ContextCompat.getColor(this, R.color.color_text))
        tvMensajeTitulo.text = getString(R.string.buscando_camion)
        tvMensajeSub.text = ""
        buscarCamion(epc)
    }

    private fun buscarCamion(epc: String) {
        val servidor = ServerClient("${Preferencias.ip(this)}:${Preferencias.puerto(this)}")
        servidor.buscarCamion(epc) { ok, codigo, cuerpo ->
            runOnUiThread {
                if (!ok) {
                    Toast.makeText(this, "No se pudo buscar el camión: $cuerpo", Toast.LENGTH_LONG).show()
                    limpiarTodo()
                    return@runOnUiThread
                }
                val json = JSONObject(cuerpo)
                if (codigo == 404) {
                    mostrarAdvertencia(json.optString("message", "Esa etiqueta no está registrada como camión."))
                    return@runOnUiThread
                }
                val alarma = json.optString("alarm", "")
                if (alarma.isNotEmpty()) {
                    mostrarAdvertencia(json.optString("message", "El camión no está disponible."))
                    return@runOnUiThread
                }
                val camion = json.optJSONObject("truck")
                val tickets = json.optJSONArray("tickets")
                ticketsEncontrados = tickets?.length() ?: 0
                ticketsJson = (tickets ?: org.json.JSONArray()).toString()
                mostrarCamionEncontrado(camion)
            }
        }
    }

    private fun mostrarCamionEncontrado(camion: JSONObject?) {
        tvCamionNumero.text = camion?.optString("unit_number") ?: "?"
        tvCamionPlaca.text = camion?.optString("plate") ?: "?"
        tvCamionBoletas.text = ticketsEncontrados.toString()
        contenedorMensaje.visibility = View.GONE
        contenedorCamion.visibility = View.VISIBLE
    }

    private fun mostrarAdvertencia(mensaje: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.tag_ya_existe_titulo)
            .setMessage(mensaje)
            .setPositiveButton(R.string.aceptar) { _, _ -> limpiarTodo() }
            .setCancelable(false)
            .show()
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

    private fun limpiarTodo() {
        epcDetectado = null
        ticketsEncontrados = 0
        rssiPorEpc.clear()
        tvUnidad.text = getString(R.string.esperando_parabrisas)
        tvUnidad.setTextColor(ContextCompat.getColor(this, R.color.color_text_muted))
        contenedorCamion.visibility = View.GONE
        contenedorMensaje.visibility = View.VISIBLE
        tvMensajeTitulo.text = getString(R.string.manten_presionado_gatillo)
        tvMensajeSub.text = getString(R.string.cerca_del_parabrisas)
    }

    private fun aceptar() {
        if (epcDetectado == null || contenedorCamion.visibility != View.VISIBLE) {
            Toast.makeText(this, R.string.primero_detecta_unidad, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            BoletasActivity.crearIntent(
                this,
                epc = epcDetectado!!,
                unidad = tvCamionNumero.text.toString(),
                placa = tvCamionPlaca.text.toString(),
                ticketsJson = ticketsJson,
            )
        )
        finish()
    }
}
