package com.quantumlabs.rfidreader

import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

private val JSON = "application/json".toMediaType()

/**
 * Cliente HTTP minimo hacia el receptor de pruebas en Windows
 * (tools/tag_receiver.py). Un solo endpoint: POST /tag { "epc": "..." }.
 */
class ServerClient(baseUrl: String) {
    private val url = "http://${baseUrl.trim().trimEnd('/')}/tag"
    private val client = OkHttpClient()

    fun enviarTag(epc: String, onResultado: (ok: Boolean, detalle: String) -> Unit) {
        val cuerpo = JSONObject().put("epc", epc).toString().toRequestBody(JSON)
        val request = Request.Builder().url(url).post(cuerpo).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResultado(false, e.message ?: "sin conexión")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { onResultado(it.isSuccessful, "HTTP ${it.code}") }
            }
        })
    }
}
