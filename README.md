# RFID_Reader — Salida a ruta y captura de pallets con RFID

Demo para presentación (no es producción) que verifica con RFID que un camión de reparto sale **con los pallets que pide su boleta de salida, ni más ni menos**, y que permite asociar etiquetas a productos y camiones.

**Versión:** 0.9.1 · **Estado:** entorno (Fase 3) y backend (Fase 4) completos; interfaz PyQt6 (Fase 5) en progreso — Tablero y Productos funcionando, el resto de pantallas y la APK (Fases 6-7) por construir.

## Cómo funciona

```
Etiqueta ~~UHF~~ CS108-2 ──Bluetooth LE──► APK Android ──WiFi / HTTP──► Laptop
                                          (S24 Ultra)             API Flask + PostgreSQL + app Windows
```

- **Lectora CSL CS108-2:** UHF EPC Gen2, Bluetooth LE. No tiene WiFi.
- **Etiquetas Beontag CRUISER WINDSHIELD:** una en el parabrisas de cada camión y una por pallet.
- **APK Android (Kotlin):** modos *Salida a Ruta* y *Captura de Tags*.
- **App Windows (Python):** catálogos, boletas de salida, monitor en vivo de salidas, alarmas y captura manual.

### Salida a Ruta (resumen)
Parabrisas → boletas activas del camión (se eligen una o varias) → lectura de pallets con el gatillo y palomeo por producto → *Finalizar lectura* → si cuadra exacto: boletas `despachada` y camión `en ruta`; si no: alarma en la APK y en Windows, con **Repetir lectura** o **Autorizar con motivo**.

### Captura de Tags (resumen)
Elegir pallet (producto, modo lote) o camión → leer → guardar. El pallet recibe un **folio** consecutivo (`PLT-000123`) y su **fecha de salida de producción** (primera captura).

## Productos de la demo

Coca Cola 2 L · Coca Cola 600 ml · Sprite 600 ml · Agua Cristal 600 ml · Bevi 355 ml

## Estructura del proyecto

```
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
│   ├── ui/                # interfaz PyQt6 (armazón + Tablero; imagen corporativa de Cam_Lens_V2)
│   └── tests/            # pruebas pytest (verification.py)
└── android_app/          # (por crear) proyecto Kotlin
```

## Documentación

| Documento | Contenido |
|---|---|
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
|---|---|
| Laptop | Windows con WiFi (hotspot), Python 3.14 (o 3.13), pipenv, PostgreSQL ya instalado |
| Celular | Samsung Galaxy S24 Ultra, Android 16 (mínimo del SDK: Android 8.0 / API 26) |
| Desarrollo Android | Android Studio y JDK 17 |
| Hardware | Lectora CSL CS108-2 con batería cargada; etiquetas Beontag CRUISER WINDSHIELD |

## Puesta en marcha

1. **Entorno Python:** `pipenv install` (ya instala `flask`, `psycopg[binary]`, `python-dotenv`, `pyqt6` y, como dependencia de desarrollo, `pytest`). Probado con Python 3.14.
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
6. **Red:** activar el *Hotspot móvil* de Windows y conectar el celular. La IP de la laptop se muestra en el Tablero de la app (Fase 5, por construir).
7. **Ejecutar la app:**
   ```powershell
   pipenv run python windows_app/main.py
   ```
   Abre la ventana de escritorio (PyQt6) y levanta la API en un hilo secundario, en `0.0.0.0:5000`.
8. **Pruebas:** `pipenv run pytest windows_app` (función de verificación de la salida).
9. **APK:** instalar `app-debug.apk` (`./gradlew assembleDebug`) y configurar IP y puerto en Ajustes — pendiente (Fases 6-7).

## Advertencias importantes

- **Nunca se escribe en las etiquetas.** La app demo de CSL tiene un botón *Write*: no usarlo.
- El líquido de los refrescos en PET atenúa la señal UHF: hacer la **prueba en un camión real cargado** antes de confiar en el conteo (ver `ROADMAP.md`, Fase 1).
- La etiqueta Beontag solo declara **vidrio** como superficie; en pallets de madera hay que probar la posición.
- Proyecto de **demo**: sin HTTPS ni autenticación robusta.
