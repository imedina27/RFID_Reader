# CLAUDE.md — Instrucciones del proyecto RFID_Reader

Este archivo da contexto a Claude (y a cualquier asistente de código) cada vez que se trabaja en este proyecto. **Léelo completo antes de hacer cambios.**

## 1. Qué es este proyecto

Sistema de **demo para una presentación** (no es producción) que verifica con RFID que un camión de reparto sale con los pallets que pide su(s) boleta(s) de salida, ni más ni menos, y que permite asociar etiquetas a productos y camiones.

Tres piezas:

1. **Lectora RFID CSL CS108-2** (UHF EPC Gen2, Bluetooth LE, **sin WiFi**).
2. **APK Android** (Kotlin) en un Samsung S24 Ultra (Android 16): modos *Salida a Ruta* y *Captura de Tags*.
3. **App Windows** (Python): API Flask + interfaz de escritorio + PostgreSQL.

```
Etiqueta ~~UHF~~ CS108-2 ──BLE──► APK (celular) ──WiFi/HTTP──► Laptop (Flask + PostgreSQL + app Windows)
```

## 2. Orden de lectura obligatorio

Antes de programar, lee en este orden:

1. `ROADMAP.md` — estado, decisiones, fases y pendientes.
2. `docs/funcional.md` — procedimientos, reglas, alarmas y pantallas.
3. `docs/modelo_datos.md` — esquema PostgreSQL.
4. `docs/api.md` — contrato de la API.
5. `docs/tag.md`, `docs/lectora.md`, `docs/apk.md` — hardware, etiqueta y SDK.

**Si algo del código contradice estos documentos, se detiene el trabajo y se pregunta al usuario** (o se actualiza el documento si el usuario aprueba el cambio). No inventes reglas de negocio.

## 3. Stack y entorno

| Tema | Decisión |
|---|---|
| Lenguaje Windows | Python **3.14** (puede bajar a 3.13 si faltan paquetes); entorno con **pipenv** (`Pipfile` ya existe) |
| API | Flask, `host="0.0.0.0"`, puerto **5000**, corre en un hilo secundario dentro de la app de escritorio |
| Interfaz Windows | PyQt6 o PySide6 (recomendado sobre Tkinter; confirmar compatibilidad con Python 3.14 en la Fase 3) |
| Base de datos | **PostgreSQL** local, `psycopg` v3; conexión desde `windows_app/.env` (`DATABASE_URL`) |
| APK | Kotlin, `minSdk 26`, `targetSdk 36`, JDK 17, SDK `csl-rfid-android-sdk` (JitPack) |
| Red | Hotspot de la laptop (IP habitual `192.168.137.1`) |

Estructura prevista (ver `ROADMAP.md`, sección 4.3):

```
RFID_Reader/
├── ROADMAP.md  README.md  CHANGELOG.md  CLAUDE.md
├── Pipfile / Pipfile.lock
├── docs/
├── windows_app/   (main.py, api.py, db.py, schema.sql, ui/, tests/)
└── android_app/   (proyecto Kotlin)
```

## 4. Convenciones de código

- **Idioma:** la interfaz (pantallas, mensajes al usuario, alarmas) va en **español**. El código, nombres de tablas, columnas, endpoints, variables y comentarios técnicos van en **inglés**. La documentación (`docs/`, ROADMAP, README, CHANGELOG) va en **español**.
- **SQL:** siempre **consultas parametrizadas** (`%s` con `psycopg`). Nunca concatenar texto en SQL, aunque sea una demo.
- **EPC:** se normaliza siempre (**mayúsculas, sin espacios**) tanto en la APK como en la API, antes de guardar o consultar. Columna `VARCHAR(48)`.
- **Zona horaria:** `TIMESTAMPTZ` en la base; mostrar en hora local (America/Mexico_City).
- **Secretos:** `windows_app/.env` **nunca se versiona** (debe estar en `.gitignore`). Se mantiene un `.env.example` sin contraseñas.
- **Errores de la API:** cuerpo `{ "error": "codigo", "message": "texto legible" }` con los códigos de `docs/api.md`.
- **Transacciones:** el cierre de una salida (boletas, etiquetas y camión) se hace en **una sola transacción**.
- **Pruebas:** la verificación de la salida (esperado contra leído por producto) debe ser una **función aislada con pruebas automáticas** (`pytest`). Es la regla más importante de la demo.
- Código simple y legible: es una demo. No agregar capas, patrones ni dependencias que no hagan falta.

## 5. Reglas de negocio que no se pueden romper

(Detalle completo en `docs/funcional.md`.)

