# Ficha técnica — Lectora CSL CS108-2

> Fuente: documentación pública del fabricante (Convergence Systems Limited) y distribuidores. Los puntos marcados con ⚠️ **no están confirmados** y deben verificarse con la etiqueta física del equipo o con el manual.

## 1. Resumen

El CS108 es un lector RFID **UHF** portátil tipo "sled" (se acopla a un celular o se usa con la empuñadura). Lee TAGs **EPC Gen2 / ISO 18000-6C** y se comunica con el celular o la PC por **Bluetooth Low Energy**. Tiene un puerto USB-C, pero **no tiene WiFi**.

## 2. Respuesta a "¿la lectora tiene WiFi?"

**No.** El fabricante lo indica expresamente: *"No, Bluetooth (BLE) is the only radio communication link"*. La lectora solo se enlaza por **Bluetooth 4.1 (BLE)** y por **cable USB-C**.

Consecuencia para el proyecto: el WiFi lo ponen el **celular** (que habla con la app Windows) y la **PC**. La lectora solo habla con el celular por Bluetooth.

```text
TAG ~~RF UHF~~ CS108 ──BLE──► Celular (APK) ──WiFi/HTTP──► PC (app Windows)
```

## 3. Especificaciones

| Característica | Valor |
| --- | --- |
| Fabricante / modelo | Convergence Systems Limited (CSL) — CS108 (variante "-2" ⚠️) |
| Tecnología RFID | UHF, EPC Class 1 Gen 2 / ISO 18000-6C, modo Dense Reader disponible |
| Chip lector | Impinj Indy R2000 |
| Conectividad | Bluetooth 4.1 (BLE) y USB-C |
| WiFi | **No** |
| Alcance | Hasta ~20 m (antena lineal) / ~15 m (antena circular), según TAG y entorno |
| Batería | 3,400 mAh, reemplazable en campo |
| Carga | USB-C (primera carga ≈ 4 h); también base CS108D o cargador externo CS108C |
| Códigos de barras | Módulo 2D opcional (sufijo "2D" en el modelo) |
| Sistemas compatibles | Android, iOS, Windows y Linux |
| Protección | IP54 (según distribuidor, versión con antena circular) |

### Sobre el sufijo "-2" ✅ confirmado (2026-10-07)

Confirmado con la etiqueta física del equipo: el "-2" indica la **región de frecuencia FCC/IC** (EE. UU./Canadá, **902–928 MHz**), que es la misma banda que usa México. La etiqueta trae **FCC ID** (`UB4CS108C1GEN2`) e **IC ID** (`8073A-CS1082CA`), certificaciones que solo aplican a equipos de esa región de frecuencia — no hace falta revisar más, el equipo ya es el correcto para la demo en México.

### Datos de la etiqueta del equipo (confirmado 2026-10-07)

- [x] **Modelo completo:** Sled Handheld Reader CS108-2, antena de **polarización circular** ("Cir. Pol.").
- [x] **Banda de frecuencia / región:** 902–928 MHz (FCC/IC, EE. UU./Canadá) — compatible con México.
- [x] **Número de serie:** `VPD21C2MP5519`.
- [x] **FCC ID:** `UB4CS108C1GEN2` · **IC ID:** `8073A-CS1082CA` · **BT MAC:** `6C:79:B8:25:EE:D9`.
- [ ] Versión de firmware (visible desde la app demo; no leída de la etiqueta).

## 4. TAGs compatibles

Como el equipo es UHF EPC Gen2, necesitas **TAGs UHF Gen2** (etiquetas adhesivas, tarjetas o llaveros UHF). **No sirven** las tarjetas NFC/Mifare, ni las de proximidad de 125 kHz.

- El identificador que se lee es el **EPC**, normalmente de 96 bits (24 caracteres hexadecimales), por ejemplo `E2000017221101441890XXXX`.
- Ese EPC será la **llave primaria** de la tabla `tags` en la app Windows.
- **TAG definido para el proyecto:** Beontag CRUISER WINDSHIELD (UHF Gen2v2, chip Impinj M780, 865–928 MHz, para vidrio). Es compatible con la CS108-2. Ver `docs/tag.md`.

## 5. Cómo funciona en la práctica

1. Se enciende con el botón de encendido (mantener ~3 s; LED verde fijo).
2. El botón de emparejamiento parpadea al arrancar, lo que indica que está listo para ser descubierto.
3. **El emparejamiento lo hace la aplicación**, no el menú Bluetooth del celular: el CS108 no se empareja como un auricular, sino que la APK lo busca por BLE y se conecta.
4. Se lee con el **gatillo** del equipo o por comando desde la app (inventario).

## 6. Consideraciones importantes para la demo

