package com.quantumlabs.rfidreader

import android.content.res.ColorStateList
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.csl.rfidsdk.callbacks.BatteryCallback
import com.csl.rfidsdk.callbacks.RfidInventoryCallback
import com.csl.rfidsdk.callbacks.TriggerCallback
import com.csl.rfidsdk.config.RfidStopReason
import com.csl.rfidsdk.models.BatteryInfo
import com.csl.rfidsdk.models.RfidError
import com.csl.rfidsdk.models.RfidInventoryStats
import com.csl.rfidsdk.models.RfidTag

/**
 * Inventario RFID: pantalla de diagnóstico, lectura continua mientras se
 * mantiene el gatillo presionado. No llama a la API ni toca la base de
 * datos -- solo cuenta EPCs en el teléfono. Pita (una vez por EPC) la
 * primera vez que aparece cada etiqueta. El switch "Prefijos" filtra contra
 * la lista local de la pantalla Prefijos; la lista de lecturas se reinicia
 * cada vez que se entra a la pantalla o se cambia el switch.
 */
class InventarioActivity : AppCompatActivity() {

    private lateinit var rfidManager: com.csl.rfidsdk.RfidManager
    private lateinit var tarjetaConexion: View
    private lateinit var tvEstadoTarjeta: TextView
    private lateinit var chipConexion: TextView
    private lateinit var pildoraBateria: View
    private lateinit var ivBateria: ImageView
    private lateinit var tvBateria: TextView
    private lateinit var switchPrefijos: SwitchCompat
    private lateinit var tvTotalLecturas: TextView
    private lateinit var adapter: InventarioAdapter
    private lateinit var tonoGenerador: ToneGenerator

    private val lecturas = mutableListOf<LecturaInventario>()
    private val conteos = mutableMapOf<String, Int>()
    private val ultimaActualizacion = mutableMapOf<String, Long>()
    private var leyendo = false
    private var prefijosGuardados: Set<String> = emptySet()

    private val triggerCallback = object : TriggerCallback {
        override fun onTriggerStateChanged(pressed: Boolean) {
            runOnUiThread {
                if (pressed) iniciarLectura() else detenerLectura()
            }
        }
    }

    private val inventoryCallback = object : RfidInventoryCallback {
        override fun onTagRead(tag: RfidTag) {
            runOnUiThread { procesarLectura(tag.epc) }
        }

        override fun onInventoryRound(stats: RfidInventoryStats) = Unit
        override fun onInventoryStopped(reason: RfidStopReason) = Unit

        override fun onInventoryError(error: RfidError) {
            runOnUiThread {
                Toast.makeText(this@InventarioActivity, "Error de lectura: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }

        override fun onBatteryUpdate(batteryInfo: BatteryInfo) = Unit
    }

    private val batteryCallback = object : BatteryCallback {
        override fun onBatteryUpdate(batteryInfo: BatteryInfo) {
            runOnUiThread { BateriaPildora.actualizar(this@InventarioActivity, pildoraBateria, ivBateria, tvBateria, batteryInfo) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventario)
        // Evita que la pantalla se apague sola mientras se esta leyendo --
        // si se apaga a medio escaneo, Android pausa la lectura y hay que
        // volver a empezar (pedido del usuario, 2026-10-09).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rfidManager = (application as RfidApplication).rfidManager
        tonoGenerador = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)

        tarjetaConexion = findViewById(R.id.tarjetaConexion)
        tvEstadoTarjeta = findViewById(R.id.tvEstadoTarjeta)
        chipConexion = findViewById(R.id.chipConexion)
        pildoraBateria = findViewById(R.id.pildoraBateria)
        ivBateria = findViewById(R.id.ivBateria)
        tvBateria = findViewById(R.id.tvBateria)
        switchPrefijos = findViewById(R.id.switchPrefijos)
        tvTotalLecturas = findViewById(R.id.tvTotalLecturas)

        adapter = InventarioAdapter(lecturas)
        findViewById<RecyclerView>(R.id.lvInventario).apply {
            layoutManager = LinearLayoutManager(this@InventarioActivity)
            adapter = this@InventarioActivity.adapter
        }

        marcarLeyendo(false)
        switchPrefijos.setOnCheckedChangeListener { _, _ -> reiniciarLista() }
        findViewById<ImageButton>(R.id.btnRegresar).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (!rfidManager.isConnected) {
            Toast.makeText(this, "La lectora se desconectó", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        prefijosGuardados = Preferencias.prefijos(this)
        reiniciarLista()
        rfidManager.enableTrigger(triggerCallback, false)
        BateriaPildora.actualizar(this, pildoraBateria, ivBateria, tvBateria, rfidManager.getBatteryInfo())
        rfidManager.startBatteryMonitoring(batteryCallback)
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

    override fun onDestroy() {
        tonoGenerador.release()
        super.onDestroy()
    }

    private fun reiniciarLista() {
        conteos.clear()
        ultimaActualizacion.clear()
        lecturas.clear()
        adapter.notifyDataSetChanged()
        actualizarTotal()
    }

    private fun actualizarTotal() {
        tvTotalLecturas.text = getString(R.string.total_tags_leidos, conteos.size)
    }

    private fun iniciarLectura() {
        marcarLeyendo(true)
        rfidManager.startInventory(inventoryCallback)
    }

    private fun detenerLectura() {
        if (!leyendo) return
        rfidManager.stopInventory()
        marcarLeyendo(false)
        adapter.notifyDataSetChanged() // refresca los conteos finales
    }

    private fun procesarLectura(epcCrudo: String) {
        val epc = epcCrudo.uppercase()
        if (switchPrefijos.isChecked && prefijosGuardados.none { epc.startsWith(it) }) {
            return
        }

        val esNuevo = !conteos.containsKey(epc)
        conteos[epc] = (conteos[epc] ?: 0) + 1

        if (esNuevo) {
            tonoGenerador.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            lecturas.add(0, LecturaInventario(epc, conteos[epc]!!))
            adapter.notifyItemInserted(0)
            actualizarTotal()
            return
        }

        // La lectora reporta el mismo tag decenas de veces por segundo con el
        // gatillo presionado -- se refresca el contador con tope de 300ms por
        // EPC para no saturar la lista (mismo criterio que
        // MainActivity.marcarVistoDeNuevo).
        val ahora = System.currentTimeMillis()
        val ultima = ultimaActualizacion[epc] ?: 0L
        if (ahora - ultima < 300) return
        ultimaActualizacion[epc] = ahora

        val index = lecturas.indexOfFirst { it.epc == epc }
        if (index >= 0) {
            lecturas[index].conteo = conteos[epc]!!
            adapter.notifyItemChanged(index)
        }
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
            tvEstadoTarjeta.text = rfidManager.getConnectedReader()?.name ?: getString(R.string.lectora_conectada)
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
}
