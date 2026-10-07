package com.quantumlabs.rfidreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.csl.rfidsdk.RfidManager
import com.csl.rfidsdk.callbacks.RfidConfigurationCallback
import com.csl.rfidsdk.callbacks.RfidConnectionCallback
import com.csl.rfidsdk.callbacks.RfidInventoryCallback
import com.csl.rfidsdk.callbacks.RfidScanCallback
import com.csl.rfidsdk.callbacks.TriggerCallback
import com.csl.rfidsdk.config.RfidStopReason
import com.csl.rfidsdk.models.BatteryInfo
import com.csl.rfidsdk.models.RfidError
import com.csl.rfidsdk.models.RfidInventoryStats
import com.csl.rfidsdk.models.RfidReader
import com.csl.rfidsdk.models.RfidTag
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Salida a ruta / captura de tags (ROADMAP.md, Fase 6): conecta con la
 * CS108-2, lee tags SOLO mientras el gatillo fisico esta presionado (el
 * patron enableTrigger(callback, false) + performClick manual, verificado
 * en el demo oficial cs710aquickstart/InventoryActivity.java) y manda cada
 * EPC al receptor configurado en Ajustes.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var rfidManager: RfidManager
    private lateinit var tarjetaConexion: android.view.View
    private lateinit var tvNombreLectora: TextView
    private lateinit var chipConexion: TextView
    private lateinit var chipConteo: TextView
    private lateinit var btnCapturaTags: Button
    private lateinit var btnSalidaRuta: Button
    private lateinit var adapter: LecturasAdapter
    private val lecturas = mutableListOf<Lectura>()
    private val formatoHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var leyendo = false
    private val manejador = Handler(Looper.getMainLooper())

    /** EPC unicos vistos en esta conexion (docs/funcional.md: "acumula
     * etiquetas unicas" -- la lectora reporta el mismo tag decenas de veces
     * por segundo mientras el gatillo esta presionado; sin esto la lista y
     * los envios al servidor se saturan con el mismo EPC repetido). */
    private val epcsVistos = mutableSetOf<String>()

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

    private val triggerCallback = object : TriggerCallback {
        override fun onTriggerStateChanged(pressed: Boolean) {
            runOnUiThread {
                if (pressed && !leyendo) {
                    iniciarLectura()
                } else if (!pressed && leyendo) {
                    detenerLectura()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rfidManager = (application as RfidApplication).rfidManager
        tarjetaConexion = findViewById(R.id.tarjetaConexion)
        tvNombreLectora = findViewById(R.id.tvNombreLectora)
        chipConexion = findViewById(R.id.chipConexion)
        chipConteo = findViewById(R.id.chipConteo)
        btnCapturaTags = findViewById(R.id.btnCapturaTags)
        btnSalidaRuta = findViewById(R.id.btnSalidaRuta)

        adapter = LecturasAdapter(lecturas)
        findViewById<RecyclerView>(R.id.lvLecturas).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        actualizarChipConexion(conectado = false)
        tarjetaConexion.setOnClickListener { onTarjetaConexionTocada() }
        findViewById<ImageButton>(R.id.btnAjustes).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnCapturaTags.setOnClickListener {
            startActivity(Intent(this, CapturaTagsActivity::class.java))
        }
        btnSalidaRuta.setOnClickListener {
            startActivity(Intent(this, SalidaRutaActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (rfidManager.isConnected) {
            // Si se cambio la potencia en Ajustes mientras ya estaba
            // conectado, antes se quedaba guardada pero sin aplicarse de
            // verdad hasta la siguiente reconexion. Reaplicarla aqui cubre
            // el caso mas comun: Ajustes -> Guardar -> regresar.
            aplicarPotencia()
            rfidManager.enableTrigger(triggerCallback, false)
        }
    }

    override fun onPause() {
        super.onPause()
        if (rfidManager.isConnected) {
            rfidManager.disableTrigger()
        }
    }

    private fun onTarjetaConexionTocada() {
        if (rfidManager.isConnected) return
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
        actualizarChipConexion(conectado = false, texto = "Buscando…")
        rfidManager.startScan(object : RfidScanCallback {
            override fun onReaderDiscovered(reader: RfidReader) {
                rfidManager.stopScan()
                runOnUiThread {
                    tvNombreLectora.text = reader.name
                    actualizarChipConexion(conectado = false, texto = "Conectando…")
                }
                conectar(reader)
            }

            override fun onReaderUpdated(reader: RfidReader) = Unit

            override fun onScanError(error: RfidError) {
                runOnUiThread {
                    actualizarChipConexion(conectado = false)
                    Toast.makeText(this@MainActivity, "Error al buscar: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun conectar(reader: RfidReader) {
        rfidManager.connect(reader, object : RfidConnectionCallback {
            override fun onConnecting() {
                runOnUiThread { actualizarChipConexion(conectado = false, texto = "Conectando…") }
            }

            override fun onConnected(reader: RfidReader) {
                runOnUiThread { actualizarChipConexion(conectado = false, texto = "Inicializando…") }
            }

            override fun onReaderReady(reader: RfidReader) {
                runOnUiThread {
                    actualizarChipConexion(conectado = true)
                    btnCapturaTags.isEnabled = true
                    btnSalidaRuta.isEnabled = true
                    epcsVistos.clear()
                    ultimaActualizacionVisto.clear()
                    aplicarPotencia()
                    val bateria = rfidManager.getBatteryInfo()
                    Log.d("RfidSDK", "Bateria de la lectora: ${rfidManager.getBatteryPercentage()}% (${bateria?.formattedVoltage ?: "?"})")
                    // Justo al conectar, el estado del gatillo que reporta la
                    // lectora puede llegar erroneo por un instante (carrera en
                    // el handshake BLE) y disparar una lectura fantasma. Se
                    // espera a que el canal se estabilice antes de confiar en
                    // el gatillo (visto en pruebas reales 2026-10-06).
                    manejador.postDelayed({ prepararLecturaPorGatillo() }, 800)
                }
            }

            override fun onConnectionFailed(error: RfidError) {
                runOnUiThread {
                    actualizarChipConexion(conectado = false)
                    Toast.makeText(this@MainActivity, "Error de conexión: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onDisconnected(reader: RfidReader, error: RfidError?) {
                manejador.removeCallbacksAndMessages(null)
                leyendo = false
                runOnUiThread {
                    actualizarChipConexion(conectado = false)
                    btnCapturaTags.isEnabled = false
                    btnSalidaRuta.isEnabled = false
                }
            }
        })
    }

    /** Activa el gatillo físico (modo manual, igual que el demo oficial
     * cs710aquickstart/InventoryActivity): la lectora avisa cuando se
     * presiona o suelta, y onTriggerStateChanged hace start/stopInventory. */
    private fun prepararLecturaPorGatillo() {
        rfidManager.enableTrigger(triggerCallback, false)
    }

    private fun aplicarPotencia() {
        val dbm = Preferencias.potenciaDbm(this)
        rfidManager.configure().powerLevel(dbm * 10).apply(object : RfidConfigurationCallback {
            override fun onConfigured() = Unit
            override fun onConfigurationFailed(error: RfidError) = Unit
        })
    }

    private val inventoryCallback = object : RfidInventoryCallback {
        override fun onTagRead(tag: RfidTag) {
            runOnUiThread {
                if (epcsVistos.add(tag.epc)) {
                    agregarLectura(tag.epc)
                    val servidor = ServerClient("${Preferencias.ip(this@MainActivity)}:${Preferencias.puerto(this@MainActivity)}")
                    servidor.enviarTag(tag.epc) { ok, detalle ->
                        runOnUiThread { actualizarEstadoEnvio(tag.epc, ok, detalle) }
                    }
                } else {
                    // Ya se mandó antes: no se repite el envío, pero se refresca
                    // la hora para que se note que la lectora lo sigue viendo
                    // (si no, parece que dejó de leer).
                    marcarVistoDeNuevo(tag.epc)
                }
            }
        }

        override fun onInventoryRound(stats: RfidInventoryStats) = Unit

        override fun onInventoryStopped(reason: RfidStopReason) = Unit

        override fun onInventoryError(error: RfidError) {
            runOnUiThread { Toast.makeText(this@MainActivity, "Error de lectura: ${error.message}", Toast.LENGTH_LONG).show() }
        }

        override fun onBatteryUpdate(batteryInfo: BatteryInfo) = Unit
    }

    private fun iniciarLectura() {
        leyendo = true
        rfidManager.startInventory(inventoryCallback)
    }

    private fun detenerLectura() {
        leyendo = false
        rfidManager.stopInventory()
        adapter.notifyDataSetChanged() // refresca hora/contador finales (van con tope de 400ms mientras se lee)
    }

    // ---------------------------------------------------------- estado UI --

    private fun actualizarChipConexion(conectado: Boolean, texto: String? = null) {
        val color = ContextCompat.getColor(this, if (conectado) R.color.color_ok else R.color.color_text_muted)
        chipConexion.text = texto ?: if (conectado) "Conectado" else "Desconectado"
        chipConexion.setTextColor(color)
        chipConexion.backgroundTintList = ColorStateList.valueOf(withAlpha(color, if (conectado) 40 else 30))
        if (!conectado && texto == null) tvNombreLectora.text = getString(R.string.sin_conectar)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private fun agregarLectura(epc: String) {
        lecturas.add(0, Lectura(epc, formatoHora.format(System.currentTimeMillis()), EstadoEnvio.ENVIANDO))
        adapter.notifyItemInserted(0)
        chipConteo.text = lecturas.size.toString()
    }

    private val ultimaActualizacionVisto = mutableMapOf<String, Long>()

    /** Cuenta cada vez que una etiqueta ya enviada se vuelve a ver, y refresca
     * su hora -- da evidencia de que la lectora la sigue viendo. El contador
     * sube en cada lectura real; el refresco visual tiene tope de 400ms por
     * EPC (la lectora reporta el mismo tag decenas de veces por segundo). */
    private fun marcarVistoDeNuevo(epc: String) {
        val index = lecturas.indexOfFirst { it.epc == epc }
        if (index < 0) return
        lecturas[index].conteo++

        val ahora = System.currentTimeMillis()
        val ultima = ultimaActualizacionVisto[epc] ?: 0L
        if (ahora - ultima < 400) return
        ultimaActualizacionVisto[epc] = ahora
        lecturas[index].hora = formatoHora.format(ahora)
        adapter.notifyItemChanged(index)
    }

    private fun actualizarEstadoEnvio(epc: String, ok: Boolean, detalle: String) {
        val index = lecturas.indexOfFirst { it.epc == epc && it.estado == EstadoEnvio.ENVIANDO }
        if (index >= 0) {
            lecturas[index].estado = if (ok) EstadoEnvio.ENVIADO else EstadoEnvio.ERROR
            lecturas[index].detalleError = detalle
            adapter.notifyItemChanged(index)
        }
    }

    override fun onDestroy() {
        manejador.removeCallbacksAndMessages(null)
        if (rfidManager.isConnected) {
            rfidManager.disableTrigger()
            rfidManager.stopInventory()
            rfidManager.disconnect()
        }
        super.onDestroy()
    }
}
