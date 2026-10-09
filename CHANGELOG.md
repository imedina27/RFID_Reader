# Changelog

Todos los cambios relevantes del proyecto se documentan en este archivo.
Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado `0.x.y` mientras el proyecto sea una demo.

> **Regla:** cada cambio se anota primero en **[Sin publicar]** y, al cerrar una sesión o hito, se pasa a una versión nueva con fecha. La versión debe coincidir con la del encabezado de `ROADMAP.md`. Ver `CLAUDE.md`, sección 6.

## [Sin publicar]

### Añadido

- *(nada por ahora)*

## [0.21.0] - 2026-10-07

### Añadido

- **Lista blanca de prefijos de EPC**: pantalla **Prefijos** nueva en Windows (tabla `epc_prefixes`, `E28011` precargado, botones Agregar/Eliminar). Una etiqueta leída que no empiece con ninguno de los prefijos cargados se ignora por completo — ni cuenta, ni se captura, ni genera alarma — tanto en Captura de Tags como en Salida a Ruta. Nuevos endpoints `GET/POST /api/epc-prefixes`, `DELETE /api/epc-prefixes/{id}`. `windows_app/ui/pages/prefijos.py`.
- **APK**: nuevo diálogo "Salida correcta, pero se detectó una etiqueta ya despachada — revisar" (botón "Entendido") cuando el producto cuadra exacto pero se leyó de más una etiqueta ya despachada (posible reetiquetado); nuevo mensaje de advertencia en Captura de Tags para `invalid_prefix`.

### Cambiado

- **Se relajó la regla de bloqueo del Finalizar** (hallazgo de una prueba real: una lectura completa y correcta se bloqueaba por 2 etiquetas totalmente ajenas al proyecto y una 5ª etiqueta real nunca capturada). Ahora una etiqueta `unknown` o `already_dispatched` de más **ya no bloquea** el cierre si el producto esperado por boleta está completo — solo falta/sobra producto de verdad sigue bloqueando. Siguen quedando registradas en `alarms` para revisión posterior. `verification.BLOCKING_READ_RESULTS` ahora vacío; nuevo `api.ALARM_READ_RESULTS` para la condición de registrar la alarma (antes compartía la misma constante que el bloqueo). `POST /dispatches/{id}/finish` ahora regresa `ya_despachadas` (EPCs) aunque cierre `completed`, para que la APK pueda avisar aunque no bloquee.
- `tests/test_verification.py` actualizado a la nueva regla (dos pruebas que esperaban bloqueo ahora esperan que pase, más una prueba nueva que confirma que falta/sobra producto real sigue bloqueando sin importar las etiquetas raras).
- **Panel en vivo del Tablero**: la salida del camión (cualquiera — termine la salida, se confirme que no tiene boletas, o se agote la espera sin confirmar boletas) ahora siempre sigue de frente, en el mismo sentido con el que entró (hacia la derecha), nunca en reversa. `ocultar_camion()` ya anima la salida en vez de desaparecer de golpe (sigue siendo idempotente: no repite la animación si ya está oculto o saliendo). `windows_app/ui/widgets/camion_vivo.py`.

### Documentación

- `docs/funcional.md` (sección 4, reglas de validación), `docs/modelo_datos.md` (tabla `epc_prefixes` y cálculo de verificación) y `docs/api.md` (endpoints de prefijos, respuesta de `/tags/batch` y `/finish`) actualizados a la nueva regla.

## [0.20.0] - 2026-10-07

### Añadido

