# Contrato de la API — borrador v0.1

Base URL: `http://<IP_LAPTOP>:5000/api` · JSON · sin autenticación (demo).
Todos los EPC se normalizan (mayúsculas, sin espacios) en la APK y en la API.

## Salud

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/health` | Estado de la API y de PostgreSQL |

## Catálogos (Windows y APK)

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET / POST | `/products` | Listar / crear productos |
| PUT | `/products/{id}` | Editar producto |
| GET / POST | `/trucks` | Listar / crear camiones |
| PUT | `/trucks/{id}` | Editar camión |
| POST | `/trucks/{id}/available` | Marcar el camión como disponible (regreso de ruta) |

## Etiquetas y captura

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/tags/{epc}` | Consultar una etiqueta: tipo, producto o camión, estado |
| GET | `/tags?kind=&product_id=&status=` | Listar con filtros |
| POST | `/tags/batch` | **Captura por lote de pallets** `{ "product_id": 3, "epcs": ["..."] }` |
| POST | `/tags/truck` | Asociar la etiqueta de parabrisas `{ "truck_id": 2, "epc": "..." }` |
| PUT | `/tags/{epc}` | Corregir (cambiar producto) |
| DELETE | `/tags/{epc}` | Eliminar |
| POST | `/tags/reset-delivered` | **Solo demo** ("Limpiar Estado", Windows): regresa a `captured` los pallets cuya salida más reciente ya está entregada (`completed`/`completed_with_difference` + `delivered_at`), para reutilizar las mismas etiquetas físicas en otro ensayo. No toca los que siguen en ruta o no han salido |

Ejemplo de `GET /tags/{epc}` (pallet):

```json
{
  "found": true,
  "epc": "E28011C0A500007042D701FB",
  "kind": "pallet",
  "folio": "PLT-000123",
  "product": { "id": 2, "name": "Coca Cola 600 ml" },
  "status": "captured",
  "production_date": "2026-10-07T08:42:10-06:00"
}
```

`production_date` es la fecha y hora de la primera captura (`captured_at`) y no cambia.

Respuesta de `POST /tags/batch`: un resultado por EPC (los creados incluyen su folio y fecha).

```json
{
  "product_id": 3,
  "results": [
    { "epc": "E28011C0A500007042D701FB", "result": "created", "folio": "PLT-000123", "production_date": "2026-10-07T08:42:10-06:00" },
    { "epc": "E28011C0A500007042D70200", "result": "already_captured", "product_id": 3 },
    { "epc": "E28011C0A500007042D70201", "result": "already_captured_other_product", "product_id": 5 },
    { "epc": "E28011C0A500007042D70202", "result": "is_truck_tag" },
    { "epc": "E28011C0A500007042D70203", "result": "already_dispatched", "folio": "PLT-000050", "product_id": 2 }
  ]
}
```

`already_dispatched` aparece cuando se intenta recapturar una etiqueta de pallet que ya salió en una salida a ruta (docs/funcional.md, salvaguardas de la sección 5: una etiqueta ya despachada no puede recapturarse).

## Boletas de salida (principalmente Windows)

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/exit-tickets?status=&truck_id=` | Listar boletas con sus líneas |
| POST | `/exit-tickets` | Crear `{ "customer": "...", "truck_id": 2, "lines": [{ "product_id": 3, "pallets": 4 }] }` — el `folio` lo genera el sistema (`BOL-000123`, igual que el de los pallets) |
| PUT | `/exit-tickets/{id}` | Editar (solo si está `active`) |
| POST | `/exit-tickets/{id}/assign-truck` | Asignar o cambiar camión `{ "truck_id": 2 }` |
| POST | `/exit-tickets/{id}/cancel` | Cancelar |

## Salida a ruta (APK)

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/dispatch/lookup/{truck_epc}` | Dado el EPC del parabrisas, devuelve el camión, su estado y sus **boletas activas** con líneas. Si hay un problema, devuelve la alarma (`no_active_tickets`, `truck_not_available`) |
| POST | `/dispatches` | Abrir salida `{ "truck_epc": "...", "ticket_ids": [11, 12] }` → devuelve `dispatch_id` y lo **esperado** por producto |
| POST | `/dispatches/{id}/reads` | Enviar un lote de EPC leídos `{ "epcs": ["...", "..."] }` → devuelve el **palomeo** actualizado |
| GET | `/dispatches/{id}/status` | Palomeo actual: esperado, leído y diferencia por producto, más etiquetas problemáticas |
| POST | `/dispatches/{id}/reset` | Reiniciar las lecturas de la salida |
| POST | `/dispatches/{id}/finish` | **Finalizar lectura**: si cuadra, cierra la salida; si no, genera alarmas y devuelve las diferencias |
| POST | `/dispatches/{id}/authorize` | Autorizar con diferencia `{ "authorized_by": "...", "reason": "..." }` |
| POST | `/dispatches/{id}/cancel` | Cancelar la salida |
| POST | `/dispatches/{id}/deliver` | **"Unidad en planta"** (Windows): solo si está `completed`/`completed_with_difference` y aún no entregada. Marca `delivered_at` y pone el camión `available`. No cambia `status` — ese sigue distinguiendo si salió "Normal" o "Con autorización" |

Respuesta de `POST /dispatches/{id}/reads` y `GET /dispatches/{id}/status`:

```json
{
  "dispatch_id": 7,
  "status": "in_progress",
  "products": [
    { "product_id": 3, "name": "Refresco Cola 600 ml", "expected": 4, "read": 4, "diff": 0 },
    { "product_id": 5, "name": "Refresco Naranja 2 L", "expected": 2, "read": 1, "diff": -1 },
    { "product_id": 8, "name": "Agua 1 L",            "expected": 0, "read": 1, "diff": 1 }
  ],
  "problem_tags": [
    { "epc": "E28011C0A500007042D70300", "result": "unknown" },
    { "epc": "E28011C0A500007042D70301", "result": "already_dispatched" }
  ],
  "total_read": 6
}
```

Respuesta de `POST /dispatches/{id}/finish`:

```json
{ "result": "ok", "dispatch_status": "completed" }
```

```json
{
  "result": "mismatch",
  "dispatch_status": "in_progress",
  "alarms": [
    { "id": 41, "type": "missing", "product_id": 5, "diff": -1 },
    { "id": 42, "type": "excess",  "product_id": 8, "diff": 1 }
  ]
}
```

## Alarmas (Windows)

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/alarms?status=open\|ack\|all` | Listar alarmas (`open` por defecto); incluye `truck_unit_number` (el camión de la salida que la generó, vía `dispatch_id`, o vacío si no aplica) |
| POST | `/alarms/{id}/ack` | Marcar como atendida `{ "acknowledged_by": "..." }` |

## Códigos de error

- `400` datos inválidos · `404` no encontrado · `409` conflicto de regla de negocio (p. ej. camión con salida en proceso, etiqueta ya asociada a otro producto) · `503` base de datos no disponible.
- El cuerpo del error siempre lleva `{ "error": "codigo", "message": "texto legible" }`.
