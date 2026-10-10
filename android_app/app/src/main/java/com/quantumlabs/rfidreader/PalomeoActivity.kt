package com.quantumlabs.rfidreader

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.csl.rfidsdk.callbacks.RfidInventoryCallback
import com.csl.rfidsdk.callbacks.TriggerCallback
import com.csl.rfidsdk.config.RfidStopReason
import com.csl.rfidsdk.models.BatteryInfo
import com.csl.rfidsdk.models.RfidError
import com.csl.rfidsdk.models.RfidInventoryStats
import com.csl.rfidsdk.models.RfidTag
import org.json.JSONObject

/**
 * Salida a Ruta, paso 3 "Palomeo" (docs/funcional.md, Procedimiento A,
 * pasos 5-7): lectura continua (gatillo mantenido, varias pasadas, se
 * acumulan etiquetas unicas) comparada en vivo contra lo esperado por
 * producto. Al no cuadrar "Finalizar lectura" el operador elige "Aceptar"
 * (la unidad regresa a la zona de carga) o "Autorizar salida" (motivo +
 * nombre). Con solo faltante, la primera vez "Aceptar" no cancela nada --
 * deja seguir leyendo; con sobrante, o la segunda vez con faltante, si
 * cancela la salida de una vez (decision del usuario 2026-10-06).
 */
class PalomeoActivity : AppCompatActivity() {

    private lateinit var rfidManager: com.csl.rfidsdk.RfidManager
    private lateinit var tvCamion: TextView
    private lateinit var chipLeidas: TextView
    private lateinit var btnFinalizar: Button
    private lateinit var adapter: PalomeoAdapter

