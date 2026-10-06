# Changelog

Todos los cambios relevantes del proyecto se documentan en este archivo.
Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado `0.x.y` mientras el proyecto sea una demo.

> **Regla:** cada cambio se anota primero en **[Sin publicar]** y, al cerrar una sesión o hito, se pasa a una versión nueva con fecha. La versión debe coincidir con la del encabezado de `ROADMAP.md`. Ver `CLAUDE.md`, sección 6.

## [Sin publicar]

### Añadido
- *(nada por ahora)*

## [0.9.0] - 2026-10-06

### Añadido
- `windows_app/ui/`: armazón de la interfaz de escritorio (Fase 5), en **PyQt6**. Encabezado, logo e imagen corporativa de Quantum Labs reutilizados tal cual de `C:\Users\Meki\Documents\Python\Cam_Lens_V2` (no se modificó ese proyecto): `ui/images/` (header, logo, logo con texto, íconos de tema/flechas/combo), `ui/widgets/imagen_escalada.py` y la paleta de colores y hoja de estilos (`ui/styles/stylesheet.py`, recortada a lo que esta app usa) con tema oscuro/claro.
- `windows_app/ui/app.py`: ventana principal con barra superior, logo, título, botón de tema y navegación izquierda a las 7 pantallas de `docs/funcional.md` (Tablero, Productos, Camiones, Etiquetas, Boletas de salida, Salidas a ruta, Alarmas).
- `windows_app/ui/pages/tablero.py`: pantalla **Tablero** funcional — IP y puerto de la API, estado de PostgreSQL, alarmas abiertas, camiones en ruta y salidas del día; consulta PostgreSQL directamente (no vía HTTP) con refresco cada 2 s, según `ROADMAP.md` sección 4.1.
- `windows_app/ui/pages/placeholder.py`: pantalla genérica "en construcción" para las 6 pantallas que aún no se implementan.
- `windows_app/main.py`: ahora levanta la ventana PyQt6 en el hilo principal y la API Flask en un hilo secundario (proceso único, como exige `ROADMAP.md`); probado de extremo a extremo (la ventana abre y `GET /api/health` responde al mismo tiempo).

## [0.8.0] - 2026-10-06

### Añadido
- Repositorio git inicializado (`origin` → `https://github.com/imedina27/RFID_Reader.git`) y `.gitignore` (excluye `.env`, entornos virtuales y build de Android).
- Entorno pipenv confirmado en **Python 3.14**: `flask`, `psycopg[binary]`, `python-dotenv`, `pyqt6` (compatibilidad confirmada con 3.14) y `pytest` como dependencia de desarrollo.
- Base de datos `RFID_Reader` creada en PostgreSQL **18.6** (puerto 5432); `windows_app/.env` (no versionado) y `windows_app/.env.example`.
- `windows_app/schema.sql`: esquema completo según `docs/modelo_datos.md`, ejecutado y verificado contra PostgreSQL real.
- `windows_app/db.py`: conexión (`psycopg` v3) y carga del esquema al arrancar.
- `windows_app/verification.py`: función aislada de verificación de una salida (esperado vs. leído, por producto), con **12 pruebas automáticas** en `windows_app/tests/test_verification.py` (regla más importante de la demo, `CLAUDE.md` sección 4).
- `windows_app/api.py`: API Flask completa según `docs/api.md` — catálogos (productos, camiones), etiquetas y captura por lote, boletas de salida, salida a ruta (lookup, abrir, lecturas, estado, reset, finalizar, autorizar, cancelar) y alarmas. El cierre de una salida (boletas + etiquetas + camión) se hace en una sola transacción por solicitud.
- `windows_app/seed.py`: carga los 5 productos de la demo.
- `windows_app/main.py`: punto de entrada; inicializa el esquema y levanta la API en `0.0.0.0:5000`.
- Probado de extremo a extremo contra la base de datos real: salida correcta, salida con diferencia (faltante + etiqueta no registrada) y autorización con motivo; restricciones "un camión, una sola salida en proceso" y "una sola etiqueta de parabrisas por camión" verificadas.
- `windows_app/import_trucks.py`: importa camiones reales y su etiqueta de parabrisas desde el archivo de flota (`docs/vehicleInfo_43.xlsx`, no versionado). Se cargaron **110 camiones** con tag válido (de 114 filas con tag; 4 se omitieron por re-etiquetado, quedándose con el registro más reciente por número económico).

### Cambiado
- `docs/api.md`: `POST /tags/batch` documenta el resultado `already_dispatched` (etiqueta de pallet ya despachada que se intenta recapturar).
- `.gitignore`: se excluyen los `.xlsx` de `docs/` (datos reales de la flota; no se versionan).

### Documentación
- `ROADMAP.md`: Fase 3 completa y Fase 4 (backend) casi completa; pendientes actualizados.
- `README.md`: estado y pasos de puesta en marcha actualizados a lo ya implementado.

