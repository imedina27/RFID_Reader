# RFID_Reader — Salida a ruta y captura de pallets con RFID

Demo para presentación (no es producción) que verifica con RFID que un camión de reparto sale **con los pallets que pide su boleta de salida, ni más ni menos**, y que permite asociar etiquetas a productos y camiones.

**Versión:** 0.21.2 · **Estado:** entorno (Fase 3) y backend (Fase 4) completos; interfaz PyQt6 (Fase 5) **completa** (Tablero con panel en vivo del escaneo, Productos, Camiones, Pallets, Boletas de salida, Salidas a ruta, Alarmas y Prefijos — lista blanca de EPC); la APK tiene **Captura de Tags y Salida a Ruta completas**, probadas de punta a punta con hardware real contra la API real (S24 Ultra + CS108-2), e ícono propio con el isotipo de Quantum Labs. Falta el modo simulado de la APK (ver `docs/apk.md` y `ROADMAP.md`).

## Cómo funciona

```text
Etiqueta ~~UHF~~ CS108-2 ──Bluetooth LE──► APK Android ──WiFi / HTTP──► Laptop
                                          (S24 Ultra)             API Flask + PostgreSQL + app Windows
```

- **Lectora CSL CS108-2:** UHF EPC Gen2, Bluetooth LE. No tiene WiFi.
- **Etiquetas Beontag CRUISER WINDSHIELD:** una en el parabrisas de cada camión y una por pallet.
- **APK Android (Kotlin):** modos *Salida a Ruta* y *Captura de Tags*.
- **App Windows (Python):** catálogos, boletas de salida, monitor en vivo de salidas, alarmas y captura manual.

### Salida a Ruta (resumen) — funcionando en la APK

Parabrisas (EPC de mayor RSSI) → boletas activas del camión (se eligen una o varias con casillas) → lectura continua de pallets con el gatillo y palomeo por producto → *Finalizar lectura* → si cuadra exacto: boletas `despachada` y camión `en ruta`; si no cuadra, aviso con dos opciones: **Aceptar** (la unidad regresa a la zona de carga y se cancela la salida, o solo deja seguir leyendo si es la primera vez que falta algo) o **Autorizar salida** con motivo. Detalle en `docs/funcional.md`.

### Captura de Tags (resumen) — modo Pallet funcionando en la APK

Lee una etiqueta, elige el producto y guarda — una a la vez, con aviso si la etiqueta ya existe. El pallet recibe un **folio** consecutivo (`PLT-000123`) y su **fecha de salida de producción** (primera captura). El modo Camión (asociar una etiqueta de parabrisas nueva) todavía no está construido.

## Productos de la demo

Coca Cola 2 L · Coca Cola 600 ml · Sprite 600 ml · Agua Cristal 600 ml · Bevi 355 ml

## Estructura del proyecto

```text
RFID_Reader/
├── README.md  ROADMAP.md  CHANGELOG.md  CLAUDE.md
├── Pipfile / Pipfile.lock
├── docs/
│   ├── funcional.md      # procedimientos, reglas, alarmas, pantallas
│   ├── modelo_datos.md   # esquema PostgreSQL
│   ├── api.md            # contrato de la API
│   ├── lectora.md        # CSL CS108-2
│   ├── tag.md            # etiqueta Beontag
│   └── apk.md            # SDK, permisos y flujos de la APK
├── windows_app/
│   ├── schema.sql  db.py  api.py  verification.py  seed.py  import_trucks.py  main.py
│   ├── .env (no versionado)  .env.example
│   ├── ui/                # interfaz PyQt6 completa: Tablero, Productos, Camiones, Pallets, Boletas, Salidas, Alarmas, Prefijos (imagen corporativa de Cam_Lens_V2)
│   └── tests/            # pruebas pytest (verification.py)
├── android_app/          # proyecto Kotlin: Captura de Tags y Salida a Ruta completas
└── tools/
    └── tag_receiver.py    # receptor de prueba en consola, fuera de la app principal
```

## Documentación

| Documento | Contenido |
| --- | --- |
| [`ROADMAP.md`](ROADMAP.md) | Decisiones, fases, pendientes, riesgos y próximos pasos |
| [`CHANGELOG.md`](CHANGELOG.md) | Historial de versiones |
| [`CLAUDE.md`](CLAUDE.md) | Instrucciones y convenciones para Claude / asistentes de código |
| [`docs/funcional.md`](docs/funcional.md) | Qué hace el sistema, paso a paso |
| [`docs/modelo_datos.md`](docs/modelo_datos.md) | Tablas, estados y lógica de verificación |
| [`docs/api.md`](docs/api.md) | Endpoints y ejemplos de respuesta |
| [`docs/lectora.md`](docs/lectora.md) | Especificaciones y manuales de la lectora |
| [`docs/tag.md`](docs/tag.md) | Especificaciones y recomendaciones de la etiqueta |
| [`docs/apk.md`](docs/apk.md) | SDK de Android, permisos y flujos de la APK |

