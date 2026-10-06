package com.quantumlabs.rfidreader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.csl.rfidsdk.RfidManager
import com.csl.rfidsdk.callbacks.RfidConnectionCallback
import com.csl.rfidsdk.callbacks.RfidInventoryCallback
import com.csl.rfidsdk.callbacks.RfidScanCallback
import com.csl.rfidsdk.config.RfidStopReason
import com.csl.rfidsdk.models.BatteryInfo
import com.csl.rfidsdk.models.RfidError
import com.csl.rfidsdk.models.RfidInventoryStats
import com.csl.rfidsdk.models.RfidReader
import com.csl.rfidsdk.models.RfidTag
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * APK minima de prueba (ROADMAP.md, Fase 6): conectarse a la CS108-2,
 * leer tags por inventario continuo y mandar cada EPC al receptor de
 * pruebas en Windows (tools/tag_receiver.py). Sin pantallas de "Salida a
 * Ruta" ni "Captura de Tags" todavia -- eso es la Fase 7, sobre esta
 * misma base.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var rfidManager: RfidManager
    private lateinit var tvEstado: TextView
    private lateinit var etServidor: EditText
    private lateinit var btnConectar: Button
    private lateinit var adapter: ArrayAdapter<String>
    private val lecturas = mutableListOf<String>()
    private val formatoHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val permisosNecesarios: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
            )
        }

    private val pedirPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        if (resultados.values.all { it }) {
            buscarYConectar()
        } else {
            Toast.makeText(this, "Se necesitan permisos de Bluetooth y ubicación", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rfidManager = (application as RfidApplication).rfidManager
        tvEstado = findViewById(R.id.tvEstado)
        etServidor = findViewById(R.id.etServidor)
        btnConectar = findViewById(R.id.btnConectar)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, lecturas)
        findViewById<ListView>(R.id.lvLecturas).adapter = adapter

        btnConectar.setOnClickListener { onBotonConectar() }
    }

    private fun onBotonConectar() {
        if (rfidManager.isConnected) {
            tvEstado.text = "Ya conectado"
            return
        }
        val faltantes = permisosNecesarios.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltantes.isEmpty()) {
            buscarYConectar()
        } else {
            pedirPermisos.launch(faltantes.toTypedArray())
        }
    }

    private fun buscarYConectar() {
        tvEstado.text = "Buscando lectora..."
        rfidManager.startScan(object : RfidScanCallback {
            override fun onReaderDiscovered(reader: RfidReader) {
                rfidManager.stopScan()
                runOnUiThread { tvEstado.text = "Conectando a ${reader.name}..." }
                conectar(reader)
            }

            override fun onReaderUpdated(reader: RfidReader) = Unit

            override fun onScanError(error: RfidError) {
                runOnUiThread { tvEstado.text = "Error al buscar: ${error.message}" }
            }
        })
    }

    private fun conectar(reader: RfidReader) {
        rfidManager.connect(reader, object : RfidConnectionCallback {
            override fun onConnecting() {
                runOnUiThread { tvEstado.text = "Conectando..." }
            }

            override fun onConnected(reader: RfidReader) {
                runOnUiThread { tvEstado.text = "Conectado, inicializando..." }
            }

            override fun onReaderReady(reader: RfidReader) {
                runOnUiThread {
                    tvEstado.text = "Listo: ${reader.name} — leyendo tags"
                    iniciarLectura()
                }
            }

            override fun onConnectionFailed(error: RfidError) {
                runOnUiThread { tvEstado.text = "Error de conexión: ${error.message}" }
            }

            override fun onDisconnected(reader: RfidReader, error: RfidError?) {
                runOnUiThread { tvEstado.text = "Desconectado" }
            }
        })
    }

    private fun iniciarLectura() {
        val servidor = ServerClient(etServidor.text.toString())
        rfidManager.startInventory(object : RfidInventoryCallback {
            override fun onTagRead(tag: RfidTag) {
                runOnUiThread { agregarLectura(tag.epc) }
                servidor.enviarTag(tag.epc) { ok, detalle ->
                    runOnUiThread { actualizarEstadoEnvio(tag.epc, ok, detalle) }
                }
            }

            override fun onInventoryRound(stats: RfidInventoryStats) = Unit

            override fun onInventoryStopped(reason: RfidStopReason) {
                runOnUiThread { tvEstado.text = "Lectura detenida" }
            }

            override fun onInventoryError(error: RfidError) {
                runOnUiThread { tvEstado.text = "Error de lectura: ${error.message}" }
            }

            override fun onBatteryUpdate(batteryInfo: BatteryInfo) = Unit
        })
    }

    private fun agregarLectura(epc: String) {
        lecturas.add(0, "${formatoHora.format(System.currentTimeMillis())}  $epc  [enviando...]")
        adapter.notifyDataSetChanged()
    }

    private fun actualizarEstadoEnvio(epc: String, ok: Boolean, detalle: String) {
        val index = lecturas.indexOfFirst { it.contains(epc) }
        if (index >= 0) {
            val estado = if (ok) "enviado" else "ERROR: $detalle"
            lecturas[index] = lecturas[index].substringBeforeLast("[") + "[$estado]"
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        if (rfidManager.isConnected) {
            rfidManager.stopInventory()
            rfidManager.disconnect()
        }
        super.onDestroy()
    }
}
