package com.quantumlabs.rfidreader

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Prefijos: lista local a este teléfono (guardada con Preferencias), sin
 * relación con la lista de prefijos del servidor/Windows. Solo la usa
 * Inventario RFID para filtrar lo que se muestra en pantalla. No valida
 * longitud: el usuario decide qué tan largo es cada prefijo.
 */
class PrefijosActivity : AppCompatActivity() {

    private lateinit var etPrefijo: EditText
    private lateinit var adapter: PrefijosAdapter
    private val prefijos = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prefijos)

        etPrefijo = findViewById(R.id.etPrefijo)
        adapter = PrefijosAdapter(prefijos) { prefijo -> eliminar(prefijo) }
        findViewById<RecyclerView>(R.id.lvPrefijos).apply {
            layoutManager = LinearLayoutManager(this@PrefijosActivity)
            adapter = this@PrefijosActivity.adapter
        }

        findViewById<ImageButton>(R.id.btnRegresar).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAgregarPrefijo).setOnClickListener { agregar() }

        cargar()
    }

    private fun cargar() {
        prefijos.clear()
        prefijos.addAll(Preferencias.prefijos(this).sorted())
        adapter.notifyDataSetChanged()
    }

    private fun agregar() {
        val texto = etPrefijo.text.toString()
        if (!Preferencias.agregarPrefijo(this, texto)) {
            Toast.makeText(this, R.string.prefijo_invalido, Toast.LENGTH_SHORT).show()
            return
        }
        etPrefijo.setText("")
        cargar()
    }

    private fun eliminar(prefijo: String) {
        Preferencias.eliminarPrefijo(this, prefijo)
        cargar()
    }
}
