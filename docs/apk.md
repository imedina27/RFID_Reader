# Documentación — APK Android para el CS108-2

> Este documento recoge lo que el fabricante publica sobre su SDK de Android. Los puntos marcados con ⚠️ **deben verificarse en el código del demo** durante la Fase 1, porque la documentación pública consultada no detalla nombres de clases ni permisos.
> **2026-10-06:** se clonó `cslrfid/cs710s-android` (tag `v1.1.0`) y se leyó el código real del wrapper SDK y del demo `cs710aquickstart`. Las secciones marcadas ✅ ya están verificadas contra ese código, no son suposición.

## 1. Opciones de SDK oficiales (todas con licencia MIT)

| Opción | Repositorio | Observaciones |
| --- | --- | --- |
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
| --- | --- |
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

> **Estado actual (2026-10-06):** Salida a Ruta y Captura de Tags ya están construidas y probadas con hardware real contra la API real (ya no mandan al receptor de pruebas). **Inventario RFID y Prefijos** (2026-10-09) son pantallas de diagnóstico nuevas, locales al teléfono, sin probar aún con hardware real. Falta el modo simulado (`SimulatedSource`) — ver `ROADMAP.md` Fase 7.

```text
Inicio (común):
1. Pedir permisos (BLE + red)
2. Escanear BLE → mostrar lectoras CS108 encontradas → conectar
3. Menú principal: cuadrícula 2x2 con "Captura de Tags", "Salida a Ruta",
   "Inventario RFID" (las tres desactivadas hasta que la lectora queda
   conectada) y "Prefijos" (siempre disponible, no necesita lectora) --
   MainActivity

Modo SALIDA A RUTA (SalidaRutaActivity → BoletasActivity → PalomeoActivity → AutorizarActivity):
1. "Esperando parabrisas": mientras el gatillo esta presionado se escucha sin
   cortar, guardando el RSSI mas alto visto por EPC (puede haber tags de
   camiones vecinos cerca); al soltar, se toma el EPC ganador
2. GET /api/dispatch/lookup/{truck_epc} → camión + boletas activas, o alarma
   (`no_active_tickets`, `truck_not_available`, o 404 si el tag no es de
   ningun camion registrado) -- se muestra en una ventana de advertencia
3. Si hay boletas: se muestran sus datos (Camion, Placa, Boletas activas)
   bajo el campo "Unidad Detectada...", con botones Aceptar/Cancelar.
   Aceptar → pantalla de Boletas activas (lista con casillas, una tarjeta
   por boleta con Folio/Cliente/lineas) → Confirmar → POST /api/dispatches
4. Palomeo: mientras el GATILLO esta presionado se acumulan EPC unicos y se
   mandan uno a uno → POST /api/dispatches/{id}/reads; lista de productos
   esperado/leido con color (verde completo, amarillo falta, rojo sobra/no
   solicitado); "Reiniciar lecturas" → POST /reset
5. "Finalizar lectura" → POST /api/dispatches/{id}/finish
   - Producto completo (sin importar si hubo etiquetas unknown/already_dispatched
     de mas, decision del usuario 2026-10-07) → dialogo "Salida correcta",
     cierra hasta el menu
   - Producto completo pero con alguna etiqueta already_dispatched de mas
     ("ya_despachadas" no viene vacio en la respuesta) → dialogo distinto,
     "Salida correcta, pero se detecto una etiqueta ya despachada --
     revisar" (boton "Entendido"), cierra igual hasta el menu
   - Falta o sobra producto de verdad → dialogo con dos botones, "Aceptar"
     y "Autorizar salida" (ver docs/funcional.md "Resultado en caso de
     diferencia" para cuando Aceptar cancela la salida de una vez vs.
     cuando solo deja seguir leyendo); "Autorizar salida" pide motivo +
     nombre → POST /api/dispatches/{id}/authorize

Modo CAPTURA DE TAGS (CapturaTagsActivity) -- captura de pallets:
1. Lee una etiqueta (se detiene el inventario en el primer tag nuevo)
2. Aparece el EPC y, debajo, el combo de productos (GET /api/products)
3. Aceptar → POST /api/tags/batch con un solo EPC; segun la respuesta:
   - "created" → aviso "Guardado correcto" (3 s) y se limpia el EPC (el
     producto se queda seleccionado para el siguiente pallet)
   - cualquier otro resultado (ya capturada, de otro producto, es de
     camion, ya despachada, prefijo invalido) → ventana de advertencia,
     Aceptar limpia todo

Lista blanca de prefijos de EPC (GET /api/epc-prefixes, pantalla Prefijos
en Windows): un EPC que no empiece con ninguno de los prefijos cargados se
ignora por completo, tanto en Captura de Tags como en Salida a Ruta -- ni
se guarda, ni cuenta, ni genera alarma (se asume ajeno al proyecto).
4. Cancelar → limpia EPC y producto, foco en el campo

Pendiente: SimulatedSource (modo sin hardware). La etiqueta de camion
(parabrisas) no se captura desde la APK de pruebas: los camiones ya
llegan con su etiqueta asociada por carga directa en la base de datos
(`windows_app/import_trucks.py`).

Modo INVENTARIO RFID (InventarioActivity) -- diagnostico, sin tocar la API
ni la base de datos (pedido del usuario, 2026-10-09):
1. Lectura continua mientras el GATILLO esta presionado (igual que Captura
   de Tags/Salida a Ruta); cada EPC unico se cuenta y se muestra en una
   lista (EPC + "xN" lecturas); pita (ToneGenerator) solo la primera vez
   que aparece cada EPC; debajo del switch de Prefijos hay un contador del
   total de etiquetas unicas leidas
2. Switch "Prefijos": si esta activo, un EPC que no empiece con ninguno de
   los prefijos guardados en la pantalla Prefijos (de la APK, ver abajo) se
   ignora por completo -- no se cuenta, no pita, no aparece en la lista ni
   en el total
3. La lista se reinicia cada vez que se entra a la pantalla y cada vez que
   se cambia el switch (no se mezcla lo leido antes/despues del filtro)
4. No hay flecha de detalle por tag: se evaluo y se descarto (ver nota
   abajo)

Pantalla PREFIJOS (PrefijosActivity) -- solo la usa Inventario RFID:
1. Lista de prefijos de EPC guardada en el propio telefono (SharedPreferences,
   Preferencias.kt), precargada con "E28011"
2. Agregar (normaliza a mayusculas) y Eliminar; sin validar longitud (el
   usuario decide que tan largo es cada prefijo, igual que en Windows)
3. Es independiente de la lista de prefijos del servidor/Windows
   (GET/POST /api/epc-prefixes, tabla epc_prefixes) -- mismo nombre, dos
   listas distintas sin relacion entre si; esta nunca sale del telefono

Nota sobre el detalle del tag: se penso en una flecha ">" para ver "mas
informacion guardada en el tag" (por ejemplo datos de pallet que grabe el
proveedor), pero se descarto antes de programar. El SDK, en la ruta real de
inventario (RfidInventoryManager.processTagData), solo llena EPC, RSSI,
conteo y canal -- nunca TID ni memoria de usuario, aunque el modelo RfidTag
declare esos campos. Leerlos de verdad requeriria usar la capa de mas bajo
nivel del SDK vendorizado (cslibrary4a, comando TYPE_18K6C_TAG_ACCESS), sin
wrapper ni ejemplo existente en el proyecto -- se dejo fuera de esta pantalla
por riesgo y alcance.
```

