package com.quantumlabs.rfidreader

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.csl.rfidsdk.RfidManager

/**
 * Instancia unica de RfidManager para toda la app (igual que
 * QuickStartApplication en el demo oficial cs710aquickstart): asi la
 * conexion BLE sobrevive aunque cambien de pantalla.
 */
class RfidApplication : Application() {

    lateinit var rfidManager: RfidManager
        private set

    override fun onCreate() {
        super.onCreate()
        // El tema (oscuro/claro) sigue al sistema del telefono.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        // Con logger: los logs internos del SDK (estado del gatillo, inventario,
        // conexion) se ven en Logcat con la etiqueta "RfidSDK" -- necesario para
        // diagnosticar el hardware real.
        rfidManager = RfidManager.builder(this)
            .setLogger { msg -> Log.d("RfidSDK", msg) }
            .build()
    }

    override fun onTerminate() {
        rfidManager.release()
        super.onTerminate()
    }
}