## Requisitos

| Componente | Requisito |
| --- | --- |
| Laptop | Windows con WiFi (hotspot), Python 3.14 (o 3.13), pipenv, PostgreSQL ya instalado |
| Celular | Samsung Galaxy S24 Ultra, Android 16 (mínimo del SDK: Android 8.0 / API 26) |
| Desarrollo Android | Android Studio y JDK 17 |
| Hardware | Lectora CSL CS108-2 con batería cargada; etiquetas Beontag CRUISER WINDSHIELD |

## Puesta en marcha

1. **Entorno Python:** `pipenv install` (ya instala `flask`, `psycopg`, `python-dotenv`, `pyqt6` y, como dependencia de desarrollo, `pytest`). Probado con Python 3.14. `psycopg` usa su implementación pura Python (sin el extra `binary`) porque en esta máquina una política WDAC de la empresa bloquea su `.pyd` sin firma; `windows_app/db.py` agrega automáticamente la carpeta `bin` de PostgreSQL al `PATH` del proceso para que encuentre `libpq.dll`.
2. **Base de datos** (PostgreSQL, ya instalado — en esta máquina: 18.6, puerto 5432): crear la base con tu rol existente, por ejemplo

   ```sql
   CREATE DATABASE "RFID_Reader" OWNER tu_rol;
   ```

3. **Configuración:** crear `windows_app/.env` (no se versiona; ver `windows_app/.env.example`) con

   `DATABASE_URL=postgresql://usuario:clave@localhost:5432/RFID_Reader`.

4. **Esquema y datos de ejemplo:**

   ```powershell
   pipenv run python windows_app/seed.py
   ```

   (crea las tablas si no existen y carga los 5 productos de la demo).

5. **Firewall (PowerShell como administrador):**

   ```powershell
   netsh advfirewall firewall add rule name="RFID API" dir=in action=allow protocol=TCP localport=5000
   ```

6. **Red:** activar el *Hotspot móvil* de Windows y conectar el celular. La IP de la laptop se muestra en el Tablero de la app.
7. **Ejecutar la app:**

   ```powershell
   pipenv run python windows_app/main.py
   ```

   Abre la ventana de escritorio (PyQt6) y levanta la API en un hilo secundario, en `0.0.0.0:5000`.

8. **Pruebas:** `pipenv run pytest windows_app` (función de verificación de la salida).

## Probar la APK (Captura de Tags y Salida a Ruta)

`android_app/`: imagen corporativa Quantum Labs, pantalla de Ajustes (engranaje), y menú principal con **Captura de Tags** y **Salida a Ruta** — ambas probadas de punta a punta con hardware real (S24 Ultra + CS108-2) contra la API real de `windows_app/main.py` (ya no se usa `tools/tag_receiver.py` para esto). Para probarla:

1. Con `windows_app/main.py` corriendo (paso 7 de arriba), compilar (`android_app\gradlew.bat assembleDebug`) e instalar `app-debug.apk` en el S24 Ultra (`adb install -r` con el celular conectado por USB y la depuración USB activada, o copiando el archivo).
2. Toca el engranaje (⚙) y confirma la IP de la laptop (`ipconfig`) y el puerto 5000 — se recuerdan entre sesiones.
3. En la pantalla principal, toca la tarjeta de conexión para conectar con la lectora; los botones **Captura de Tags** y **Salida a Ruta** se activan cuando queda lista.
4. **Captura de Tags:** lee una etiqueta de pallet, elige el producto y dale Aceptar — se guarda con folio consecutivo (`PLT-000123`).
5. **Salida a Ruta:** lee el parabrisas del camión, confirma sus boletas activas, lee los pallets con el gatillo (palomeo en vivo) y dale Finalizar.

**Si no llegan lecturas aunque la IP sea correcta:** puede haber dos procesos escuchando el puerto 5000 a la vez (por ejemplo, una instancia vieja de `windows_app/main.py` que quedó corriendo). Revisa con `netstat -ano | findstr :5000` y cierra el proceso que sobre.

**Si "Depuración por USB" aparece bloqueada:** es el "Bloqueador automático" de Samsung — Ajustes → Seguridad y privacidad → Bloqueador automático, apágalo (o su protección de USB) antes de activar la depuración.

## Advertencias importantes

- **Nunca se escribe en las etiquetas.** La app demo de CSL tiene un botón *Write*: no usarlo.
- El líquido de los refrescos en PET atenúa la señal UHF: hacer la **prueba en un camión real cargado** antes de confiar en el conteo (ver `ROADMAP.md`, Fase 1).
- La etiqueta Beontag solo declara **vidrio** como superficie; en pallets de madera hay que probar la posición.
- Proyecto de **demo**: sin HTTPS ni autenticación robusta.
