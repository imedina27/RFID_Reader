package com.quantumlabs.rfidreader

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Salida a Ruta, paso 2 "Boletas activas" (docs/funcional.md, Procedimiento
 * A, paso 4): el camion ya se identifico en el paso 1; aqui el operador
 * marca cuales de sus boletas activas salen en este viaje y confirma, lo
 * que abre la salida (POST /api/dispatches) y calcula lo esperado por
 * producto.
 */
class BoletasActivity : AppCompatActivity() {

    private lateinit var tvCamion: TextView
    private lateinit var tvContadorSeleccion: TextView
    private lateinit var btnConfirmar: Button
    private lateinit var boletas: List<BoletaUi>
    private lateinit var epc: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_boletas)

        epc = intent.getStringExtra(EXTRA_EPC) ?: ""
        val unidad = intent.getStringExtra(EXTRA_TRUCK_UNIT) ?: "?"
        val placa = intent.getStringExtra(EXTRA_TRUCK_PLATE) ?: "?"
        boletas = parsearBoletas(intent.getStringExtra(EXTRA_TICKETS_JSON) ?: "[]")

        tvCamion = findViewById(R.id.tvCamion)
        tvContadorSeleccion = findViewById(R.id.tvContadorSeleccion)
        btnConfirmar = findViewById(R.id.btnConfirmar)
        tvCamion.text = "$unidad · $placa"

        val adapter = BoletasAdapter(boletas) { actualizarContador() }
        findViewById<RecyclerView>(R.id.lvBoletas).apply {
            layoutManager = LinearLayoutManager(this@BoletasActivity)
            this.adapter = adapter
        }
        actualizarContador()

        findViewById<ImageButton>(R.id.btnRegresar).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCancelar).setOnClickListener { finish() }
        btnConfirmar.setOnClickListener { confirmar() }
    }

    private fun parsearBoletas(json: String): List<BoletaUi> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                val lineasJson = t.optJSONArray("lines") ?: JSONArray()
                val lineas = buildList {
                    for (j in 0 until lineasJson.length()) {
                        val l = lineasJson.getJSONObject(j)
                        add(LineaBoleta(l.getString("product_name"), l.getInt("pallets")))
                    }
                }
                add(BoletaUi(t.getInt("id"), t.getString("folio"), t.getString("customer"), lineas))
            }
        }
    }

    private fun actualizarContador() {
        val seleccionadas = boletas.count { it.seleccionada }
        tvContadorSeleccion.text = when (seleccionadas) {
            0 -> ""
            1 -> "1 boleta seleccionada"
            else -> "$seleccionadas boletas seleccionadas"
        }
        btnConfirmar.isEnabled = seleccionadas > 0
    }

    private fun confirmar() {
        val idsSeleccionados = boletas.filter { it.seleccionada }.map { it.id }
        if (idsSeleccionados.isEmpty()) {
            Toast.makeText(this, R.string.elige_al_menos_una_boleta, Toast.LENGTH_SHORT).show()
            return
        }
        btnConfirmar.isEnabled = false
        val servidor = ServerClient("${Preferencias.ip(this)}:${Preferencias.puerto(this)}")
        servidor.abrirSalida(epc, idsSeleccionados) { ok, codigo, cuerpo ->
            runOnUiThread {
                if (!ok || codigo !in 200..299) {
                    btnConfirmar.isEnabled = true
                    val mensaje = runCatching { JSONObject(cuerpo).optString("message") }.getOrNull()
                    Toast.makeText(this, mensaje?.ifBlank { null } ?: getString(R.string.no_se_pudo_abrir_salida), Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val respuesta = JSONObject(cuerpo)
                startActivity(
                    PalomeoActivity.crearIntent(
                        this,
                        dispatchId = respuesta.optInt("dispatch_id"),
                        unidad = intent.getStringExtra(EXTRA_TRUCK_UNIT) ?: "?",
                        placa = intent.getStringExtra(EXTRA_TRUCK_PLATE) ?: "?",
                    )
                )
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_EPC = "epc"
        const val EXTRA_TRUCK_UNIT = "truck_unit"
        const val EXTRA_TRUCK_PLATE = "truck_plate"
        const val EXTRA_TICKETS_JSON = "tickets_json"

        fun crearIntent(contexto: android.content.Context, epc: String, unidad: String, placa: String, ticketsJson: String): Intent {
            return Intent(contexto, BoletasActivity::class.java)
                .putExtra(EXTRA_EPC, epc)
                .putExtra(EXTRA_TRUCK_UNIT, unidad)
                .putExtra(EXTRA_TRUCK_PLATE, placa)
                .putExtra(EXTRA_TICKETS_JSON, ticketsJson)
        }
    }
}
