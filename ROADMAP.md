# ROADMAP — Salida a ruta y captura de pallets con RFID (Demo)

> **Estado:** v0.11 — entorno (Fase 3) y backend (Fase 4) completos; interfaz Windows (Fase 5) en progreso (Tablero y Productos funcionando); la APK mínima (Fase 6: conectar + leer + mandar EPC por HTTP) **ya compila** (`app-debug.apk` generado y verificado) — falta probarla contra la lectora y el celular reales. Diseño detallado de pantallas (Fase 2) y el resto de Fases 6-7 por hacer.
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
- [ ] Android Studio + JDK 17; depuración USB en el S24 Ultra y `adb devices` (pendiente, Fase 6).

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
- [ ] Instalar en el S24 Ultra y probar contra la lectora real (falta `adb` / depuración USB; por ahora solo hay herramientas de línea de comandos, no Android Studio).
- [ ] Lectura de un solo EPC (parabrisas, el de mayor RSSI) — hoy el inventario reporta todos los tags que ve.
- [ ] Gatillo físico (el SDK ya trae `enableTrigger()`, falta usarlo) y `SimulatedSource` (modo sin hardware).
- [ ] Ajuste de potencia desde la UI (el SDK ya lo soporta: `configure().powerLevel(n)`).

### Fase 7 — APK: flujos
- [ ] **Captura de Tags** (pallet en modo lote y camión), con salvaguardas.
- [ ] **Salida a Ruta:** parabrisas → boletas → escaneo y palomeo → finalizar → resultado, repetir o autorizar con motivo.
- [ ] Pantalla de ajustes (IP, puerto, potencia, modo simulado).
- [ ] Manejo de errores: sin red, tiempo de espera, Bluetooth desconectado.
- [ ] Generar APK: `./gradlew assembleDebug`.

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

- [ ] Se capturan etiquetas de pallets (por producto) y de camiones desde la APK, y se ven en Windows.
- [ ] Se crea una boleta en Windows y se asigna a un camión.
- [ ] En la APK: parabrisas → boletas → lectura de pallets con el gatillo → palomeo → *Finalizar*.
- [ ] Si cuadra: boleta `despachada`, pallets `despachados` y camión `en ruta`, visible en Windows.
- [ ] Si no cuadra: alarma en la APK y en Windows; se puede repetir la lectura o autorizar con motivo.
- [ ] Todo funciona sobre el hotspot, sin internet.

---

## 9. Próximos pasos inmediatos

1. **Instalar Android Studio** (ya compila por línea de comandos; falta la IDE para depurar con USB) y probar "conectar + leer + mandar EPC" en el S24 Ultra contra la CS108-2 real y `tools/tag_receiver.py`.
2. **Seguir con la Fase 5**: construir las pantallas que faltan (Camiones, Etiquetas/Captura, Boletas de salida, Salidas a ruta) sobre el armazón PyQt6 ya armado.
3. **Abrir el puerto 5000 en el firewall** desde PowerShell como administrador (ver Fase 4) y probar `GET /api/health` desde el celular en la misma red.
4. **Prueba en camión real cargado** con la app demo de CSL (Fase 1): porcentaje de pallets leídos y posición de la etiqueta.
5. **Diseñar los detalles de cada pantalla** (Fase 2) a medida que se construyen, o antes si se prefiere bocetarlas todas primero.
