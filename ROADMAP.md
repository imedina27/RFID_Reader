# ROADMAP — Salida a ruta y captura de pallets con RFID (Demo)

> **Estado:** v0.15 — entorno (Fase 3) y backend (Fase 4) completos; interfaz Windows (Fase 5) en progreso (Tablero y Productos funcionando); APK (Fases 6-7) con **Captura de Tags (modo Pallet) y Salida a Ruta completas**, probadas de punta a punta con hardware real contra la API real de `windows_app` (ya no manda al receptor de pruebas): identificación del camión por el EPC de mayor RSSI, selección de boletas, palomeo en vivo, Finalizar con sus dos resultados (correcta / diferencia con "Aceptar" que cancela o deja seguir leyendo según el caso, y "Autorizar salida" con motivo). Pendiente de la APK: Captura de Tags modo Camión y `SimulatedSource`. Diseño detallado de pantallas de Windows (Fase 2) y las pantallas que faltan de la Fase 5 siguen pendientes.
> **Documentación:** `docs/funcional.md` (qué hace el sistema), `docs/modelo_datos.md`, `docs/api.md`, `docs/lectora.md`, `docs/tag.md`, `docs/apk.md`.
> **Alcance:** proyecto para presentación/demo. No está pensado para producción (sin HTTPS, sin autenticación robusta, sin alta disponibilidad).

---

## 1. Objetivo

Un sistema que verifica, con RFID, que un camión de reparto sale **con los pallets que pide su(s) boleta(s) de salida, ni más ni menos**, y que permite asociar cada etiqueta a su producto.

Tres piezas:

1. **Lectora CSL CS108-2** (UHF, Bluetooth LE) que lee las etiquetas Beontag.
2. **APK Android** (Samsung S24 Ultra) que opera la lectora en campo: *Salida a Ruta* y *Captura de Tags*.
3. **App Windows (Python + PostgreSQL)** para catálogos, boletas de salida, monitoreo en vivo y alarmas.

```
Etiqueta ~~UHF~~ CS108-2 ──BLE──► Celular (APK) ──WiFi/HTTP──► Laptop (API Flask + PostgreSQL + app Windows)
                                       ▲                                   │
                                       └─────── resultados y alarmas ──────┘
```

> **Hallazgo clave:** el CS108 **no tiene WiFi**. Solo Bluetooth 4.1 (BLE) y USB-C. El WiFi lo aportan el celular y la laptop.

### Resumen del flujo (detalle en `docs/funcional.md`)

**Salida a Ruta:** parabrisas → boletas activas del camión (se eligen una o varias) → lectura de pallets con el gatillo y palomeo contra lo pedido → *Finalizar lectura* → si cuadra exacto: boletas `despachada`, camión `en ruta`; si no cuadra: alarma en la APK y en Windows, con **Repetir lectura** o **Autorizar con motivo**.

**Captura de Tags:** elegir pallet (producto, modo lote) o camión → leer → guardar la etiqueta asociada. Los pallets que salen de la línea de producción se capturan igual.

---