1. **Nunca se escribe en la memoria de las etiquetas.** Solo se lee el EPC. (La app demo de CSL tiene un botón *Write*: no usarlo.)
2. Cada etiqueta se asocia a **un producto** (pallet) o a **un camión** (parabrisas); no identifica un pallet único. Es de un solo uso.
3. Estados de etiqueta de pallet: `captured` → `dispatched`. Una etiqueta `dispatched` **no se cuenta de nuevo**.
4. **Folio del pallet** consecutivo generado por el sistema (`PLT-000123`) y **fecha de salida de producción = fecha de la primera captura**; esa fecha **no cambia** nunca.
5. Boleta de salida: folio, cliente y líneas (producto + pallets completos). Una o varias boletas por camión. Sin campos extra.
6. La salida es **correcta solo si, por cada producto, leído = esperado**, y no hay etiquetas desconocidas ni ya despachadas. Con varias boletas se compara contra el **total por producto**.
7. Un camión: una sola salida en proceso. Un camión `en ruta` no puede abrir otra salida.
8. Si no cuadra: alarma en la APK y en Windows; el operador puede **repetir la lectura** o **autorizar con motivo** (queda registrado quién y por qué).
9. Lectura del parabrisas: **un solo EPC** (el de mayor RSSI). Las etiquetas de camión se **ignoran** durante el escaneo de pallets.
10. Tipos de alarma: `missing`, `excess`, `unknown_tag`, `already_dispatched`, `no_active_tickets`, `truck_not_available`.

## 6. Mantenimiento de la documentación — OBLIGATORIO

**En cada sesión de trabajo que cambie algo del proyecto (código, decisiones, alcance, esquema, API, pendientes), Claude debe actualizar SIEMPRE estos tres archivos antes de terminar:**

### 6.1 `ROADMAP.md`
- Marcar con `[x]` las tareas completadas y agregar las nuevas.
- Mover los pendientes resueltos a "Ya resuelto" y añadir los nuevos pendientes.
- Actualizar la línea **Estado** (versión y resumen) del encabezado.
- Actualizar riesgos, decisiones y "Próximos pasos inmediatos" si cambiaron.

### 6.2 `README.md`
- Mantener al día el **estado del proyecto**, cómo instalar/ejecutar, estructura de carpetas y el índice de documentación.
- Si cambia un comando, una dependencia, un puerto o la forma de arrancar, reflejarlo aquí.

### 6.3 `CHANGELOG.md`
- Formato **Keep a Changelog** en español, con versiones tipo `MAJOR.MINOR.PATCH` (mientras sea demo: `0.x.y`).
- Cada cambio va primero en la sección `## [Sin publicar]` y, al cerrar una sesión o hito, se pasa a una versión nueva con fecha `AAAA-MM-DD`.
- Categorías: **Añadido**, **Cambiado**, **Corregido**, **Eliminado**, **Documentación**.
- Cada entrada: una línea clara de qué cambió y por qué (mencionar archivos o módulos cuando ayude).
- Subir la versión: `PATCH` para correcciones, `MINOR` para funcionalidad nueva o fase completada, `MAJOR` solo si el usuario lo decide.
- La versión del `CHANGELOG.md` y la del encabezado del `ROADMAP.md` deben coincidir.

### 6.4 Otras reglas de documentación
- Si cambia el modelo de datos, actualizar también `docs/modelo_datos.md` (y `schema.sql`); si cambia un endpoint, `docs/api.md`; si cambia un procedimiento o regla, `docs/funcional.md`.
- Al terminar, **resumir al usuario** qué se cambió y qué archivos de documentación se actualizaron.

## 7. Cosas que NO se deben hacer

- No escribir en etiquetas RFID ni proponer hacerlo.
- No exponer PostgreSQL a la red (solo el puerto 5000 de la API se abre en el firewall).
- No subir `.env`, contraseñas ni datos reales de clientes.
- No cambiar `targetSdk` a 37 o más sin añadir el permiso `ACCESS_LOCAL_NETWORK` (ver `docs/apk.md`).
- No dar por hecho nombres de clases del SDK de CSL: se verifican en el demo oficial (`cslrfid/cs710s-android`, módulo `cs710aquickstart`).
- No agregar funcionalidad fuera del alcance de `docs/funcional.md` sin confirmarlo con el usuario.

## 8. Datos de la demo

- **Productos:** Coca Cola 2 L, Coca Cola 600 ml, Sprite 600 ml, Agua Cristal 600 ml, Bevi 355 ml.
- **Etiquetas:** Beontag CRUISER WINDSHIELD, máximo 20–30 en la prueba. EPC de ejemplo real: `E28011C0A500007042D701FB`.
- **Pallets:** base de madera, refrescos en PET, algunos con emplaye. **Camiones:** reales, etiqueta en el parabrisas.
- **Riesgo físico principal:** el líquido en PET atenúa la señal UHF; puede haber falsos faltantes y excedentes. Ver `ROADMAP.md`, sección de riesgos.

## 9. Cómo trabajar con el usuario (Ismael)

- Hablar en **español**, tono cercano y claro. Respuestas cortas y directas; explicar lo técnico en palabras simples.
- Ante una duda que cambie el diseño, **preguntar antes de programar**.
- Al terminar una tarea, decir qué se hizo, qué quedó pendiente y cuál es el siguiente paso sugerido.