- **Panel en vivo del Tablero**: el camión (SVG vista superior proporcionado por el usuario, decorativo — no refleja la cantidad real de pallets; recoloreado por tema, `light_camion.svg`/`dark_camion.svg` a partir del original en gris `#969696`) **no se ve al inicio**: aparece con una animación de izquierda a derecha (con desaceleración, recortada a su propio widget) en cuanto se escanea el parabrisas, con círculos de pulso morados mientras escanea pallets (recicla la idea del "leyendo" que tenía la APK y se quitó de ahí). Se centra de verdad dándole el mismo peso a la zona izquierda (Boletas asignadas + Datos de la unidad) y a la derecha (semáforo). El semáforo se pinta al terminar la salida (verde `completed`, amarillo `completed_with_difference`, rojo `cancelled`) y todo se congela 5 s antes de limpiarse solo. La barra de resumen (PostgreSQL/Alarmas/Camiones en ruta/Salidas del día) se movió al fondo de la pantalla. `windows_app/ui/widgets/camion_vivo.py` (SVG + pulsos + animación de entrada con `QPainter`/`QSvgRenderer`), `windows_app/ui/widgets/semaforo.py`, `windows_app/ui/pages/tablero.py` ampliado.
- **`windows_app/live_state.py`**: estado efímero en memoria (no se persiste) compartido entre el hilo de Flask y la ventana, para que el Tablero sepa "se acaba de escanear un camión" aunque `GET /api/dispatch/lookup` no guarde nada en la base — se pierde si se reinicia la app, a propósito.

### Cambiado

- `GET /api/dispatch/lookup/{epc}`: ahora anota el camión resuelto en `live_state` (sin cambiar la respuesta ni el comportamiento de la APK).
- `StyleSheet` (Windows): nueva bandera `current_is_dark`, que `MainWindow.apply_theme()` actualiza y que el widget del camión lee directo en su repintado para saber qué SVG usar, sin necesidad de conectar señales de cambio de tema (primer widget de una pantalla con un ícono propio por tema).

### Documentación

- `docs/funcional.md`: descripción del Tablero actualizada con el panel en vivo.

## [0.19.0] - 2026-10-07

### Añadido

- **Pantalla Alarmas (app Windows) — cierra la Fase 5**: lista con filtro Abiertas/Atendidas/Todas, más reciente arriba, refresco automático cada 2 s. Columnas Fecha, Tipo, Camión (cruzado con la salida que la generó), Mensaje, Atendida por y Estado. **Aviso visual**: filas abiertas resaltadas en amarillo (`faltante`) o rojo (`excedente`/`etiqueta no registrada`/`pallet ya despachado`). **Aviso sonoro**: compara los IDs de alarmas abiertas en cada refresco y suena un beep (`QApplication.beep()`, sin archivos de audio) si aparece una nueva. Botón **Marcar atendida** (pide el nombre de quien atiende, `POST /api/alarms/{id}/ack`). Decisión del usuario: solo cubre los 4 tipos que de verdad se guardan en `alarms` (`missing`, `excess`, `unknown_tag`, `already_dispatched`); `no_active_tickets`/`truck_not_available` se quedan como avisos solo de la APK, sin persistirse. `windows_app/ui/pages/alarmas.py`, nuevas funciones en `api_client.py` (`list_alarms`, `ack_alarm`).

### Cambiado

- **`GET /api/alarms`** ahora cruza el camión de la salida que generó la alarma (`truck_unit_number`, vía `dispatch_id`) y acepta `status=open|ack|all`. Documentado en `docs/api.md`.

### Documentación

- `docs/funcional.md`: descripción de la pantalla Alarmas actualizada a lo realmente construido.

## [0.18.0] - 2026-10-07

### Añadido

- **Botón "Limpiar Estado" en Pallets (app Windows), solo para la demo**: separado a propósito de Agregar/Editar con un espacio ancho, siempre activo, pide confirmación. Regresa a `captured` los pallets cuya salida **más reciente** ya quedó **entregada** (`completed`/`completed_with_difference` + `delivered_at`), para reutilizar las mismas etiquetas físicas en otro ensayo de la demo; no toca los que siguen en ruta o no han salido. Nuevo endpoint `POST /api/tags/reset-delivered`. `windows_app/ui/pages/pallets.py`, nueva función `reset_delivered_tags()` en `api_client.py`.

### Pendiente

- **Relleno morado del botón "Limpiar Estado"**: se intentó con un nuevo estilo `QPushButton#purple_button` (`#7E57C2`, el mismo morado de `Cam_Lens_V2/styles/stylesheet.py`), pero el estilo nativo `windowsvista` no lo pintó (ni agregando un borde explícito del mismo color, el truco habitual para este problema). A pedido del usuario se quitó el estilo sin usar y el botón quedó igual que los demás (`edit_button`) por ahora.