## 2. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Lectora | CSL CS108-2 — UHF EPC Gen2, Bluetooth LE, sin WiFi |
| Etiquetas | Beontag CRUISER WINDSHIELD (UHF Gen2v2, Impinj M780). EPC único de 96 bits. Hay más de 1,000; la prueba usa **20–30** |
| EPC validado | Lectura real con la app demo de CSL: `E28011C0A500007042D701FB` (24 hex). Etiquetas sin contraseña (00000000). **No se escribe en las etiquetas** |
| Qué identifica la etiqueta | Un **producto** (pallet) o un **camión** (parabrisas), no un pallet único. Etiqueta de **un solo uso** (supuesto) |
| Datos del pallet | **Folio** (consecutivo `PLT-000123` generado por el sistema al capturar), **Producto** y **Fecha de salida de producción** (= fecha y hora de la primera captura; no cambia después) |
| Productos de la demo | Coca Cola 2 L, Coca Cola 600 ml, Sprite 600 ml, Agua Cristal 600 ml, Bevi 355 ml |
| Estado de etiquetas de pallet | `capturada` → `despachada`; una etiqueta `despachada` no se puede contar de nuevo |
| Boleta de salida | Folio, cliente y líneas **producto + pallets completos** (más fecha de creación y camión asignado, que pone el sistema); una o varias boletas por camión; se asigna el camión desde Windows. Sin campos extra |
| Verificación | Por **totales por producto**; debe cuadrar exacto |
| Tras una alarma | **Repetir lectura** o **Autorizar con motivo** (queda registrado) |
| Captura de tags | En la **APK**, con vista y corrección en Windows (también captura manual pegando el EPC) |
| Pallets de línea de producción | Es la misma captura de tags (modo lote) |
| Producto de la demo | Refrescos en PET sobre pallets de **base de madera**; algunos con emplaye |
| Camiones | **Reales**, con etiqueta en el parabrisas |
| SDK Android | `csl-rfid-android-sdk` (wrapper por callbacks, MIT, vía JitPack) |
| App Windows | Python + **Flask** (API) + interfaz **PyQt6/PySide6** (recomendado sobre Tkinter por el número de pantallas; se confirma en la Fase 3) |
| Base de datos | **PostgreSQL** (ya instalado), `psycopg` v3 |
| Entorno Python | **pipenv** (`Pipfile`, Python 3.14) |
| Celular | Samsung Galaxy S24 Ultra, Android 16, One UI 8.5 |
| APK | Kotlin, `minSdk 26`, `targetSdk 36`, JDK 17 |
| Red | **Hotspot de la laptop**; alternativa: hotspot del celular |

## 3. Pendientes por confirmar

- [ ] **Prueba de lectura en un camión real cargado** (porcentaje de pallets leídos) y **dónde pegar la etiqueta** en el pallet de madera (con y sin emplaye).
- [ ] **Diseño de las pantallas** de la APK y de Windows (Fase 2).
- [ ] Supuestos de `docs/funcional.md` sección 8 (etiqueta de un solo uso, un producto por pallet, autorización con motivo sin contraseña, regreso de ruta manual).
- [ ] Etiqueta de la lectora: banda de frecuencia (debe ser 902–928 MHz en México), modelo completo y número de serie.
- [ ] Abrir el puerto 5000 en el firewall de Windows (requiere PowerShell como administrador; no se pudo ejecutar desde esta sesión por falta de permisos elevados).
- [ ] Boletas de ejemplo reales para la demo (depende de clientes/camiones confirmados en la Fase 2).
- [ ] Instalar **Android Studio** (la IDE completa) para poder editar con autocompletado y depurar con el S24 Ultra conectado por USB — por ahora solo se instalaron JDK 17 y las herramientas de línea de comandos del SDK (suficiente para compilar).

### Ya resuelto
- [x] Lectora, etiqueta, SDK, base de datos, celular y red definidos.
- [x] Hito de hardware: lectora + S24 Ultra + etiqueta leyendo (captura de pantalla del usuario).
- [x] Procedimientos de Salida a Ruta y Captura de Tags (`docs/funcional.md`).
- [x] Modelo de datos y contrato de la API (borradores).
- [x] Productos de la demo (5) y datos del pallet (folio, producto, fecha de salida de producción).
- [x] Campos de la boleta: folio, cliente y líneas; sin campos extra.
- [x] Versión de PostgreSQL y puerto confirmados: **PostgreSQL 18.6, puerto 5432**; base `RFID_Reader`.
- [x] Compatibilidad de `psycopg[binary]` y **PyQt6** con Python 3.14 confirmada (ambos instalan e importan sin problema).
- [x] Repositorio git inicializado, `.gitignore` y remoto configurado (`https://github.com/imedina27/RFID_Reader.git`).
- [x] Backend de la app Windows (Fase 4): `schema.sql`, `db.py`, `api.py` (todos los endpoints de `docs/api.md`), `verification.py` con pruebas pytest, `seed.py`, `main.py`. Probado de extremo a extremo contra la base real (salida correcta, salida con diferencia, autorización con motivo).
- [x] Permisos y clases exactas del SDK de Android: clonado `cslrfid/cs710s-android` (tag `v1.1.0`) y leído el código real del wrapper y del demo (`docs/apk.md`).
- [x] **110 camiones reales** cargados con su etiqueta de parabrisas (`windows_app/import_trucks.py`, a partir del archivo de flota del usuario; 4 registros con número económico repetido se resolvieron quedándose con el más reciente).
- [x] JDK 17 (Temurin) y las herramientas de línea de comandos del SDK de Android instaladas localmente (sin la IDE); `android_app/` compila: `app-debug.apk` generado y verificado (paquete, permisos y versiones correctas con `aapt2 dump badging`).
- [x] **Captura de Tags (modo Pallet) y Salida a Ruta completas, probadas de punta a punta con hardware real contra la API real** (2026-10-06): ver detalle en Fases 6 y 7 y en `docs/apk.md`/`docs/funcional.md`. Incluye mockups aprobados por el usuario para cada pantalla antes de programarlas.

