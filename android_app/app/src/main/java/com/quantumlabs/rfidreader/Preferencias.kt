package com.quantumlabs.rfidreader

import android.content.Context

/** Ajustes persistidos (pantalla de Ajustes): IP/puerto del receptor,
 * potencia de la antena y modo simulado. También guarda la lista de
 * prefijos de Inventario RFID (pantalla Prefijos) -- es local al teléfono,
 * sin relación con la lista de prefijos del servidor/Windows. */
object Preferencias {
    private const val ARCHIVO = "ajustes"
    const val IP_DEFECTO = "192.168.137.1"
    const val PUERTO_DEFECTO = "5000"
    const val POTENCIA_DEFECTO = 26
    private val PREFIJOS_DEFECTO = setOf("E28011")

    private fun prefs(context: Context) = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun ip(context: Context): String = prefs(context).getString("ip", IP_DEFECTO) ?: IP_DEFECTO
    fun puerto(context: Context): String = prefs(context).getString("puerto", PUERTO_DEFECTO) ?: PUERTO_DEFECTO
    fun potenciaDbm(context: Context): Int = prefs(context).getInt("potencia", POTENCIA_DEFECTO)
    fun modoSimulado(context: Context): Boolean = prefs(context).getBoolean("modo_simulado", false)

    fun guardar(
        context: Context,
        ip: String,
        puerto: String,
        potenciaDbm: Int,
        modoSimulado: Boolean,
    ) {
        prefs(context).edit()
            .putString("ip", ip)
            .putString("puerto", puerto)
            .putInt("potencia", potenciaDbm)
            .putBoolean("modo_simulado", modoSimulado)
            .apply()
    }

    fun prefijos(context: Context): Set<String> =
        prefs(context).getStringSet("prefijos", PREFIJOS_DEFECTO) ?: PREFIJOS_DEFECTO

    /** Agrega un prefijo (normalizado a mayúsculas); devuelve false si está
     * vacío o ya existía, para que la pantalla avise al usuario. */
    fun agregarPrefijo(context: Context, prefijo: String): Boolean {
        val limpio = prefijo.trim().uppercase()
        if (limpio.isEmpty()) return false
        val actuales = prefijos(context).toMutableSet()
        if (!actuales.add(limpio)) return false
        prefs(context).edit().putStringSet("prefijos", actuales).apply()
        return true
    }

    fun eliminarPrefijo(context: Context, prefijo: String) {
        val actuales = prefijos(context).toMutableSet()
        actuales.remove(prefijo)
        prefs(context).edit().putStringSet("prefijos", actuales).apply()
    }
}