### Documentación

- `docs/api.md` y `docs/funcional.md`: documentado el endpoint y el botón nuevos.

## [0.17.0] - 2026-10-07

### Añadido

- **Pantalla Salidas a ruta (app Windows)**: historial de salidas (más reciente arriba), con refresco automático cada 2 s y 4 filtros (Estado, Camión, Cliente, Folio de boleta). Se separó **"cómo salió"** de **"en qué parte del proceso va"** para no perder información al entregar: `dispatches.status` (`completed`/`completed_with_difference`) ya no cambia nunca y da la columna **Tipo de salida** (Normal / Con autorización); la columna **Estado** (No ha salido / En ruta / Entregado / Cancelada) se calcula combinando `status` con la columna nueva `delivered_at`. Botón **"Ver detalle"**: palomeo completo coloreado (verde/amarillo/rojo, igual que la APK), con refresco propio cada 2 s si la salida sigue "No ha salido" (monitor en vivo) o foto fija si ya terminó — reusa `GET /api/dispatches/{id}/status`. Botón **"Unidad en planta"**: solo si "En ruta"; marca la salida como entregada y libera el camión. Windows no crea ni modifica salidas (eso lo hace la APK); el historial se consulta con SQL directo a PostgreSQL (no hay endpoint de listado), igual que el Tablero. `windows_app/ui/pages/salidas.py`, `windows_app/ui/dialogs/salida_detalle_dialog.py`, nuevas funciones en `api_client.py` (`get_dispatch_status`, `deliver_dispatch`).

### Cambiado

- **`dispatches` tiene una columna nueva, `delivered_at`** (`windows_app/schema.sql`, con migración `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` porque la base ya existía), y un endpoint nuevo, **`POST /api/dispatches/{id}/deliver`** (solo si la salida está `completed`/`completed_with_difference` y aún no entregada; marca `delivered_at` y pone el camión `available`, en una sola operación). Documentado en `docs/api.md` y `docs/modelo_datos.md`.

### Documentación

- `docs/funcional.md`: descripción de la pantalla Salidas a ruta actualizada a lo realmente construido (filtros, columnas Estado/Tipo de salida, botones Ver detalle/Unidad en planta).

## [0.16.0] - 2026-10-07

### Añadido