---

## 4. Arquitectura

### 4.1 App Windows (Python)
- **Proceso único:** la interfaz corre en el hilo principal y Flask en un hilo secundario, en `0.0.0.0:5000`.
- **Persistencia:** PostgreSQL local; la cadena de conexión sale de `.env` (no se versiona). Solo la app Windows accede a la base de datos.
- **Esquema:** `schema.sql` (ver `docs/modelo_datos.md`) se ejecuta al arrancar.
- **Tiempo real:** la interfaz consulta PostgreSQL cada 1–2 s para mostrar el palomeo en vivo y las alarmas nuevas (aviso visual y sonoro).
- **Transacciones:** el cierre de una salida (boletas, etiquetas y camión) se hace en una sola transacción.

### 4.2 APK Android (Kotlin)
- Interfaz `RfidSource` con `Cs108Source` (SDK real) y `SimulatedSource` (plan B).
- **Gatillo físico:** mientras está presionado, la APK acumula EPC únicos y los envía a la API por lotes; el palomeo se actualiza con la respuesta.
- **Parabrisas:** lectura de un solo EPC (el de mayor RSSI).
- **Potencia de la antena** ajustable para no leer lo que está fuera del camión.
- Modo simulado para ensayar sin lectora.

### 4.3 Estructura de repositorio sugerida

```
RFID_Reader/
├── ROADMAP.md
├── Pipfile / Pipfile.lock
├── .gitignore              # incluir .env
├── docs/
│   ├── funcional.md  modelo_datos.md  api.md
│   ├── lectora.md    tag.md           apk.md
├── windows_app/
│   ├── main.py  api.py  db.py  schema.sql  .env.example
│   └── ui/                 # pantallas (Tablero, Productos, Camiones, Etiquetas, Boletas, Salidas, Alarmas)
└── android_app/            # proyecto Kotlin
```

---

## 5. Fases y tareas

### Fase 0 — Hardware ✅ (casi completa)
- [x] Lectora CS108-2 identificada; etiqueta Beontag definida.
- [x] Lectora, S24 Ultra y etiqueta leyendo con la app demo de CSL.
- [ ] Leer la etiqueta de la lectora: banda, antena y número de serie.

### Fase 1 — Pruebas físicas y estudio del SDK
- [ ] **Prueba en camión real cargado:** etiquetas en pallets de madera con y sin emplaye, en la cara exterior, y medir qué porcentaje de pallets se lee desde las puertas y los costados. Probar potencias distintas.
- [ ] Probar **etiquetas junto a refrescos en PET** (líquido) y en pallets del fondo del camión; probar con el gatillo en varias pasadas.
- [ ] Probar si se leen pallets o camiones vecinos y a qué potencia deja de ocurrir.
- [ ] Definir **posición estándar de la etiqueta** en el pallet.
- [ ] Probar la lectura del parabrisas (distancia y ángulo, desde el frente del camión).
- [x] Clonar `cslrfid/cs710s-android`, abrir el demo `cs710aquickstart` y completar `docs/apk.md` (clases, callbacks, permisos, RSSI, potencia, gatillo) — hecho 2026-10-06.

**Entregable:** resultados de las pruebas físicas (sigue pendiente); `docs/apk.md` ya sin pendientes de código.

### Fase 2 — Diseño de pantallas
- [ ] Definir con el usuario los diseños (bocetos) de las pantallas de la APK y de Windows listadas en `docs/funcional.md`, sección 6.
- [ ] Definir camiones (número económico y placa) y boletas de ejemplo para la demo.