## [0.7.0] - 2026-10-06

### Añadido
- `CLAUDE.md`: instrucciones del proyecto para Claude y otros asistentes (contexto, orden de lectura, stack, convenciones, reglas de negocio, cosas que no se deben hacer).
- `README.md`: descripción general, estado, estructura e índice de documentación.
- `CHANGELOG.md`: este archivo de versiones.

### Documentación
- Regla permanente: en cada sesión se actualizan siempre `ROADMAP.md`, `README.md` y `CHANGELOG.md`.

## [0.6.0] - 2026-10-06

### Añadido
- Lista de productos de la demo: Coca Cola 2 L, Coca Cola 600 ml, Sprite 600 ml, Agua Cristal 600 ml y Bevi 355 ml (datos de ejemplo en `docs/modelo_datos.md`).
- Datos del pallet: **folio** consecutivo generado por el sistema (`PLT-000123`), producto y **fecha de salida de producción** (= primera captura, no cambia).
- Secuencia `pallet_folio_seq` y columna `tags.folio` en el modelo de datos.

### Cambiado
- `docs/api.md`: las respuestas de etiquetas incluyen `folio` y `production_date`.
- `docs/funcional.md`: el palomeo muestra los folios de los pallets leídos; la pantalla de etiquetas lista folio, EPC, producto, fecha y estado.
- Boleta de salida confirmada sin campos adicionales (folio, cliente y líneas; el sistema agrega fecha de creación y camión).

## [0.5.0] - 2026-10-06

### Añadido
- `docs/funcional.md`: procedimientos de **Salida a Ruta** y **Captura de Tags**, reglas de validación, tipos de alarma, estados y lista de pantallas de la APK y de Windows.
- `docs/modelo_datos.md`: esquema PostgreSQL (productos, camiones, etiquetas, boletas, salidas, alarmas, lecturas) y lógica de verificación y cierre.
- `docs/api.md`: contrato de la API (catálogos, captura por lote, boletas, salida a ruta, alarmas).
- Estado de etiqueta de pallet (`capturada` → `despachada`) para evitar contar un pallet dos veces.

### Cambiado
- `ROADMAP.md` reestructurado: nuevas fases (pruebas físicas en camión real, diseño de pantallas, backend, interfaz Windows, flujos de la APK).
- `docs/apk.md`: flujos de la APK actualizados a *Salida a Ruta* y *Captura de Tags*.
- Decisiones acordadas: cantidad en pallets completos, tras alarma se puede repetir o autorizar con motivo, captura en la APK con vista en Windows, pallets de línea de producción = misma captura.

## [0.4.0] - 2026-10-06

### Cambiado
- Temática del proyecto: de lectura genérica de TAGs a **pallets en camiones** (camión identificado por la etiqueta del parabrisas).
- `docs/tag.md` reescrito: explicación de EPC frente a número impreso, uso de la etiqueta en pallets (fuera de diseño, solo declara vidrio), reparto de etiquetas de prueba.

### Añadido
- Riesgos físicos: líquido en PET, madera y emplaye, metal, interferencia entre etiquetas.
- Validación de hardware con captura de pantalla de la app demo de CSL (EPC `E28011C0A500007042D701FB`).

## [0.3.0] - 2026-10-06

### Cambiado
- Base de datos: de SQLite a **PostgreSQL** (`psycopg` v3, `schema.sql` al arrancar, `.env` para la conexión).
- Entorno Python: se usa **pipenv** (`Pipfile` existente, Python 3.14).
- Celular definido: Samsung Galaxy S24 Ultra, Android 16, One UI 8.5 (`targetSdk 36`).
- Red: hotspot de la laptop como opción principal.

### Documentación
- `docs/apk.md`: notas de Android 16 (permiso de red local opcional hasta `targetSdk 37`) y de Samsung One UI (batería, primer plano).

## [0.2.0] - 2026-10-06

### Añadido
- `docs/lectora.md`: ficha técnica de la lectora CSL CS108-2 (Bluetooth 4.1 BLE y USB-C, **sin WiFi**, UHF EPC Gen2).
- `docs/apk.md`: SDK oficial de CSL para Android (`csl-rfid-android-sdk`, MIT, JitPack, `minSdk 26`), permisos esperados y flujo de la APK.
- `docs/tag.md`: ficha técnica de la etiqueta Beontag CRUISER WINDSHIELD (Impinj M780, 865–928 MHz, EPC único de 96 bits).

### Cambiado
- `ROADMAP.md` v0.2: lectora identificada y compatibilidad lectora–etiqueta confirmada.

## [0.1.0] - 2026-10-06

### Añadido
- `ROADMAP.md` inicial (borrador): objetivo, arquitectura (Flask + app Windows + APK Kotlin), fases, puntos críticos de red y riesgos.
- Carpeta del proyecto `RFID_Reader` con entorno pipenv.
