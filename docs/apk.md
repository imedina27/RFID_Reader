# Documentación — APK Android para el CS108-2

> Este documento recoge lo que el fabricante publica sobre su SDK de Android. Los puntos marcados con ⚠️ **deben verificarse en el código del demo** durante la Fase 1, porque la documentación pública consultada no detalla nombres de clases ni permisos.
> **2026-10-06:** se clonó `cslrfid/cs710s-android` (tag `v1.1.0`) y se leyó el código real del wrapper SDK y del demo `cs710aquickstart`. Las secciones marcadas ✅ ya están verificadas contra ese código, no son suposición.

## 1. Opciones de SDK oficiales (todas con licencia MIT)

| Opción | Repositorio | Observaciones |
|---|---|---|
| **Wrapper SDK `csl-rfid-android-sdk` (recomendada)** | https://github.com/cslrfid/cs710s-android | Basada en *callbacks*. Soporta **CS710S y CS108**. Se instala por JitPack. `minSdk 26`, `compileSdk/targetSdk 36`, Java 17. |
| SDK clásico `cslibrary4a` + app demo | https://github.com/cslrfid/CS108-Android-Java-App | App demo y SDK Java para CS108. Más antigua (requiere SDK platform 25, Android Studio 3.1.3+, JDK 1.8). Incluye módulos `app`, `cslibrary4a`, `epctagcoder` y carpeta `doc`. |
| Protocolo propio (byte stream) | PDF "Bluetooth and USB Byte Stream API" (ver `lectora.md`) | Solo si se quisiera implementar BLE a mano. **No recomendado para la demo.** |

**Recomendación:** usar el **wrapper SDK** (`csl-rfid-android-sdk`) y apoyarse en el código del demo `cs710aquickstart` del mismo repositorio como referencia de uso.

### Clases reales del wrapper SDK ✅ (`com.csl.rfidsdk`, verificado en `v1.1.0`)

- `RfidManager` — punto de entrada único. `RfidManager.create(context)` (instancia compartida a nivel `Application`, ver `QuickStartApplication`/`RfidApplication`).
  - `startScan(RfidScanCallback)` / `stopScan()` → descubre lectoras (`RfidReader`: `getName()`, `getAddress()`, `getRssi()`).
  - `connect(RfidReader, RfidConnectionCallback)` / `disconnect()` / `isConnected()`.
  - `startInventory(RfidInventoryCallback)` / `stopInventory()` → `onTagRead(RfidTag)` por cada lectura; `RfidTag.getEpc()` trae el EPC (el que nos importa), también `getRssi()`, `getTid()`.
  - `configure().powerLevel(n)...apply(RfidConfigurationCallback)` — ajuste de potencia y demás parámetros (sesión, Q, target, región).
  - `enableTrigger(TriggerCallback, autoInventory=true)` — soporte nativo del gatillo físico (arranca/para el inventario solo); no hace falta leerlo a mano.
- Coordenada JitPack confirmada (el `.aar` resuelve con HTTP 200): `com.github.cslrfid.cs710s-android:csl-rfid-android-sdk:1.1.0`.
- El wrapper y el demo están en **Java**, no Kotlin — no afecta: Kotlin consume la librería igual (es un `.aar` normal). Nuestra APK se escribe en Kotlin.

### Instalación (según el README del fabricante)

`settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

`app/build.gradle`:

```groovy
dependencies {
    implementation 'com.github.cslrfid.cs710s-android:csl-rfid-android-sdk:1.0.0'
}
```

Otras librerías publicadas en JitPack: `cslibrary4a` (controladores BLE/USB de bajo nivel) y `epctagcoder` (codificar/decodificar EPC).

## 2. Qué demuestra el demo oficial

El demo `cs710aquickstart` ejemplifica: **escaneo BLE, conexión, inventario, búsqueda tipo Geiger, monitoreo de batería y soporte del gatillo físico**. Es justo lo que necesita nuestra APK (escaneo + conexión + inventario).

## 3. Apps de prueba ya publicadas (para validar la lectora antes de programar)

| App | Enlace |
|---|---|
| CS108 Java App (Android) | https://play.google.com/store/apps/details?id=com.csl.cs108ademoapp |
| CS108 C# App (Android) | https://play.google.com/store/apps/details?id=csl.cs108fulldemo.demo |

> **Primer hito práctico:** instalar una de estas apps, encender la lectora, conectarla y leer un TAG UHF. Si eso funciona, el hardware está validado y el resto es programación.

## 4. Requisitos del celular y del entorno

- **Celular de la demo:** Samsung Galaxy S24 Ultra, Android 16 (API 36), One UI 8.5. Cumple de sobra el mínimo del wrapper SDK (Android 8.0 / API 26) y coincide con el `targetSdk 36` que usa el SDK.
- Bluetooth Low Energy (BLE) — incluido en el S24 Ultra.
- Android Studio reciente y **JDK 17**.
- Depuración USB activada y `adb devices` mostrando el celular.

## 5. Permisos Android ✅ (verificado 2026-10-06)

Confirmado clonando `cslrfid/cs710s-android` (tag `v1.1.0`) y leyendo el `AndroidManifest.xml` real de `cs710aquickstart`. A diferencia de lo que se suponía antes, **`ACCESS_FINE_LOCATION` sí se pide también en Android 12+** (no solo `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`):

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="s" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

Además, para hablar con la app Windows:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Esto ya está aplicado en `android_app/app/src/main/AndroidManifest.xml`.

## 6. Flujo de la APK

> **Estado actual (2026-10-06):** existe una primera versión mínima en `android_app/` que solo hace "conectar → inventario continuo → mandar cada EPC por HTTP" (sin pantallas de menú, sin Salida a Ruta ni Captura de Tags todavía). El flujo completo de abajo es el objetivo de las Fases 6-7; se construye sobre esta base.

```
Inicio (común):
1. Pedir permisos (BLE + red)
2. Escanear BLE → mostrar lectoras CS108 encontradas → conectar