### Fase 3 — Entorno de desarrollo ✅
- [x] pipenv: `flask`, `psycopg[binary]`, `python-dotenv`, `pyqt6` (confirmado con Python 3.14) y `pytest` (dev).
- [x] PostgreSQL: base `RFID_Reader` creada (PostgreSQL 18.6, puerto 5432, rol existente `qua_admin`).
- [x] `windows_app/.env` (no versionado) y `windows_app/.env.example`; `.gitignore` en el repo.
- [x] JDK 17 + `adb` funcionando con el S24 Ultra conectado por USB (depuración USB habilitada; hubo que desactivar el "Bloqueador automático" de Samsung, que la bloqueaba). Falta solo Android Studio como IDE (se puede seguir compilando e instalando por línea de comandos sin ella).

### Fase 4 — App Windows: backend ✅ (casi completa)
- [x] `schema.sql` y `db.py` según `docs/modelo_datos.md`.
- [x] API según `docs/api.md`: catálogos, etiquetas y captura por lote, boletas, **salida a ruta**, alarmas.
- [x] Reglas de `docs/funcional.md` sección 4 (alarmas, bloqueos, cierre transaccional) — verificadas con pruebas de extremo a extremo.
- [x] `verification.py`: función aislada de verificación con 12 pruebas pytest.
- [x] `seed.py` con los 5 productos de la demo (camiones y boletas de ejemplo reales quedan para la Fase 2).
- [x] Probado con el cliente de pruebas de Flask contra la base real (equivalente a curl/Postman): salida correcta, con diferencia y autorización.
- [ ] Abrir el puerto 5000 en el firewall (requiere PowerShell como administrador):
  ```powershell
  netsh advfirewall firewall add rule name="RFID API" dir=in action=allow protocol=TCP localport=5000
  ```

### Fase 5 — App Windows: interfaz (en progreso)
- [x] Armazón de la ventana (PyQt6): encabezado, logo e imagen corporativa reutilizados de `Cam_Lens_V2` (`windows_app/ui/`), navegación a la **derecha** con las 7 pantallas y tema oscuro/claro (corregido: los botones no seleccionados ya se ven bien en modo claro).
- [x] Proceso único funcionando: Flask corre en un hilo secundario y la ventana en el hilo principal (`windows_app/main.py`).
- [x] **Tablero**: estado de PostgreSQL, alarmas abiertas, camiones en ruta y salidas del día, consultando PostgreSQL directamente (no vía HTTP) con refresco cada 2 s.
- [x] **Productos**: catálogo con alta, edición (nombre/presentación) y baja/reactivación (`active`), vía la API HTTP (`windows_app/ui/api_client.py`, `windows_app/ui/dialogs/producto_dialog.py`). La "baja" no borra el renglón (el `id` sigue referenciado por etiquetas y líneas de boleta).
- [ ] Camiones, Etiquetas/Captura, Boletas de salida, Salidas a ruta (monitor en vivo) — siguen como placeholders ("en construcción") en `windows_app/ui/pages/`.
- [ ] Aviso visual y sonoro de alarmas (el Tablero ya muestra el conteo, falta el aviso sonoro/destacado).
- [ ] Aviso claro si PostgreSQL o la API no están disponibles (el Tablero ya marca "No disponible" en rojo; falta un aviso más visible en el resto de pantallas).

