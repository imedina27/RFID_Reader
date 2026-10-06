package com.quantumlabs.rfidreader

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/** Pantalla de Ajustes: IP/puerto del receptor, potencia de la antena y
 * modo simulado (docs/apk.md, sección "Ajustes"). */
class SettingsActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var etPuerto: EditText
    private lateinit var seekPotencia: SeekBar
    private lateinit var tvPotenciaValor: TextView
    private lateinit var switchSimulado: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etIp = findViewById(R.id.etIp)
        etPuerto = findViewById(R.id.etPuerto)
        seekPotencia = findViewById(R.id.seekPotencia)
        tvPotenciaValor = findViewById(R.id.tvPotenciaValor)
        switchSimulado = findViewById(R.id.switchSimulado)

        etIp.setText(Preferencias.ip(this))
        etPuerto.setText(Preferencias.puerto(this))
        seekPotencia.progress = Preferencias.potenciaDbm(this)
        tvPotenciaValor.text = "${seekPotencia.progress} dBm"
        switchSimulado.isChecked = Preferencias.modoSimulado(this)

        seekPotencia.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvPotenciaValor.text = "$progress dBm"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        findViewById<android.widget.ImageButton>(R.id.btnRegresar).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnProbar).setOnClickListener { probarConexion() }
        findViewById<Button>(R.id.btnGuardar).setOnClickListener { guardarYRegresar() }
    }

    private fun probarConexion() {
        val servidor = ServerClient("${etIp.text}:${etPuerto.text}")
        Toast.makeText(this, "Probando…", Toast.LENGTH_SHORT).show()
        servidor.probarConexion { ok, detalle ->
            runOnUiThread {
                val mensaje = if (ok) "Conexión correcta" else "No se pudo conectar: $detalle"
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun guardarYRegresar() {
        Preferencias.guardar(
            this,
            ip = etIp.text.toString().trim(),
            puerto = etPuerto.text.toString().trim(),
            potenciaDbm = seekPotencia.progress,
            modoSimulado = switchSimulado.isChecked,
        )
        Toast.makeText(this, "Ajustes guardados", Toast.LENGTH_SHORT).show()
        finish()
    }
}