    private var dispatchId: Int = -1
    private var unidad: String = "?"
    private var placa: String = "?"
    private var leyendo = false
    private var yaFalloSoloFaltante = false
    private val epcsEnviados = mutableSetOf<String>()
    private val problemasAvisados = mutableSetOf<String>()

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
                if (epcsEnviados.add(tag.epc)) {
                    enviarLectura(tag.epc)
                }
            }
        }

        override fun onInventoryRound(stats: RfidInventoryStats) = Unit
        override fun onInventoryStopped(reason: RfidStopReason) = Unit

        override fun onInventoryError(error: RfidError) {
            runOnUiThread { Toast.makeText(this@PalomeoActivity, "Error de lectura: ${error.message}", Toast.LENGTH_LONG).show() }
        }

        override fun onBatteryUpdate(batteryInfo: BatteryInfo) = Unit
    }

    private val abrirAutorizar = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == RESULT_OK) {
            // Ya quedo cerrada con diferencia autorizada; no hay nada mas
            // que hacer en Salida a Ruta.
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_palomeo)
        // Evita que la pantalla se apague sola mientras se esta leyendo --
        // si se apaga a medio escaneo, Android pausa la lectura y hay que
        // volver a empezar (pedido del usuario, 2026-10-09).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rfidManager = (application as RfidApplication).rfidManager
        dispatchId = intent.getIntExtra(EXTRA_DISPATCH_ID, -1)
        unidad = intent.getStringExtra(EXTRA_TRUCK_UNIT) ?: "?"
        placa = intent.getStringExtra(EXTRA_TRUCK_PLATE) ?: "?"

        tvCamion = findViewById(R.id.tvCamion)
        chipLeidas = findViewById(R.id.chipLeidas)
        btnFinalizar = findViewById(R.id.btnFinalizar)
        tvCamion.text = "$unidad · $placa"

        adapter = PalomeoAdapter(mutableListOf())
        findViewById<RecyclerView>(R.id.lvProductos).apply {
            layoutManager = LinearLayoutManager(this@PalomeoActivity)
            adapter = this@PalomeoActivity.adapter
        }

        findViewById<ImageButton>(R.id.btnRegresar).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnReiniciar).setOnClickListener { reiniciar() }
        btnFinalizar.setOnClickListener { finalizar() }

        cargarEstado()
    }

    private fun cargarEstado() {
        servidor().obtenerEstadoDispatch(dispatchId) { ok, codigo, cuerpo ->
            runOnUiThread {
                if (!ok || codigo !in 200..299) {
                    Toast.makeText(this, R.string.no_se_pudo_abrir_salida, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                actualizarDesdeRespuesta(JSONObject(cuerpo))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (rfidManager.isConnected) {
            rfidManager.enableTrigger(triggerCallback, false)
        } else {
            Toast.makeText(this, "La lectora se desconectó", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (rfidManager.isConnected) {
            rfidManager.disableTrigger()
        }
        if (leyendo) {
            rfidManager.stopInventory()
            leyendo = false
        }
    }

    private fun iniciarLectura() {
        leyendo = true
        rfidManager.startInventory(inventoryCallback)
    }

    private fun detenerLectura() {
        leyendo = false
        rfidManager.stopInventory()
    }

    private fun parsearProductos(json: String): MutableList<ProductoPalomeo> {
        val arr = org.json.JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                add(
                    ProductoPalomeo(
                        productId = p.getInt("product_id"),
                        nombre = p.optString("name", "?"),
                        esperado = p.getInt("expected"),
                        leido = p.getInt("read"),
                        diferencia = p.getInt("diff"),
                    )
                )
            }
        }.toMutableList()
    }

    private fun actualizarDesdeRespuesta(cuerpo: JSONObject) {
        adapter.actualizar(parsearProductos(cuerpo.optJSONArray("products")?.toString() ?: "[]"))
        chipLeidas.text = "${cuerpo.optInt("total_read")} leídas"

        val problemas = cuerpo.optJSONArray("problem_tags") ?: org.json.JSONArray()
        for (i in 0 until problemas.length()) {
            val t = problemas.getJSONObject(i)
            val epc = t.optString("epc")
            if (problemasAvisados.add(epc)) {
                val resultado = t.optString("result")
                val mensaje = if (resultado == "unknown") getString(R.string.etiqueta_no_registrada_aviso) else getString(R.string.etiqueta_ya_despachada_aviso)
                Toast.makeText(this, "$mensaje: $epc", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enviarLectura(epc: String) {
        val servidor = servidor()
        servidor.enviarLecturaDispatch(dispatchId, epc) { ok, codigo, cuerpo ->
            runOnUiThread {
                if (!ok || codigo !in 200..299) {
                    epcsEnviados.remove(epc) // puede reintentarse si se vuelve a leer
                    return@runOnUiThread
                }
                actualizarDesdeRespuesta(JSONObject(cuerpo))
            }
        }
    }

    private fun reiniciar() {
        val servidor = servidor()
        servidor.reiniciarLecturas(dispatchId) { ok, codigo, cuerpo ->
            runOnUiThread {
                if (!ok || codigo !in 200..299) {
                    Toast.makeText(this, R.string.no_se_pudo_abrir_salida, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                epcsEnviados.clear()
                problemasAvisados.clear()
                actualizarDesdeRespuesta(JSONObject(cuerpo))
            }
        }
    }

    private fun finalizar() {
        btnFinalizar.isEnabled = false
        val servidor = servidor()
        servidor.finalizarLectura(dispatchId) { ok, codigo, cuerpo ->
            runOnUiThread {
                btnFinalizar.isEnabled = true
                if (!ok || codigo !in 200..299) {
                    Toast.makeText(this, R.string.no_se_pudo_finalizar, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val respuesta = JSONObject(cuerpo)
                if (respuesta.optString("result") == "ok") {
                    val yaDespachadas = respuesta.optJSONArray("ya_despachadas") ?: org.json.JSONArray()
                    if (yaDespachadas.length() > 0) {
                        mostrarSalidaCorrectaConObservacion()
                    } else {
                        mostrarSalidaCorrecta()
                    }
                } else {
                    mostrarDialogoDiferencia(respuesta.optJSONArray("alarms") ?: org.json.JSONArray())
                }
            }
        }
    }

    private fun mostrarSalidaCorrecta() {
        AlertDialog.Builder(this)
            .setTitle(R.string.salida_correcta_titulo)
            .setMessage("El camión $unidad ($placa) ya está en ruta.")
            .setPositiveButton(R.string.aceptar) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    /** Cuadra exacto, pero se leyó de mas una etiqueta ya despachada (no
     * bloquea -- decision del usuario, 2026-10-07 -- pero se avisa para que
     * alguien lo revise: podria ser un posible reetiquetado). */
    private fun mostrarSalidaCorrectaConObservacion() {
        AlertDialog.Builder(this)
            .setTitle(R.string.salida_correcta_titulo)
            .setMessage(R.string.salida_correcta_con_etiqueta_despachada)
            .setPositiveButton(R.string.entendido) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun mostrarDialogoDiferencia(alarmas: org.json.JSONArray) {
        var haySobrante = false
        for (i in 0 until alarmas.length()) {
            if (alarmas.getJSONObject(i).optString("type") == "excess") haySobrante = true
        }
        // Con sobrante, o si ya fallo antes solo por faltante, "Aceptar"
        // manda la unidad de regreso a la zona de carga (cancela la salida).
        // La primera vez que falta solo, "Aceptar" nada mas deja seguir leyendo.
        val esDefinitivo = haySobrante || yaFalloSoloFaltante
        if (!esDefinitivo) yaFalloSoloFaltante = true

        AlertDialog.Builder(this)
            .setTitle(R.string.tag_ya_existe_titulo)
            .setMessage(if (esDefinitivo) R.string.aviso_duro_palomeo else R.string.aviso_suave_faltante)
            .setNegativeButton(R.string.aceptar) { _, _ ->
                if (esDefinitivo) cancelarYRegresarAlInicio()
            }
            .setPositiveButton(R.string.autorizar_salida) { _, _ -> abrirAutorizarActivity() }
            .setCancelable(false)
            .show()
    }

    private fun cancelarYRegresarAlInicio() {
        servidor().cancelarSalida(dispatchId) { _, _, _ ->
            runOnUiThread {
                startActivity(Intent(this, SalidaRutaActivity::class.java))
                finish()
            }
        }
    }

    private fun abrirAutorizarActivity() {
        abrirAutorizar.launch(AutorizarActivity.crearIntent(this, dispatchId))
    }

    private fun servidor() = ServerClient("${Preferencias.ip(this)}:${Preferencias.puerto(this)}")

    companion object {
        const val EXTRA_DISPATCH_ID = "dispatch_id"
        const val EXTRA_TRUCK_UNIT = "truck_unit"
        const val EXTRA_TRUCK_PLATE = "truck_plate"

        fun crearIntent(contexto: android.content.Context, dispatchId: Int, unidad: String, placa: String): Intent {
            return Intent(contexto, PalomeoActivity::class.java)
                .putExtra(EXTRA_DISPATCH_ID, dispatchId)
                .putExtra(EXTRA_TRUCK_UNIT, unidad)
                .putExtra(EXTRA_TRUCK_PLATE, placa)
        }
    }
}