### Fase 6 — APK: lectura (versión mínima en progreso)
- [x] Proyecto Android (`android_app/`, Kotlin) con `csl-rfid-android-sdk` v1.1.0: conectar a la CS108-2 (escaneo BLE + conexión) y leer por inventario continuo, mandando cada EPC por HTTP a un receptor de pruebas (`tools/tag_receiver.py`, fuera de la app principal).
- [x] **Compila:** `./gradlew assembleDebug` genera `app-debug.apk` (~7 MB) sin errores. En el camino se corrigieron dos cosas que solo se descubren compilando de verdad: AGP 9+ ya no usa el plugin `org.jetbrains.kotlin.android` (Kotlin viene integrado) y OkHttp se bajó a 4.12.0 (la rama 5.x exige `compileSdk` 37+).
- [x] **Instalado y probado en el S24 Ultra real contra la CS108-2 real** (2026-10-06): se conectó por BLE a "CS108Reader25EED9", leyó el tag validado `E28011C0A500007042D701FB` repetidamente y lo mandó por Wi-Fi al receptor de prueba en la laptop — confirmado viendo la lectura llegar a `tools/tag_receiver.py`.
  - Se agregó persistencia (`SharedPreferences`) del campo IP:puerto para no tener que volver a escribirlo cada vez que se abre la app.
  - **Ojo para la próxima prueba:** si no llegan lecturas aunque la IP sea correcta, revisar que no haya **dos** procesos escuchando el puerto 5000 a la vez (`netstat -ano | findstr :5000`) — pasó que una instancia vieja de `windows_app/main.py` seguía corriendo de una prueba anterior y se quedaba con las peticiones en vez del receptor.
- [x] **Imagen corporativa Quantum Labs y pantalla de Ajustes** (2026-10-06): tema día/noche real (`values/colors.xml` + `values-night/colors.xml`, sigue el tema del sistema sin lógica propia), logo en la barra superior, y `SettingsActivity` (engranaje) con IP, puerto, potencia de la antena (sí se aplica a la lectora real: `configure().powerLevel(n)`) y el interruptor de modo simulado (persistido; **aún no genera lecturas falsas**, eso queda pendiente).
- [x] **Gatillo físico funcionando con hardware real**: `enableTrigger(callback, false)` + manejo manual (igual que el demo oficial `cs710aquickstart/InventoryActivity`) — lee únicamente mientras se mantiene presionado y se detiene al soltarlo. Se verificó con el log interno del SDK (`RfidManager.builder(...).setLogger{}`, agregado para depuración): "Trigger state changed: PRESSED/RELEASED" → "Starting/Stopping inventory".
  - **Hallazgo real de hardware:** justo al conectar, el estado del gatillo puede llegar erróneo por un instante (carrera en el handshake BLE) y disparar una lectura fantasma; se resolvió esperando ~800 ms tras `onReaderReady` antes de activar el gatillo.
  - **Deduplicación por EPC único** (`docs/funcional.md`: "acumula etiquetas únicas"): la lectora reporta el mismo tag decenas de veces por segundo mientras el gatillo está presionado; sin deduplicar, la lista y los envíos al servidor se saturaban con el mismo EPC repetido (se vieron 85+ envíos de un solo tag en una prueba). Ahora cada EPC se registra y se manda una sola vez por conexión.
  - Corregido: la barra de título se traslapaba con la barra de estado del sistema (edge-to-edge, obligatorio desde `targetSdk` 35+) — se agregó `android:fitsSystemWindows="true"`.
- [x] **Bug real del SDK vendorizado y parchado** (2026-10-06): `NullPointerException` intermitente durante el inventario, causado por `CsLibrary4A.onRFIDEvent()` (rama `default:` del switch interno, sin asignar `responseType`). Se vendorizó el SDK completo como módulos locales (`android_app/csl-rfid-android-sdk/`, `cslibrary4a/`, `epctagcoder/`, MIT, ya no JitPack) y se parchó en los dos lugares donde ocurría. Confirmado en log real: ya no truena.
- [x] **Diagnóstico de la lectura lenta con el gatillo** (2026-10-06): no era el bug anterior (persistía con el parche puesto), ni la potencia (confirmada correctamente aplicada, 26.0 dBm), ni la batería (confirmada sana, 65%/3.79V). Causa real: el Bluetooth del **teléfono**, tras muchas conexiones/desconexiones seguidas al mismo lector, negocia un intervalo de conexión cada vez más lento — sobrevive a reinstalar la app o reiniciar la lectora porque vive en el sistema del celular. **Mitigación confirmada con hardware real:** apagar/prender el Bluetooth del teléfono (o modo avión) antes de una sesión larga de pruebas.
- [x] Corregido de paso: la potencia ajustada en Ajustes solo se aplicaba una vez al conectar; ahora también se reaplica en `onResume()` si ya hay conexión activa.
- [x] **Lectura de un solo EPC por mayor RSSI** (parabrisas, 2026-10-06): en Salida a Ruta se acumula el RSSI más alto visto por EPC mientras el gatillo está presionado y, al soltar, se usa el ganador — resuelve leer de más con etiquetas de camiones vecinos cerca.
- [x] **Menú principal**: dos botones grandes ("Captura de Tags", "Salida a Ruta") en la pantalla de conexión, desactivados hasta que la lectora queda lista.
- [ ] `SimulatedSource` (modo sin hardware) — el interruptor en Ajustes ya existe pero todavía no simula lecturas.

