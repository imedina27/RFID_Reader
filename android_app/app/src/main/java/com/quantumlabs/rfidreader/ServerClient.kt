package com.quantumlabs.rfidreader

import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private val JSON = "application/json".toMediaType()

data class Producto(val id: Int, val nombre: String)

/**
 * Cliente HTTP hacia la laptop: el receptor de pruebas (tools/tag_receiver.py,
 * POST /tag) y la API real de windows_app (docs/api.md, prefijo /api). Ambos
 * corren en el mismo host:puerto configurado en Ajustes.
 */
class ServerClient(baseUrl: String) {
    private val base = "http://${baseUrl.trim().trimEnd('/')}"
    private val client = OkHttpClient()

    /** GET /api/products (docs/api.md) -- solo productos activos. */
    fun obtenerProductos(onResultado: (ok: Boolean, productos: List<Producto>) -> Unit) {
        val request = Request.Builder().url("$base/api/products").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResultado(false, emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onResultado(false, emptyList())
                        return
                    }
                    val arr = JSONArray(it.body?.string() ?: "[]")
                    val productos = buildList {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            if (obj.optBoolean("active", true)) {
                                add(Producto(obj.getInt("id"), obj.getString("name")))
                            }
                        }
                    }
                    onResultado(true, productos)
                }
            }
        })
    }

    /** GET /api/dispatch/lookup/{epc} (docs/api.md): dado el EPC del
     * parabrisas, regresa el camion y, segun el caso, sus boletas activas
     * o una alarma ("no_active_tickets"/"truck_not_available"). 404 si la
     * etiqueta no esta registrada como camion. El llamador interpreta el
     * cuerpo JSON segun el codigo HTTP. */
    fun buscarCamion(epc: String, onResultado: (ok: Boolean, codigo: Int, cuerpo: String) -> Unit) {
        val request = Request.Builder().url("$base/api/dispatch/lookup/$epc").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResultado(false, -1, e.message ?: "sin conexión")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResultado(true, it.code, it.body?.string() ?: "{}")
                }
            }
        })
    }

    /** POST /api/dispatches (docs/api.md): abre la salida a ruta con las
     * boletas elegidas. Regresa dispatch_id y lo esperado por producto. */
    fun abrirSalida(epc: String, ticketIds: List<Int>, onResultado: (ok: Boolean, codigo: Int, cuerpo: String) -> Unit) {
        val cuerpo = JSONObject()
            .put("truck_epc", epc)
            .put("ticket_ids", JSONArray(ticketIds))
            .toString().toRequestBody(JSON)
        val request = Request.Builder().url("$base/api/dispatches").post(cuerpo).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResultado(false, -1, e.message ?: "sin conexión")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResultado(true, it.code, it.body?.string() ?: "{}")
                }
            }
        })
    }

    /** GET /api/dispatches/{id}/status (docs/api.md): palomeo actual, ya con
     * el nombre de cada producto. */
    fun obtenerEstadoDispatch(dispatchId: Int, onResultado: (ok: Boolean, codigo: Int, cuerpo: String) -> Unit) {
        val request = Request.Builder().url("$base/api/dispatches/$dispatchId/status").get().build()
        ejecutar(request, onResultado)
    }

    /** POST /api/dispatches/{id}/reads (docs/api.md) con un solo EPC --
     * el palomeo se actualiza casi en tiempo real, una lectura a la vez. */
    fun enviarLecturaDispatch(dispatchId: Int, epc: String, onResultado: (ok: Boolean, codigo: Int, cuerpo: String) -> Unit) {
        val cuerpo = JSONObject().put("epcs", JSONArray().put(epc)).toString().toRequestBody(JSON)
        val request = Request.Builder().url("$base/api/dispatches/$dispatchId/reads").post(cuerpo).build()
        ejecutar(request, onResultado)
    }

    /** POST /api/dispatches/{id}/reset (docs/api.md): borra las lecturas de la salida. */
    fun reiniciarLecturas(dispatchId: Int, onResultado: (ok: Boolean, codigo: Int, cuerpo: String) -> Unit) {
        val request = Request.Builder().url("$base/api/dispatches/$dispatchId/reset")
            .post("".toRequestBody(null)).build()
        ejecutar(request, onResultado)
    }

    /** POST /api/dispatches/{id}/finish (docs/api.md): compara lo leido contra
     * lo esperado y cierra la salida si cuadra. */
    fun finalizarLectura(dispatchId: Int, onResultado: (ok: Boolean, codigo: Int, cuerpo: String) -> Unit) {
        val request = Request.Builder().url("$base/api/dispatches/$dispatchId/finish")
            .post("".toRequestBody(null)).build()
        ejecutar(request, onResultado)
    }

    /** POST /api/dispatches/{id}/cancel (docs/api.md): la unidad regresa a la
     * zona de carga -- se cancela la salida sin tocar boletas ni camion, que
     * quedan libres para un intento nuevo (decision del usuario 2026-10-06). */
    fun cancelarSalida(dispatchId: Int, onResultado: (ok: Boolean, codigo: Int, cuerpo: String) -> Unit) {
        val request = Request.Builder().url("$base/api/dispatches/$dispatchId/cancel")
            .post("".toRequestBody(null)).build()
        ejecutar(request, onResultado)
    }

    /** POST /api/dispatches/{id}/authorize (docs/api.md): cierra la salida
     * con diferencia, dejando registrado el motivo y quien autoriza. */
    fun autorizarDiferencia(dispatchId: Int, autorizadoPor: String, motivo: String, onResultado: (ok: Boolean, codigo: Int, cuerpo: String) -> Unit) {
        val cuerpo = JSONObject()
            .put("authorized_by", autorizadoPor)
            .put("reason", motivo)
            .toString().toRequestBody(JSON)
        val request = Request.Builder().url("$base/api/dispatches/$dispatchId/authorize").post(cuerpo).build()
        ejecutar(request, onResultado)
    }

    private fun ejecutar(request: Request, onResultado: (ok: Boolean, codigo: Int, cuerpo: String) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResultado(false, -1, e.message ?: "sin conexión")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResultado(true, it.code, it.body?.string() ?: "{}")
                }
            }
        })
    }

    /** POST /api/tags/batch (docs/api.md) con un solo EPC -- captura de un
     * pallet a la vez desde la pantalla de Captura de Tags. */
    fun capturarTagPallet(productId: Int, epc: String, onResultado: (ok: Boolean, resultado: String, detalle: String) -> Unit) {
        val cuerpo = JSONObject()
            .put("product_id", productId)
            .put("epcs", JSONArray().put(epc))
            .toString().toRequestBody(JSON)
        val request = Request.Builder().url("$base/api/tags/batch").post(cuerpo).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResultado(false, "error", e.message ?: "sin conexión")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onResultado(false, "error", "HTTP ${it.code}")
                        return
                    }
                    val body = JSONObject(it.body?.string() ?: "{}")
                    val primero = body.optJSONArray("results")?.optJSONObject(0)
                    onResultado(true, primero?.optString("result") ?: "error", "")
                }
            }
        })
    }

    fun enviarTag(epc: String, onResultado: (ok: Boolean, detalle: String) -> Unit) {
        val cuerpo = JSONObject().put("epc", epc).toString().toRequestBody(JSON)
        val request = Request.Builder().url("$base/tag").post(cuerpo).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResultado(false, e.message ?: "sin conexión")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { onResultado(it.isSuccessful, "HTTP ${it.code}") }
            }
        })
    }

    fun probarConexion(onResultado: (ok: Boolean, detalle: String) -> Unit) {
        val request = Request.Builder().url("$base/health").get().build()
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