- **Lee varios TAGs a la vez y de lejos.** Con 15–20 m de alcance, el inventario puede devolver TAGs que no pretendías leer. Para la demo conviene bajar la potencia de la antena, mostrar solo el TAG con mayor señal (RSSI) o dejar solo un TAG a la vez cerca.
- **Un mismo TAG se reporta muchas veces** por segundo. La APK debe filtrar duplicados antes de consultar a la app Windows.
- La antena es de **polarización lineal o circular** según el modelo; con la circular la orientación del TAG importa menos.
- Batería: cargarla completa antes de la presentación y llevar el cable USB-C.

## 8. Qué más puede hacer y parámetros de software (SDK)

> Fuente: código del wrapper `android_app/csl-rfid-android-sdk` (`com.csl.rfidsdk`) y del SDK de bajo nivel vendorizado `android_app/cslibrary4a` (`com.csl.cslibrary4a.CsLibrary4A`), revisados el 2026-10-09.

### 8.1 Capacidades además de leer EPC

| Capacidad | Qué hace | ¿La usa esta APK? |
| --- | --- | --- |
| Inventario EPC | Lectura masiva de TAGs Gen2 | Sí — es el núcleo de la app |
| Geiger search | Ayuda a localizar físicamente un TAG concreto guiándose por su RSSI (como un detector de metales) | No |
| Escaneo de código de barras | El CS108-2 trae un módulo lector de barras integrado | No |
| Batería | Reporta voltaje y porcentaje de carga | Se consulta al conectar (`MainActivity.kt`), pero solo se manda al log de depuración (`Log.d`) — **no se muestra en ninguna pantalla** de la APK |
| Gatillo físico | Detecta presión/suelta del botón para iniciar o parar la lectura | Sí, en todas las pantallas de lectura |
| Beep / vibración del propio lector | El CS108-2 puede pitar/vibrar al detectar un TAG (distinto del beep que la APK genera en el teléfono para Inventario RFID) | Existe en el SDK pero no se activa; queda en su valor de fábrica |
| Conexión Bluetooth (BLE) | Buscar, conectar y desconectar la lectora | Sí |
| Firmware / identificación | Versión de firmware, número de serie, modelo | No se consulta desde la app |
| ⚠️ Write / Lock / Kill (escribir, bloquear o inutilizar un TAG) | Existe en `CsLibrary4A` (bajo nivel) | **No se expone ni se debe exponer** — prohibido por `CLAUDE.md` sección 7 ("nunca se escribe en la memoria de las etiquetas") |

### 8.2 Parámetros configurables vía `RfidManager.configure()`

| Parámetro | Para qué sirve | ¿Se ajusta en esta APK? |
| --- | --- | --- |
| **Potencia (`powerLevel`)** | Qué tan fuerte transmite la antena; más potencia = mayor alcance de lectura, pero también lee TAGs que no interesan | **Sí** — único parámetro con control en pantalla (Ajustes, slider), ver `docs/apk.md` |
| Sesión Gen2 (`session`, S0–S3) | Cuánto tiempo "recuerda" un TAG ya inventariado antes de volver a responder en otra ronda | No, queda en el valor por defecto |
| Target (`A`/`B`/`AB_FLIP`) | Controla qué grupo de TAGs responde en cada ronda; ayuda a leer más rápido cuando hay muchos TAGs a la vez | No |
| Q value | Parámetro del algoritmo anticolisión Gen2 (tamaño de la ventana de slots); bajo = más rápido con pocos TAGs, alto = necesario con muchos TAGs simultáneos | No |
| Modo de inventario (`STANDARD`/`COMPACT`) | Compacto = más rápido con menos datos por lectura; estándar = más datos, más lento | No, se usa compacto por defecto |
| Región de frecuencia (`region`) | Banda de radio permitida según el país | Definida en el wrapper pero **nunca aplicada a propósito** — se deja la región de fábrica del equipo (902–928 MHz FCC/IC, ya correcta para México) |
| Incluir RSSI / fase / canal por lectura | Agregar datos extra a cada lectura de TAG | RSSI se usa en la lógica de negocio (p. ej. "mayor señal" en Salida a Ruta); fase y canal no se activan |

**Nota:** hoy la demo solo necesita ajustar la potencia. El resto de los parámetros Gen2 (sesión, target, Q) se dejan en su valor de fábrica porque funcionan bien para la cantidad de TAGs de la prueba (20–30). Si con más TAGs simultáneos la lectura se vuelve lenta o errática, estos son los primeros parámetros a revisar.

### 8.3 Memoria del TAG y comandos Write / Lock / Kill (solo informativo — **prohibido usarlos**, ver `CLAUDE.md` sección 5.1)

Un TAG EPC Gen2 (ISO 18000-6C) no es solo "el EPC": tiene **4 bancos de memoria** separados.

| Banco | Qué guarda | Tamaño típico |
| --- | --- | --- |
| **Reserved** | Los dos passwords del TAG: *Kill password* (32 bits) y *Access password* (32 bits) | 64 bits |
| **EPC** | El identificador EPC (el que leemos hoy) + un CRC + un bit de protocolo (PC) | 96–496 bits según el TAG |
| **TID** (Tag ID) | Identificador de fábrica: fabricante del chip y modelo. Normalmente no se puede reescribir | 32–96+ bits |
| **User** | Memoria libre para que el integrador guarde lo que quiera (texto, fechas, códigos) — no todos los TAGs la traen, y su tamaño varía | 0 a varios Kbits |