- **Ícono de la app** (2026-10-07): reemplazado el ícono genérico de Android (`@android:drawable/sym_def_app_icon`) por un ícono adaptativo con el isotipo de Quantum Labs. Nuevo `android_app/app/src/main/res/drawable/ic_launcher_foreground.png` (generado a partir de `logo_quantum.png`, recortado y centrado dentro de la zona segura) + `@color/ic_launcher_background` (`#FAF8F6`) + `mipmap-anydpi-v26/ic_launcher.xml`/`ic_launcher_round.xml`. Sin PNGs por densidad porque `minSdk` ya es 26.
- **Pantalla Camiones (app Windows)** (2026-10-07): alta, edición de placa/chofer y columna de etiqueta de parabrisas. El botón **"Asignar etiqueta"** siempre está disponible (con o sin etiqueta previa) y abre la misma ventana de edición con Número económico/Placa/Chofer deshabilitados y solo el campo Etiqueta activo, para asignarla, cambiarla o borrarla (`CamionDialog`, modo `"etiqueta"`); al aceptar, si había una etiqueta distinta se borra (`DELETE /api/tags/{epc}`) antes de crear la nueva (`POST /api/tags/truck`) — el backend no soporta reemplazar en un solo paso. Sin "Marcar disponible": no hace falta para esta demo (decisión del usuario). Mismo patrón que Productos: `windows_app/ui/pages/camiones.py`, `windows_app/ui/dialogs/camion_dialog.py`, nuevas funciones en `windows_app/ui/api_client.py` (`list_trucks`, `create_truck`, `update_truck`, `list_tags`, `assign_truck_tag`, `delete_tag`). No se tocó el backend: todos los endpoints ya existían. Probado en vivo contra los 111 camiones reales de la base de datos, incluido el reemplazo de una etiqueta vía `curl`.
- **Pantalla Pallets (app Windows)** (2026-10-07, versión simplificada a pedido del usuario): lista **solo de las etiquetas de pallet** (`GET /api/tags?kind=pallet` — las de camión se administran en Camiones, no aquí), más reciente arriba (ya viene ordenado así del backend), con refresco automático cada 2 s (`QTimer`, preservando la selección) para que una captura nueva desde la APK aparezca sola. Botón **Agregar** (captura manual: EPC + producto, `POST /api/tags/batch`) y **Editar** (corrige el producto, `PUT /api/tags/{epc}`). Sin filtros ni botón de eliminar, a pedido del usuario. `windows_app/ui/pages/pallets.py`, `windows_app/ui/dialogs/pallet_dialog.py`, nuevas funciones en `api_client.py` (`capture_pallet_tag`, `update_tag_product`). Las fechas se convierten de UTC a hora local (`America/Mexico_City`) con `zoneinfo`. No se tocó el backend. Probado en vivo por `curl`: un pallet nuevo aparece de inmediato en el primer lugar de la lista.
- **Pantalla Boletas de salida (app Windows)** (2026-10-07): alta con **folio automático** (nuevo consecutivo `exit_ticket_folio_seq` → `BOL-000123`, mismo patrón que el folio de pallets), cliente en texto libre (confirmado: no hay tabla de clientes en esta demo), líneas de producto + cantidad de pallets editables en una tabla embebida dentro del diálogo (botones "+ Línea" / quitar línea por fila), y camión limitado a los **`disponibles`** (una vez asignado no se puede quitar, solo reasignar a otro disponible — la API no soporta desasignar). Botones **Agregar**, **Editar** y **Cancelar boleta** (con confirmación), habilitados solo si la boleta está `active`. `windows_app/ui/pages/boletas.py`, `windows_app/ui/dialogs/boleta_dialog.py`, nuevas funciones en `api_client.py` (`list_exit_tickets`, `create_exit_ticket`, `update_exit_ticket`, `assign_exit_ticket_truck`, `cancel_exit_ticket`). Probado en vivo por `curl`: folio generado correctamente, asignar camión, cancelar y verificar que ya no se puede editar.

### Cambiado

- **`POST /api/exit-tickets` ya no recibe `folio`**: lo genera el propio servidor con el nuevo consecutivo `exit_ticket_folio_seq` (`windows_app/api.py`, `windows_app/schema.sql`), igual que ya pasaba con el folio de los pallets. Documentado en `docs/api.md` y `docs/modelo_datos.md`.

### Documentación

- `ROADMAP.md`: revisados los "Pendientes por confirmar" y la Fase 2; marcados como resueltos el diseño de las pantallas de la APK (mockups aprobados antes de programar), la definición de camiones (110 reales cargados) y el supuesto de autorización con motivo/nombre sin contraseña (confirmado al construirse en `AutorizarActivity`). Quedan pendientes solo las partes de Windows (diseño de pantallas y boletas de ejemplo).
- `docs/lectora.md` y `ROADMAP.md`: leída la etiqueta física de la lectora (foto del usuario, 2026-10-07) — banda **902–928 MHz** (FCC ID `UB4CS108C1GEN2`, IC ID `8073A-CS1082CA`, región EE. UU./Canadá, compatible con México), antena de polarización circular, S/N `VPD21C2MP5519`. **Fase 0 (Hardware) queda completa**; se quitó el riesgo correspondiente de la tabla de riesgos.

### Eliminado

- **Captura de Tags, modo Camión, fuera de alcance de la APK de pruebas** (decisión del usuario, 2026-10-07): se quita de `docs/funcional.md` y `ROADMAP.md` el flujo planeado de "elegir camión → leer parabrisas → asociar etiqueta" desde la APK. Los camiones de la demo ya llegan con su etiqueta de parabrisas asociada por carga directa a la base de datos (`windows_app/import_trucks.py`), así que no hace falta esa pantalla. El endpoint `POST /api/tags/truck` (ya existente en `windows_app/api.py` y documentado en `docs/api.md`) se deja tal cual, para uso futuro desde Windows si hace falta corregir una asociación a mano.

