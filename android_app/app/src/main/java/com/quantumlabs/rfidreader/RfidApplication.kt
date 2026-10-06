package com.quantumlabs.rfidreader

import android.app.Application
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
        rfidManager = RfidManager.create(this)
    }

    override fun onTerminate() {
        rfidManager.release()
        super.onTerminate()
    }
}