El Beontag CRUISER WINDSHIELD del proyecto probablemente tiene banco User disponible (común en TAGs de parabrisas), pero no se ha verificado ni se usa.

#### ¿Qué se puede guardar en el TAG? (`Write`)

`Write` escribe datos en cualquier banco (excepto TID, normalmente bloqueado de fábrica):

- **Banco EPC:** se puede reemplazar el EPC actual por otro definido por el integrador (p. ej. codificar ahí un folio de pallet en vez de un EPC aleatorio de fábrica).
- **Banco User:** texto o datos libres — aquí es donde un proveedor podría grabar "fecha de salida, tipo de producto". Es memoria cruda (bytes), no una base de datos; el formato hay que definirlo.
- **Banco Reserved:** ahí se escriben los *passwords* (ver Lock/Kill). Por defecto suelen venir en `00000000` (sin password).

El lector manda el comando `Write` del protocolo Gen2 indicando banco, offset y los datos (palabra por palabra de 16 bits). Si el banco tiene un *access password* distinto de cero, primero hay que "abrir" el TAG enviando ese password (comando `Access`).

#### ¿Cómo se bloquea? (`Lock`)

`Lock` no cambia los datos, cambia el **permiso** de un banco. Cada banco (más los dos passwords) tiene su propio estado de protección, con 4 combinaciones posibles:

| Estado | Significado |
| --- | --- |
| Open (sin bloquear) | Cualquiera puede leer y escribir ese banco, sin password |
| Lock (bloqueado con password) | Solo se puede escribir si se manda el *access password* correcto; la lectura sigue libre |
| Permalock (lectura/escritura libre, fijo) | El estado de protección queda fijo para siempre, ya no se puede cambiar ni con password |
| Permalock + bloqueado (irreversible) | El banco queda congelado para siempre: nadie, nunca, lo puede volver a escribir |

El comando `Lock` recibe una máscara indicando qué banco(s) bloquear y en qué modo. Si se bloquea con *access password* y ese password se pierde, se pierde el acceso de escritura a ese banco para siempre (salvo Kill).

#### ¿Cómo se inutiliza? (`Kill`)

`Kill` **desactiva el chip de forma permanente e irreversible**: el TAG deja de responder a cualquier comando de radio para siempre (ni lectura, ni escritura, ni reactivación). El objeto físico sigue intacto, pero el chip queda "radio-silencioso".

1. El TAG debe tener un *Kill password* distinto de cero grabado en Reserved (en `00000000`, Kill no funciona — protección de fábrica contra un Kill accidental).
2. El lector manda el comando `Kill` con ese password de 32 bits.
3. Si coincide, el chip se autodestruye lógicamente.

Uso típico: privacidad en retail (matar el TAG de un producto en la caja). En logística casi no se usa — el TAG se reutiliza o se desecha con el objeto.

#### Por qué este proyecto los prohíbe

Son modificaciones del **TAG físico**, no de la base de datos: un error de software se corrige con un deploy, pero un Write mal hecho, un Lock con password perdido o un Kill accidental deja un TAG físico inservible — con 20–30 TAGs para una demo con fecha fija, ese riesgo no se justifica. Por eso `CLAUDE.md` exige **solo lectura del EPC**, nunca Write/Lock/Kill, aunque el SDK de bajo nivel (`CsLibrary4A`) los soporte.

## 9. Documentación oficial

| Recurso | Enlace |
| --- | --- |
| Página del producto | https://www.convergence.com.hk/cs108/ |
| Descargas CS108 | https://www.convergence.com.hk/downloads/cs108/ |
| Hoja de datos (PDF) | https://www.convergence.com.hk/wp-content/uploads/2021/10/CS108-Spec-Sheet-V4-1_30-09-2021.pdf |
| Manual de usuario (PDF) | https://github.com/cslrfid/CS108-Product-Downloads/blob/master/Manuals/CS108-User-Manual.pdf |
| Especificación del protocolo Bluetooth/USB (PDF) | https://github.com/cslrfid/CS108-Product-Downloads/blob/master/Manuals/CS108_and_CS463_Bluetooth_and_USB_Byte_Stream_API_Specifications.pdf |
| Notas de versión de firmware y app demo (PDF) | https://github.com/cslrfid/CS108-Product-Downloads/blob/master/Manuals/CS108_Firmware_and_Demo_App_Software_Release_Notes.pdf |
| Guía rápida | https://www.convergence.com.hk/cs108-quick-start-guide/ |
| Firmware y herramienta de actualización | https://github.com/cslrfid/CS108-Product-Downloads/tree/master/Firmware |
| Repositorios del fabricante (SDK y demos) | https://github.com/cslrfid |