## [0.15.0] - 2026-10-06

### Añadido

- **Menú principal de la APK**: dos botones grandes, "Captura de Tags" y "Salida a Ruta", en la pantalla de conexión (`MainActivity`), desactivados hasta que la lectora queda lista; se quitó de ahí la lista de lecturas crudas (ahora vive en cada pantalla).
- **Captura de Tags, modo Pallet** (`CapturaTagsActivity`): lee un EPC a la vez (se corta el inventario en el primer tag nuevo), combo de productos (`GET /api/products`), guarda con `POST /api/tags/batch`. Ventana de advertencia si la etiqueta ya existe (capturada, de otro producto, de camión o despachada) y aviso "Guardado correcto" de 3 s si es nueva. El producto elegido se queda fijo entre lecturas (para capturar varios pallets seguidos sin reseleccionarlo); "Cancelar" es la única forma de volver a elegir desde cero. Simplifica a propósito el "modo lote" original de `docs/funcional.md`.
- **Salida a Ruta completa**, en cuatro pantallas encadenadas:
  - `SalidaRutaActivity` ("Esperando parabrisas"): mientras el gatillo está presionado se acumula el RSSI más alto visto por EPC (puede haber etiquetas de camiones vecinos cerca); al soltar se usa el EPC ganador y se busca el camión (`GET /api/dispatch/lookup/{epc}`). Si hay boletas activas, se muestran sus datos (Camión, Placa, Boletas activas) con botones Aceptar/Cancelar; si no, ventana de advertencia.
  - `BoletasActivity` ("Boletas activas"): lista con casillas, una tarjeta por boleta (Folio, Cliente, líneas de producto); "Confirmar" abre la salida (`POST /api/dispatches`).
  - `PalomeoActivity` ("Palomeo"): lectura continua (gatillo mantenido, etiquetas únicas acumuladas, cada una se manda de inmediato a `POST /api/dispatches/{id}/reads`), lista de productos esperado/leído con color (verde completo, amarillo falta, rojo sobra/no solicitado), "Reiniciar lecturas" y "Finalizar lectura".
  - `AutorizarActivity` ("Autorizar salida"): motivo + nombre de quien autoriza, cierra la salida como `completada con diferencia` (`POST /api/dispatches/{id}/authorize`).
- **Lógica de diferencia al finalizar** (decisión del usuario, ver `docs/funcional.md` "Resultado en caso de diferencia"): si sobra producto (en cualquier intento) o si solo falta y ya se había avisado antes en la misma salida, el aviso es "Unidad con sobrantes/Faltantes. Regresar a zona de carga o revisar" y "Aceptar" cancela la salida (`POST /api/dispatches/{id}/cancel`, sin tocar boletas/etiquetas/camión) y regresa al inicio de Salida a Ruta. Si solo falta y es la primera vez, el aviso es "Producto Faltante, Revisar Unidad" y "Aceptar" no cancela nada, solo deja seguir leyendo. "Autorizar salida" está disponible en los dos casos.
- Mockups (Artifact, Design canvas) aprobados por el usuario antes de programar cada pantalla nueva: Salida a Ruta (los 4 pasos) y los ajustes pedidos sobre la marcha (datos del camión visibles, botones Aceptar/Cancelar, separación visual camión/boletas).

### Cambiado

- `windows_app/api.py`: mensaje de la alarma `no_active_tickets` cambiado a "Esta unidad no tiene boletas asignadas." (antes "El camión no tiene boletas activas."), a pedido del usuario.
- `.gitignore`: `android_app/**/build/` en vez de solo `android_app/build/` y `android_app/app/build/`, para cubrir los módulos vendorizados nuevos.

### Documentación

- `docs/funcional.md`: Procedimiento A actualizado con la lógica real de "Aceptar"/"Autorizar salida" al finalizar con diferencia.
- `docs/apk.md`: pseudocódigo del flujo de la APK actualizado a lo realmente construido; nuevos puntos verificados con hardware real.