### Fase 7 — APK: flujos
- [x] **Captura de Tags, modo Pallet** (2026-10-06): un EPC a la vez (se corta el inventario en el primer tag nuevo), selector de producto (que se queda fijo entre lecturas), `POST /api/tags/batch` real, ventana de advertencia si la etiqueta ya existe (capturada, de otro producto, de camión o despachada) y aviso de 3 s al guardar. Simplifica el "modo lote" original de `docs/funcional.md` a pedido del usuario — la etiqueta de un solo uso y la asociación a un producto no cambian.
- [ ] **Captura de Tags, modo Camión** (elegir camión → leer parabrisas → `POST /api/tags/truck`) — no empezado.
- [x] **Salida a Ruta completa** (2026-10-06), probada de punta a punta con hardware real contra la API real: `SalidaRutaActivity` (parabrisas → camión encontrado/advertencia) → `BoletasActivity` (selección múltiple con casillas, `POST /dispatches`) → `PalomeoActivity` (lectura continua, palomeo con color, Reiniciar/Finalizar) → `AutorizarActivity` (motivo + nombre). Ver `docs/funcional.md` "Resultado en caso de diferencia" para la lógica de cuándo "Aceptar" cancela la salida (unidad regresa a zona de carga) o solo deja seguir leyendo.
- [x] Pantalla de ajustes (IP, puerto, potencia, modo simulado) — hecha desde la Fase 6.
- [ ] Manejo de errores más robusto: tiempos de espera, Bluetooth desconectado a medio palomeo (hoy hay avisos básicos, no es robusto).
- [x] Generar APK: `./gradlew assembleDebug` — se usa en cada cambio.

### Fase 8 — Pruebas de extremo a extremo
- [ ] Hotspot real de la demo.
- [ ] Capturar 20–30 etiquetas; crear boletas; asignar camión; salida correcta.
- [ ] Casos de alarma: faltante, excedente, etiqueta no registrada, pallet ya despachado, camión sin boletas, camión ya en ruta, dos boletas en un camión, autorización con motivo, repetir lectura.
- [ ] Fallas de infraestructura: servidor apagado, WiFi caído, Bluetooth desconectado.

### Fase 9 — Preparación de la presentación
- [ ] Guion de la demo y datos creíbles cargados.
- [ ] Plan B: modo simulado y video del flujo funcionando.
- [ ] Baterías cargadas, cable USB-C, etiquetas de repuesto.
- [ ] Ensayo completo en condiciones reales.

---

## 6. Puntos críticos de red y conexión

1. **Hotspot de la laptop (recomendado):** IP habitual `192.168.137.1`. Windows puede pedir una conexión de origen; probar antes si se activa sin internet. Alternativa: hotspot del S24 Ultra (la IP de la laptop cambia; se muestra en el Tablero).
2. El celular usa **dos radios a la vez**: Bluetooth (lectora) y WiFi (laptop). Probarlo.
3. **PostgreSQL no se expone a la red**; solo se abre el puerto 5000:
   ```powershell
   netsh advfirewall firewall add rule name="RFID API" dir=in action=allow protocol=TCP localport=5000
   ```
4. **HTTP sin cifrar:** permitirlo con `network_security_config.xml` o `usesCleartextTraffic="true"`.
5. **Android 16:** permisos `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT`; con `targetSdk 36` no hace falta el permiso de red local (obligatorio solo desde `targetSdk 37`).
6. **One UI:** batería de la APK en "Sin restricciones", app en primer plano y pantalla encendida.
7. El CS108 **no se empareja desde Ajustes**; la APK lo busca y conecta por BLE.
8. El emulador no tiene Bluetooth real; la lectora se prueba con el S24 Ultra.
9. Si el hotspot del celular aísla clientes, usar el de la laptop.

