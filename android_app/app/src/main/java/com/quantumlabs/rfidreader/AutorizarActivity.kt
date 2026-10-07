package com.quantumlabs.rfidreader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * Salida a Ruta, paso 4b "Autorizar con motivo" (docs/funcional.md,
 * Procedimiento A, resultado con diferencia): cierra la salida como
 * "completada con diferencia", dejando registrado el motivo y quien
 * autoriza (POST /api/dispatches/{id}/authorize).
 */
class AutorizarActivity : AppCompatActivity() {

    private lateinit var etMotivo: EditText
    private lateinit var etAutorizadoPor: EditText
    private lateinit var btnAutorizar: Button
    private var dispatchId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autorizar)

        dispatchId = intent.getIntExtra(EXTRA_DISPATCH_ID, -1)
        etMotivo = findViewById(R.id.etMotivo)
        etAutorizadoPor = findViewById(R.id.etAutorizadoPor)
        btnAutorizar = findViewById(R.id.btnAutorizar)
        findViewById<TextView>(R.id.tvResumenDiferencia).text = getString(R.string.lectura_no_cuadra)

        findViewById<ImageButton>(R.id.btnRegresar).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCancelar).setOnClickListener { finish() }
        btnAutorizar.setOnClickListener { autorizar() }
    }

    private fun autorizar() {
        val motivo = etMotivo.text.toString().trim()
        val autorizadoPor = etAutorizadoPor.text.toString().trim()
        if (motivo.isEmpty()) {
            Toast.makeText(this, R.string.falta_motivo, Toast.LENGTH_SHORT).show()
            return
        }
        if (autorizadoPor.isEmpty()) {
            Toast.makeText(this, R.string.falta_autorizado_por, Toast.LENGTH_SHORT).show()
            return
        }
        btnAutorizar.isEnabled = false
        val servidor = ServerClient("${Preferencias.ip(this)}:${Preferencias.puerto(this)}")
        servidor.autorizarDiferencia(dispatchId, autorizadoPor, motivo) { ok, codigo, cuerpo ->
            runOnUiThread {
                btnAutorizar.isEnabled = true
                if (!ok || codigo !in 200..299) {
                    val mensaje = runCatching { JSONObject(cuerpo).optString("message") }.getOrNull()
                    Toast.makeText(this, mensaje?.ifBlank { null } ?: getString(R.string.no_se_pudo_autorizar), Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_DISPATCH_ID = "dispatch_id"

        fun crearIntent(contexto: android.content.Context, dispatchId: Int): Intent {
            return Intent(contexto, AutorizarActivity::class.java).putExtra(EXTRA_DISPATCH_ID, dispatchId)
        }
    }
}