## [0.14.0] - 2026-10-06

### Corregido

- **Bug real del SDK de la lectora:** `CsLibrary4A.onRFIDEvent()` dejaba `responseType` sin asignar en su rama `default:`, y el código que procesa cada lectura hacía `switch` sobre ese valor nulo, provocando un `NullPointerException` intermitente durante el inventario (`android_app/cslibrary4a/.../CsLibrary4A.java`). Se vendorizó el SDK completo (`csl-rfid-android-sdk`, `cslibrary4a`, `epctagcoder`, licencia MIT, ya no se usa JitPack) como módulos locales y se parchó para asignar `HostCmdResponseTypes.NULL` en los dos lugares donde ocurría (CS108 y CS710). Confirmado con log real: ya no truena.
- La potencia de la antena (pantalla de Ajustes) no se volvía a aplicar si se cambiaba con la lectora ya conectada — solo se aplicaba una vez, al conectar. Se agregó la misma llamada en `onResume()`.
- **Causa real de la lectura lenta del gatillo** (se diagnosticó por separado del bug anterior: persistía incluso después del parche, con potencia y batería confirmadas correctas): el *stack* Bluetooth del teléfono, tras muchas conexiones/desconexiones seguidas al mismo lector, negocia un intervalo de conexión cada vez más lento — no depende de la app ni se arregla reiniciando la lectora. Apagar/prender el Bluetooth del teléfono (o modo avión) restablece la velocidad normal; confirmado con hardware real.

### Documentación

- `docs/apk.md`: registrados el bug del SDK (y su parche), el fix de potencia en `onResume()`, y el hallazgo + mitigación de la lectura lenta por degradación del Bluetooth del teléfono.

## [0.13.0] - 2026-10-06

### Añadido

- `android_app/`: imagen corporativa Quantum Labs — tema día/noche real (`values/colors.xml` + `values-night/colors.xml`, sigue el tema del sistema sin lógica propia), logo en la barra superior, y pantalla de **Ajustes** (`SettingsActivity`, ícono de engranaje) con IP, puerto, potencia de la antena (se aplica a la lectora real) y modo simulado (persistido; aún no genera lecturas falsas).
- **Lectura por gatillo físico, probada con hardware real**: `enableTrigger(callback, false)` con manejo manual (mismo patrón que el demo oficial `cs710aquickstart/InventoryActivity`) — lee solo mientras se mantiene presionado y se detiene al soltar. Confirmado con el log interno del SDK, ahora visible (`RfidManager.builder(...).setLogger{}`, etiqueta `RfidSDK` en Logcat).
- Deduplicación por EPC único (`docs/funcional.md`: "acumula etiquetas únicas"): la lectora reporta el mismo tag decenas de veces por segundo; antes de este cambio una sola prueba generó 85+ filas y envíos repetidos del mismo EPC. Ahora cada etiqueta se registra y se manda una sola vez por conexión.

### Corregido

- La barra de título se traslapaba con la barra de estado del sistema (Android 15+/`targetSdk` 35+ exige manejar *edge-to-edge*) — se agregó `android:fitsSystemWindows="true"`.
- Lectura fantasma justo al conectar: el estado del gatillo que reporta la lectora puede llegar erróneo por un instante (carrera en el handshake BLE); se resolvió esperando ~800 ms tras `onReaderReady` antes de activar el gatillo.

### Documentación

- `docs/apk.md`: gatillo físico y deduplicación marcados como verificados con hardware real; nota sobre cómo activar el logger del SDK para diagnóstico.

## [0.12.0] - 2026-10-06

### Añadido

- **Primera prueba de extremo a extremo con hardware real:** el S24 Ultra (depuración USB activada, tras desactivar el "Bloqueador automático" de Samsung que la bloqueaba) instaló la APK, se conectó por BLE a la lectora CS108-2 ("CS108Reader25EED9"), leyó repetidamente el tag validado `E28011C0A500007042D701FB` y lo mandó por Wi-Fi al receptor de prueba (`tools/tag_receiver.py`) corriendo en la laptop.
- `android_app`: persistencia del campo IP:puerto del receptor con `SharedPreferences`, para no tener que volver a escribirlo cada vez que se abre la app.