---

## 7. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| **Refrescos en PET (líquido) atenúan la señal**; pallets del fondo o del centro del camión quedan tapados → falsos "faltantes" | Etiqueta en la cara exterior y alta del pallet; varias pasadas con el gatillo acumulando; prueba en camión real en la Fase 1 |
| Se leen pallets de camiones vecinos o del andén → falsos "excedentes" | Bajar la potencia; probar a la distancia real; reiniciar lecturas; autorización con motivo |
| La etiqueta solo declara vidrio como superficie; madera y emplaye pueden cambiar el rendimiento | Prueba de ubicación en pallet real; alternativas: tarjeta o espaciador |
| Sobre metal (flejes, racks) la etiqueta casi no lee | Pegarla lejos del metal |
| La lectura del parabrisas se confunde con pallets o con otro camión | Lectura separada de un solo EPC por mayor RSSI; validar el tipo en la API; ignorar etiquetas de camión en el escaneo |
| La etiqueta es antifraude: se destruye al despegarla | Un solo uso; sobran etiquetas |
| Un pallet se cuenta en dos camiones | Estado `despachada` por etiqueta |
| Captura accidental de etiquetas ajenas en modo lote | Confirmación cuando se leen varias etiquetas nuevas a la vez |
| La API o PostgreSQL no están disponibles al presentar | Verificación al arrancar, aviso claro, probar antes |
| `psycopg` o la librería de interfaz sin paquete para Python 3.14 | Probar en la Fase 3; si falla, Python 3.13 |
| La banda de la lectora no corresponde a México | Revisar la etiqueta (902–928 MHz) |
| Falla Bluetooth o el SDK en la presentación | Modo simulado + video de respaldo |
| IP cambia o el firewall bloquea | IP visible en el Tablero; regla de firewall probada antes |
| One UI cierra el Bluetooth en segundo plano | APK en primer plano, pantalla encendida, batería sin restricciones |

---

## 8. Criterios de éxito de la demo

- [x] Se capturan etiquetas de pallets (por producto) desde la APK — probado con hardware real, falta el modo Camión (ver Fase 7).
- [ ] Se crea una boleta en Windows y se asigna a un camión (hoy se hace directo en la base de datos para pruebas; falta la pantalla de Boletas en Windows, Fase 5).
- [x] En la APK: parabrisas → boletas → lectura de pallets con el gatillo → palomeo → *Finalizar* — probado de punta a punta con hardware real.
- [x] Si cuadra: boleta `despachada`, pallets `despachados` y camión `en ruta` — confirmado en la base de datos.
- [x] Si no cuadra: alarma registrada y la unidad regresa a la zona de carga (o se autoriza la salida con motivo) — probados ambos caminos con hardware real.
- [ ] Todo funciona sobre el hotspot, sin internet (probado hasta ahora en wifi normal con ambos dispositivos).

---

## 9. Próximos pasos inmediatos

1. **Terminar la Fase 7 de la APK**: Captura de Tags modo Camión (`POST /tags/truck`) y `SimulatedSource` (modo sin hardware, plan B de la demo).
2. **Seguir con la Fase 5**: construir las pantallas que faltan (Camiones, Etiquetas/Captura, Boletas de salida, Salidas a ruta — esta última ya tiene su contraparte funcionando en la APK) sobre el armazón PyQt6 ya armado.
3. **Abrir el puerto 5000 en el firewall** desde PowerShell como administrador (ver Fase 4) y probar `GET /api/health` desde el celular en la misma red (hoy se probó con ambos en la misma Wi-Fi normal, falta probar con el hotspot).
4. **Prueba en camión real cargado** con la app demo de CSL (Fase 1): porcentaje de pallets leídos y posición de la etiqueta.
5. **Diseñar los detalles de cada pantalla** (Fase 2) a medida que se construyen, o antes si se prefiere bocetarlas todas primero.
6. Instalar Android Studio cuando se quiera editar/depurar con más comodidad (no es bloqueante: ya se puede compilar, instalar y ver logs por línea de comandos).
