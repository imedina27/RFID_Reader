# Especificación funcional — Salida a ruta y captura de tags

> Versión 0.1 — acordada con el usuario el 2026-10-06. Los **diseños de pantalla** quedan por definir; aquí se fijan los procedimientos, reglas, estados y la lista de pantallas.

## 1. Alcance

El sistema tiene dos partes:

1. **Salida a ruta:** verificar que el camión sale con los pallets que pide su(s) boleta(s) de salida, ni más ni menos.
2. **Captura de tags:** asociar cada etiqueta a un producto (pallets) o a un camión (parabrisas). Es la misma operación para los pallets nuevos que salen de la línea de producción.

Dos aplicaciones:

- **APK Android** (S24 Ultra + lectora CS108-2): se opera en campo, en andén y en línea de producción.
- **App Windows** (Python + PostgreSQL): administración, boletas, monitoreo y alarmas.

## 2. Conceptos

| Concepto | Descripción |
| --- | --- |
| **Producto** | Un tipo de producto (p. ej. "Refresco Cola 600 ml PET"). Catálogo que se mantiene en Windows. |
| **Etiqueta de pallet** | Etiqueta Beontag pegada en un pallet. Se asocia a **un producto**. Es de **un solo uso** (una etiqueta nueva por cada pallet producido). Datos que guarda el sistema: **Folio** (consecutivo único generado por el sistema al capturar, p. ej. `PLT-000123`), **Producto** y **Fecha de salida de producción** (fecha y hora de la **primera captura**, es decir, cuando el pallet salió de la línea). Esa fecha no cambia aunque la etiqueta se lea o se corrija después. |
| **Etiqueta de camión** | Etiqueta Beontag en el parabrisas. Se asocia a **un camión**. |
| **Camión** | Unidad de reparto, con número económico y placa. Estados: `disponible` y `en ruta`. |
| **Boleta de salida** | Documento con folio, cliente y una o varias líneas **(producto, cantidad de pallets completos)**. Se asigna a un camión. Estados: `activa`, `despachada`, `cancelada`. |
| **Salida a ruta** | El proceso de lectura de un camión contra una o varias de sus boletas activas. |
| **Alarma** | Aviso de una diferencia o irregularidad, visible en la APK y en Windows. |

### Estado de cada etiqueta de pallet

- `capturada`: asociada a un producto y disponible en almacén.
- `despachada`: ya salió en una salida a ruta correcta. **No puede volver a contarse** en otra salida.

## 3. Procedimiento A — Salida a ruta (APK)

| Paso | Qué hace el usuario | Qué hace el sistema |
| --- | --- | --- |
| 1 | Se acerca al frente del camión y elige **"Salida a Ruta"** en el menú | Pasa a la pantalla "Esperando parabrisas" |
| 2 | Acerca la lectora al parabrisas (gatillo) | Lee **una sola etiqueta** (la de mayor señal) y consulta el camión |
| 3 | — | Muestra las **boletas activas** del camión. Si no tiene, alarma `camión sin boletas activas`. Si el camión ya está `en ruta`, alarma `camión no disponible` |
| 4 | Selecciona **una o varias** boletas que están saliendo y confirma | Calcula lo **esperado**: suma de pallets por producto de las boletas elegidas. Abre la salida |
| 5 | Mantiene **presionado el gatillo** de la lectora y barre los pallets del camión (puede hacer varias pasadas) | Acumula etiquetas únicas, las envía a Windows en lotes y las **palomea** contra lo esperado, por producto |
| 6 | Presiona **"Finalizar lectura"** | Compara lo leído contra lo esperado y decide el resultado |
| 7a | Si **cuadra exacto** | Boletas → `despachada`; etiquetas leídas → `despachada`; camión → `en ruta`. Muestra "Salida correcta" |
| 7b | Si **no cuadra** | Muestra un aviso en la APK con dos opciones: **Aceptar** o **Autorizar salida**; registra la alarma (`faltante`/`excedente`) en Windows |

### Pantalla de palomeo (paso 5)

Una línea por producto esperado: *producto — esperados / leídos*, con marca de color: faltan (amarillo), completo (verde), sobran (rojo). Productos leídos que no están en las boletas se muestran como "no solicitado" en rojo. Contador total y botón de reinicio de lecturas. Al tocar un producto se ven los **folios** de los pallets leídos y su fecha de salida de producción.

### Resultado en caso de diferencia (decisión 2026-10-06)

Al presionar **Finalizar lectura** sin cuadrar, aparece un aviso con dos botones: **Aceptar** y **Autorizar salida**. El mensaje y lo que hace **Aceptar** dependen del caso:

- **Sobra** producto (en cualquier intento), o **falta** producto y ya se había avisado antes en esta misma salida (segundo intento o más): el aviso dice *"Unidad con sobrantes/Faltantes. Regresar a zona de carga o revisar"*. **Aceptar** manda la unidad de regreso a la zona de carga: se **cancela** la salida sin tocar boletas, etiquetas ni el camión — quedan `activa`/`capturada`/`disponible` otra vez, como si el intento no hubiera pasado. La próxima vez que el camión se presente se trata como si fuera la primera vez.
- Solo **falta** producto y es la primera vez que no cuadra en esta salida: el aviso dice *"Producto Faltante, Revisar Unidad"*. **Aceptar** no cancela nada, solo cierra el aviso y deja seguir leyendo (o volver a intentar Finalizar).
- **Autorizar salida** (en los dos avisos): pide un texto con el motivo y el nombre de quien autoriza. La salida se cierra como `completada con diferencia`; boletas, etiquetas y camión cambian de estado igual que en una salida correcta, y **queda registrado** el motivo, la diferencia y la alarma.

En todos los casos de diferencia la alarma (`faltante` o `excedente`, con el producto y la diferencia) queda guardada en Windows, incluso si la unidad regresa a la zona de carga.

## 4. Reglas de validación y alarmas

| Situación | Tipo de alarma | ¿Bloquea el cierre? |
| --- | --- | --- |
| Se leyó menos de un producto que lo pedido | `faltante` | Sí |
| Se leyó más de un producto que lo pedido, o un producto que no está en las boletas | `excedente` | Sí |
| Etiqueta leída que no está registrada | `etiqueta no registrada` | Sí |
| Etiqueta de un pallet ya `despachada` | `pallet ya despachado` | Sí |
| El camión no tiene boletas activas | `camión sin boletas activas` | Sí (no se puede abrir; aviso: "Esta unidad no tiene boletas asignadas.") |
| El camión ya está `en ruta` | `camión no disponible` | Sí (no se puede abrir) |
| Se leen etiquetas de otro camión durante el escaneo de pallets | — | No: se ignoran y solo se registran en el historial |

- "Bloquea" significa que no se cierra como correcta; se resuelve **aceptando** (la unidad regresa a la zona de carga, salida cancelada) o con **autorización de salida**.
- Con varias boletas, la verificación es por **totales por producto**. Como la etiqueta se asocia al producto, no se sabe qué pallet corresponde a qué cliente; es una limitación aceptada en la demo.
- Las alarmas de etiquetas (no registrada, ya despachada) se avisan en cuanto se leen; faltante y excedente se confirman al finalizar (el excedente también se resalta en vivo en el palomeo).
- Una misma alarma no se repite por cada lectura: se registra una vez por salida y por etiqueta/producto.

## 5. Procedimiento B — Captura de tags (APK)

| Paso | Qué hace el usuario | Qué hace el sistema |
| --- | --- | --- |
| 1 | Elige **"Captura de Tags"** | Queda listo para leer (captura de pallets) |
| 2 | Elige el **producto** una sola vez (se queda seleccionado: **modo lote**) | Queda listo para leer |
| 3 | Lee la etiqueta (gatillo) | Muestra las etiquetas nuevas leídas. Las ya capturadas se indican como "ya capturada" y no se duplican |
| 4 | Presiona **Aceptar / "Guardar N etiquetas como \<producto\>"** | Guarda cada etiqueta asociada al producto con estado `capturada`, le asigna su **folio consecutivo** y registra la **fecha de salida de producción** (fecha y hora actuales) |
| 5 | Sigue con el siguiente pallet, o cambia de producto | — |

> **Fuera de alcance de la APK de pruebas:** asociar la etiqueta del parabrisas a un camión (captura "modo Camión"). Los camiones de la demo ya llegan con su etiqueta de parabrisas asociada por carga directa a la base de datos (`windows_app/import_trucks.py`), así que la APK no necesita esta pantalla.

### Salvaguardas

- Si en una sola lectura aparecen **varias etiquetas nuevas** (por ejemplo, 5 a la vez), se pide confirmación antes de asignarlas todas al producto, para evitar capturar etiquetas ajenas por error.
- Una etiqueta de **camión** solo puede asociarse a un camión, y cada camión tiene **una** etiqueta de parabrisas (esta asociación se carga directo en la base de datos, no desde la APK — ver nota arriba).
- Una etiqueta ya `despachada` no puede recapturarse; muestra un aviso.

### Pallets de la línea de producción

Es **la misma captura de tags**. Cada pallet nuevo recibe su etiqueta, se lee en modo lote con el producto de la corrida de producción y queda `capturada`.

### Captura desde Windows

La app Windows muestra lo capturado desde la APK casi en tiempo real y permite **corregir** (cambiar el producto, eliminar) y **capturar a mano** pegando un EPC.

## 6. Pantallas (lista; el diseño se define después)

### App Windows