### Corregido

- `android_app/app/src/main/res/layout/activity_main.xml`: el valor por defecto del campo IP:puerto apuntaba a la IP típica del hotspot (`192.168.137.1`), que no era la red real usada en la prueba; se cambió al valor correcto para esa prueba (de cualquier forma, ahora se recuerda el último que se haya escrito).

### Documentación

- `docs/apk.md`: confirmado qué bloquea exactamente el "Bloqueador automático" de Samsung (la propia opción "Depuración por USB", no solo el origen de instalación) y cómo desbloquearlo.
- `README.md`: nota de troubleshooting — si no llegan lecturas al receptor aunque la IP sea correcta, puede haber dos procesos escuchando el puerto 5000 a la vez (pasó con una instancia vieja de `windows_app/main.py` que había quedado corriendo).

## [0.11.0] - 2026-10-06

### Añadido

- JDK 17 (Eclipse Temurin) y las herramientas de línea de comandos del SDK de Android instaladas localmente (sin la IDE completa), para poder compilar `android_app/` sin esperar a instalar Android Studio.
- **`android_app/` ya compila:** `./gradlew assembleDebug` genera `app-debug.apk` (~7 MB) sin errores; verificado con `aapt2 dump badging` (paquete `com.quantumlabs.rfidreader`, permisos y versiones de SDK correctos).

### Corregido

- `android_app/app/build.gradle` y `android_app/build.gradle`: quitado el plugin `org.jetbrains.kotlin.android` y su dependencia de *classpath* — desde AGP 9.0 el soporte de Kotlin viene integrado y ese plugin ya no se usa (rompía el build con un error explícito).
- `android_app/app/build.gradle`: OkHttp bajado de 5.5.0 a **4.12.0** — la rama 5.x exige `compileSdk` 37+ y este proyecto usa 36 (igual que el SDK de la lectora).

## [0.10.0] - 2026-10-06

### Añadido

- `android_app/`: primera versión de la APK (Kotlin), Fase 6 del `ROADMAP.md`. Se conecta a la lectora CS108-2 por BLE (`com.github.cslrfid.cs710s-android:csl-rfid-android-sdk:1.1.0`), lee tags por inventario continuo y manda cada EPC leído a un receptor HTTP configurable (IP:puerto en pantalla). Sin pantallas de menú, Salida a Ruta ni Captura de Tags todavía (Fase 7).
- `tools/tag_receiver.py`: receptor de prueba en consola, standalone y fuera de `windows_app/` — solo imprime cada EPC recibido, sin tocar PostgreSQL ni la API principal.
- Verificación del SDK de Android: se clonó `cslrfid/cs710s-android` (tag `v1.1.0`) y se leyó el código real del wrapper (`RfidManager` y sus *callbacks*) y del demo `cs710aquickstart`; se confirmó que el `.aar` existe en JitPack. Esto resuelve los pendientes de `docs/apk.md` marcados con ⚠️ desde la Fase 1.

### Cambiado

- `docs/apk.md`: permisos, clases y coordenada de JitPack actualizados con datos verificados (antes eran una suposición). Importante: **`ACCESS_FINE_LOCATION` sí se necesita** también en Android 12+, no solo `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` como se pensaba.

## [0.9.1] - 2026-10-06

### Añadido

- `windows_app/ui/api_client.py`: cliente HTTP de la API local (`http://127.0.0.1:5000/api`), usado por las pantallas de la interfaz.
- `windows_app/ui/dialogs/producto_dialog.py` y `windows_app/ui/pages/productos.py`: pantalla **Productos** funcional — alta, edición (nombre/presentación) y baja/reactivación, sobre la API ya existente. "Baja" marca `active = false` (no se borra el renglón).

### Cambiado

- `windows_app/ui/app.py`: la barra de navegación pasa del lado izquierdo al **derecho** (pedido del usuario).
- `windows_app/ui/styles/stylesheet.py`: corregido el tema claro — los elementos de navegación no seleccionados no tenían color de texto definido y no se veían.

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
