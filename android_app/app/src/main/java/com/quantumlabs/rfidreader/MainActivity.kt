package com.quantumlabs.rfidreader

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
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
    private lateinit var circuloEstado: FrameLayout
    private lateinit var imgCirculoIcono: ImageView
    private lateinit var tvEstadoTitulo: TextView
    private lateinit var tvEstadoSub: TextView
    private lateinit var chipConteo: TextView
    private lateinit var adapter: LecturasAdapter
    private val lecturas = mutableListOf<Lectura>()
    private val formatoHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var leyendo = false
    private var pulso: ValueAnimator? = null
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
        circuloEstado = findViewById(R.id.circuloEstado)
        imgCirculoIcono = findViewById(R.id.imgCirculoIcono)
        tvEstadoTitulo = findViewById(R.id.tvEstadoTitulo)
        tvEstadoSub = findViewById(R.id.tvEstadoSub)
        chipConteo = findViewById(R.id.chipConteo)

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
    }

    override fun onResume() {
        super.onResume()
        if (rfidManager.isConnected) {
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
                    tvEstadoTitulo.text = "Mantén presionado el gatillo"
                    tvEstadoSub.text = "de la lectora para empezar a leer"
                    epcsVistos.clear()
                    aplicarPotencia()
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
                    tvEstadoTitulo.text = "Lectora desconectada"
                    tvEstadoSub.text = "Toca la tarjeta de arriba para conectar"
                    mostrarCirculoIdle()
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
                if (!epcsVistos.add(tag.epc)) return@runOnUiThread // ya se vio y se mandó
                agregarLectura(tag.epc)
                val servidor = ServerClient("${Preferencias.ip(this@MainActivity)}:${Preferencias.puerto(this@MainActivity)}")
                servidor.enviarTag(tag.epc) { ok, detalle ->
                    runOnUiThread { actualizarEstadoEnvio(tag.epc, ok, detalle) }
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
        mostrarCirculoLeyendo()
        rfidManager.startInventory(inventoryCallback)
    }

    private fun detenerLectura() {
        leyendo = false
        mostrarCirculoIdle()
        rfidManager.stopInventory()
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

    private fun mostrarCirculoIdle() {
        circuloEstado.setBackgroundResource(R.drawable.shape_circle_idle)
        imgCirculoIcono.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_text_muted))
        tvEstadoTitulo.text = "Mantén presionado el gatillo"
        tvEstadoTitulo.setTextColor(ContextCompat.getColor(this, R.color.color_text))
        tvEstadoSub.text = "de la lectora para empezar a leer"
        detenerPulso()
    }

    private fun mostrarCirculoLeyendo() {
        circuloEstado.setBackgroundResource(R.drawable.shape_circle_reading)
        imgCirculoIcono.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_on_accent))
        tvEstadoTitulo.text = "Leyendo…"
        tvEstadoTitulo.setTextColor(ContextCompat.getColor(this, R.color.color_accent))
        tvEstadoSub.text = "gatillo presionado — suéltalo para detener"
        iniciarPulso()
    }

    private fun iniciarPulso() {
        detenerPulso()
        pulso = ObjectAnimator.ofFloat(circuloEstado, "alpha", 1f, 0.65f).apply {
            duration = 650
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun detenerPulso() {
        pulso?.cancel()
        pulso = null
        circuloEstado.alpha = 1f
    }

    private fun agregarLectura(epc: String) {
        lecturas.add(0, Lectura(epc, formatoHora.format(System.currentTimeMillis()), EstadoEnvio.ENVIANDO))
        adapter.notifyItemInserted(0)
        chipConteo.text = lecturas.size.toString()
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