**Pantalla sin apagarse durante la lectura** (hallazgo real del usuario,
2026-10-09): el apagado automático de la pantalla por inactividad de
Android pausa la lectura igual que si se saliera de la pantalla (y en
Inventario RFID, al volver a encenderla, se perdía toda la lista
acumulada). Las cuatro pantallas de lectura (`CapturaTagsActivity`,
`SalidaRutaActivity`, `PalomeoActivity`, `InventarioActivity`) agregan
`window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` en su
`onCreate` -- evita el apagado automático solo mientras esa pantalla esta
en primer plano; no cambia ningun ajuste del sistema ni afecta a otras
apps, y el usuario sigue pudiendo apagar la pantalla manualmente.

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
- **Bloqueador automático (confirmado 2026-10-06):** en Opciones de desarrollador, "Depuración por USB" puede aparecer apagado y en gris con la leyenda "Bloqueado por Bloqueador automático". Hay que ir a Ajustes → Seguridad y privacidad → Bloqueador automático y apagarlo (o su protección de USB) antes de que se pueda activar la depuración USB.

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
- [x] **Gatillo físico probado con hardware real** (2026-10-06): `enableTrigger(callback, false)` con manejo manual (igual que `cs710aquickstart/InventoryActivity`) — lee solo mientras se mantiene presionado, confirmado con el log interno del SDK ("Trigger state changed: PRESSED/RELEASED" → "Starting/Stopping inventory"). Hallazgo real: justo al conectar, el estado del gatillo puede llegar erróneo por un instante (carrera en el handshake BLE) y disparar una lectura fantasma; se resolvió con una espera de ~800 ms tras `onReaderReady` antes de activarlo.
- [ ] Reconexión si se pierde el Bluetooth (el SDK tiene una opción `autoReconnect` en `RfidManager.builder()`; falta probarla).
- [x] **Compilar.** `./gradlew assembleDebug` genera `app-debug.apk` sin errores (JDK 17 + herramientas de línea de comandos del SDK, instalados localmente sin la IDE completa). Dos ajustes que solo salieron a la luz compilando de verdad:
  - AGP 9+ ya no usa el plugin `org.jetbrains.kotlin.android` (Kotlin viene integrado desde AGP 9.0 — ver https://kotl.in/gradle/agp-built-in-kotlin). Se quitó de `android_app/app/build.gradle` y del classpath de `android_app/build.gradle`.
  - OkHttp se fijó en **4.12.0** (no 5.x): la rama 5 exige `compileSdk` 37+ y este proyecto usa 36, igual que el SDK de la lectora.
- [x] **Probado en el S24 Ultra real contra la CS108-2 real** (2026-10-06): conectó por BLE ("CS108Reader25EED9"), leyó repetidamente el tag validado `E28011C0A500007042D701FB` y lo mandó por Wi-Fi al receptor de prueba en la laptop. Instalado con `adb install` directo (sin Android Studio).
- [x] **Deduplicar por EPC único:** la lectora reporta el mismo tag decenas de veces por segundo mientras el gatillo está presionado (se vieron 85-118 lecturas del mismo EPC en ~2 s en la prueba real) — sin deduplicar, la lista y los envíos al servidor se saturan. Ahora cada EPC se registra y se manda una sola vez por conexión (`docs/funcional.md`: "acumula etiquetas únicas").
- [x] `RfidManager.builder(context).setLogger { ... }` (en vez de `RfidManager.create(context)`) deja ver en Logcat (etiqueta `RfidSDK`) los eventos internos del SDK — imprescindible para diagnosticar el hardware real (así se encontró el problema del gatillo).
- [x] **Bug real del SDK: `NullPointerException` en `CsLibrary4A.onRFIDEvent()`** (2026-10-06): la rama `default:` del `switch` interno no asigna `responseType`, y el llamador (`RfidInventoryManager.processTagData`) hace `switch` sobre ese enum nulo → `NullPointerException: ... HostCmdResponseTypes.ordinal() on a null object reference`. Se vendorizó el SDK completo como módulos locales (`android_app/csl-rfid-android-sdk/`, `cslibrary4a/`, `epctagcoder/`, licencia MIT, ya no se usa el paquete de JitPack) y se parchó `CsLibrary4A.java` en los dos lugares donde ocurre (CS108 y CS710) para asignar `HostCmdResponseTypes.NULL` en vez de dejarlo sin asignar. Confirmado en log: ya no truena, solo aparece `Received response type: NULL` (inofensivo).
- [x] **Causa real de la lectura lenta con el gatillo** (no era el bug anterior, ni la potencia, ni la batería): después de muchas conexiones y desconexiones BLE seguidas al mismo lector en poco tiempo, el *stack* de Bluetooth del **teléfono** (no de la lectora) negocia un intervalo de conexión cada vez más lento con ese dispositivo — esto sobrevive a reinstalar la app, reiniciar la lectora o cambiar la potencia, porque vive en el sistema operativo del celular, no en la app ni en la lectora. **Mitigación confirmada:** apagar y prender el Bluetooth del teléfono (o activar/desactivar modo avión) antes de una sesión larga de pruebas restablece la velocidad normal de lectura.
- [x] La potencia de la antena ajustada en Ajustes solo se aplicaba una vez, al conectar (`onReaderReady`); si se cambiaba después con la lectora ya conectada, no se re-aplicaba. Se agregó `aplicarPotencia()` también en `onResume()` cuando ya hay conexión activa.
- [x] **Captura de Tags, modo Pallet, construida y probada con hardware real contra la API real** (2026-10-06): lectura de un EPC a la vez (se corta el inventario en el primer tag nuevo, a diferencia de Salida a Ruta), selector de producto, `POST /api/tags/batch`; ventana de advertencia si la etiqueta ya existe (capturada, de otro producto, de camión o ya despachada) y aviso de 3 s si se guarda — decisiones del usuario, simplifican el "modo lote" original de `docs/funcional.md` a un flujo de una etiqueta a la vez con el producto fijo entre lecturas.
- [x] **Filtro de un solo EPC por mayor RSSI (parabrisas)**: en Salida a Ruta se acumula el RSSI más alto visto por EPC mientras el gatillo está presionado y, al soltar, se usa el EPC ganador — resuelve el pendiente de leer de más con etiquetas de camiones vecinos cerca.
- [x] **Salida a Ruta construida completa y probada con hardware real contra la API real** (2026-10-06): las 4 pantallas (`SalidaRutaActivity`, `BoletasActivity`, `PalomeoActivity`, `AutorizarActivity`), incluida la lógica de diferencia (ver `docs/funcional.md`). Probado de punta a punta: salida correcta, salida cancelada por faltante (al segundo intento) y por sobrante (al primero), y camión sin boletas activas.
- [x] **Ícono de la app con el isotipo de Quantum Labs** (2026-10-07): se reemplazó el ícono genérico de Android. Ver `ROADMAP.md` Fase 6 y `CHANGELOG.md` para el detalle de los archivos.