| Pantalla | Para qué |
| --- | --- |
| **Tablero** | Estado de PostgreSQL y de la API, IP/puerto, alarmas abiertas, camiones en ruta, salidas del día. **Panel en vivo**: al escanear el parabrisas aparece el camión (ilustración, no a escala con los pallets reales); al confirmar boletas, aparecen en "Boletas asignadas" y empiezan los círculos de pulso sobre el camión mientras se escanea; al terminar la salida se pinta un semáforo (verde correcta, amarillo con autorización, rojo cancelada) y todo se queda fijo 5 s antes de limpiarse |
| **Productos** | Catálogo (clave, nombre, presentación) |
| **Camiones** | Alta, edición y etiqueta del parabrisas. Sin botón "Marcar disponible": no hace falta para esta demo (decisión del usuario, 2026-10-07) |
| **Pallets** | Lista de las etiquetas de pallet ya capturadas (las que salieron de línea de producción), más reciente arriba, con **fecha, EPC, folio, producto y estado**; se refresca sola para que una captura nueva desde la APK aparezca al momento. Botones **Agregar** (captura manual: EPC + producto), **Editar** (corrige el producto) y **"Limpiar Estado"** (morado, separado de los demás, siempre activo — solo para la demo: regresa a `capturada` los pallets de una salida ya **entregada**, para reutilizar las mismas etiquetas físicas en otro ensayo; pide confirmación antes de aplicar). Las etiquetas de camión no aparecen aquí (se administran en Camiones) |
| **Boletas de salida** | Alta (folio **automático**, cliente en texto libre, líneas producto-pallets), **asignar camión** (solo camiones `disponibles`; una vez asignado no se puede quitar), cancelar (con confirmación), ver estado |
| **Salidas a ruta** | Historial de salidas (más reciente arriba, se refresca sola), con filtros por Estado, Camión, Cliente y Folio de boleta. Columnas Camión, Folio(s), Cliente(s), **Estado** (No ha salido/En ruta/Entregado/Cancelada) y **Tipo de salida** (Normal/Con autorización). Botón **"Ver detalle"**: palomeo completo (monitor en vivo si sigue en proceso, foto fija si ya terminó). Botón **"Unidad en planta"**: solo si "En ruta"; marca la salida como entregada y libera el camión (`POST /dispatches/{id}/deliver`) |
| **Alarmas** | Lista (filtro Abiertas/Atendidas/Todas, más reciente arriba, se refresca sola) con **fecha, tipo, camión, mensaje, atendida por y estado**. Aviso visual (fila resaltada en amarillo/rojo si está abierta) y sonoro (beep al detectar una alarma nueva). Botón **Marcar atendida** (pide el nombre de quien atiende). Solo cubre los 4 tipos que se guardan en la base (`faltante`, `excedente`, `etiqueta no registrada`, `pallet ya despachado`); "camión sin boletas activas" y "camión no disponible" se quedan como avisos solo de la APK, sin guardarse (decisión del usuario, 2026-10-07) |

### APK Android

| Pantalla | Para qué |
| --- | --- |
| **Menú principal** | "Salida a Ruta", "Captura de Tags", "Ajustes" y estado de la lectora |
| **Conexión con la lectora** | Buscar y conectar la CS108 por Bluetooth |
| **Salida a Ruta** | Esperando parabrisas → selección de boletas → escaneo y palomeo → resultado (correcta / alarma con Repetir o Autorizar) |
| **Captura de Tags** | Producto (modo lote) → lectura y confirmación |
| **Ajustes** | IP y puerto del servidor, potencia de la antena, modo simulado |

## 7. Datos mínimos de la demo

| Elemento | Propuesta |
| --- | --- |
| Productos | 5, confirmados: **Coca Cola 2 L**, **Coca Cola 600 ml**, **Sprite 600 ml**, **Agua Cristal 600 ml**, **Bevi 355 ml** |
| Camiones | 2 o 3 reales, con etiqueta en el parabrisas |
| Pallets | Hasta 20–25 etiquetas capturadas en total (máximo de la prueba: 30) |
| Boletas | 3 o 4 activas, una con dos boletas sobre el mismo camión |

## 8. Supuestos y pendientes

### Supuestos (confirmar o corregir)

- Cada pallet lleva una etiqueta nueva de un solo uso.
- Un pallet contiene un solo producto.
- Un solo operador y una sola lectora a la vez.
- La autorización con motivo se hace en la propia APK (texto con motivo y nombre); no se exige contraseña de supervisor en la demo.

### Confirmado

- Productos de la demo (sección 7).
- Pallet: folio generado por el sistema, producto y fecha de salida de producción (= primera captura).
- Boleta: folio **generado por el sistema** (consecutivo `BOL-000123`, igual que el de los pallets), cliente (texto libre, sin catálogo de clientes) y líneas (producto + pallets); el sistema añade fecha de creación y camión asignado. Sin campos extra.

### Pendientes

- [ ] Diseño de pantallas de APK y Windows.
- [ ] Prueba de lectura en un camión real cargado (porcentaje de pallets leídos) y definición de dónde se pega la etiqueta en el pallet.