Menú: "Salida a Ruta" · "Captura de Tags" · "Ajustes"

Modo SALIDA A RUTA (detalle en docs/funcional.md):
3. Esperar lectura del parabrisas (un solo EPC, el de mayor RSSI)
4. GET /api/dispatch/lookup/{truck_epc} → camión + boletas activas (o alarma)
5. El usuario elige una o varias boletas → POST /api/dispatches → se obtiene lo esperado por producto
6. Mientras el GATILLO está presionado: acumular EPC únicos y enviarlos por lote → POST /api/dispatches/{id}/reads
7. Palomeo en pantalla por producto: esperados / leídos (faltan, completo, sobran)
8. "Finalizar lectura" → POST /api/dispatches/{id}/finish
9. Cuadra → "Salida correcta". No cuadra → alarma con diferencias y opciones:
   Repetir lectura (/reset o seguir leyendo) · Autorizar con motivo (/authorize)

Modo CAPTURA DE TAGS:
3. Elegir tipo: Pallet (producto) o Camión
4. Pallet: elegir producto una vez (modo lote) → leer con el gatillo → confirmar → POST /api/tags/batch
5. Camión: elegir camión → leer parabrisas → POST /api/tags/truck
6. Si se leen varias etiquetas nuevas a la vez, pedir confirmación antes de guardar
```

> Contrato completo de endpoints en `docs/api.md`.

## 7. Comunicación con la app Windows

- Cliente HTTP: OkHttp o Retrofit.
- Android 9+ bloquea HTTP sin cifrar: para la demo, permitir *cleartext* con un `network_security_config.xml` o `android:usesCleartextTraffic="true"` en el manifest.
- La IP y el puerto del servidor se configuran en una pantalla de ajustes (la IP aparece en la app Windows).
- Tiempo de espera corto (3–5 s) y mensajes claros ante error de red.
- **Red local en Android 16:** la restricción de acceso a la red local es **opcional** en Android 16 y obligatoria solo para apps con `targetSdk 37` o superior. Con `targetSdk 36` las llamadas HTTP a `192.168.x.x` funcionan sin permiso extra. Si se sube el target a 37, declarar `ACCESS_LOCAL_NETWORK` y solicitarlo en ejecución. (Fuente: [Local network permission, Android Developers](https://developer.android.com/privacy-and-security/local-network-permission).)

### Particularidades de Samsung One UI 8.5
- El ahorro de batería puede cortar la conexión BLE en segundo plano: desactivar la optimización de batería para la APK (Ajustes → Aplicaciones → la APK → Batería → Sin restricciones).
- Mantener la APK en primer plano y la pantalla encendida durante la demo.
- Si Samsung muestra el aviso de "Bloqueador automático" o restringe la instalación del APK, permitir la instalación desde el origen usado (instalación por `adb install` evita ese paso).

## 8. Generar el APK

```bash
./gradlew assembleDebug
# Resultado: app/build/outputs/apk/debug/app-debug.apk
```

Instalar en el celular con `adb install app-debug.apk` o copiando el archivo. Para la demo basta el APK de *debug* (sin firma de producción).

## 9. Modo simulado (plan B)

La APK debe incluir un botón que simule la lectura de un EPC (por ejemplo, uno de los TAGs cargados en la base de datos). Así se puede ensayar y presentar aunque falle el Bluetooth o no haya TAGs a mano. Se recomienda encapsular la lectora detrás de una interfaz (`RfidSource`) con dos implementaciones: `Cs108Source` y `SimulatedSource`.

## 10. Puntos por verificar en el código del demo

- [x] Clases y *callbacks* exactos para escanear, conectar e iniciar inventario (sección 1).
- [x] Cómo se entrega el EPC y el RSSI (`RfidTag.getEpc()` / `getRssi()`).
- [x] Cómo ajustar la potencia de la antena (`RfidManager.configure().powerLevel(n)`).
- [x] Permisos declarados en el manifest (sección 5).
- [x] Soporte de gatillo físico: existe `enableTrigger()` en el SDK, no se probó aún con hardware real.
- [ ] Reconexión si se pierde el Bluetooth (el SDK tiene una opción `autoReconnect` en `RfidManager.builder()`; falta probarla).
- [ ] **Compilar y probar en el S24 Ultra real.** El código de `android_app/` se escribió contra el código fuente real del SDK (clonado y leído, no inventado) y el `.aar` de JitPack existe (`1.1.0`, HTTP 200), pero **no se ha compilado ni ejecutado todavía**: esta máquina no tiene Android Studio/JDK 17 instalados (pendiente de la Fase 3).
