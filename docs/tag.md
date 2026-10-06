# Ficha técnica — TAG Beontag CRUISER WINDSHIELD

> Fuente: hoja de datos del fabricante (*Product Datasheet — BEONTAG CRUISER WINDSHIELD*, 3 páginas), proporcionada por el usuario. Número de producto **3004076**.

## 1. Resumen

Etiqueta RFID **UHF** adhesiva, **antifraude (tamper-evident) y no transferible**, diseñada para **parabrisas de automóvil**. Se pega por dentro del cristal y, si se intenta despegar, queda inutilizable. Es compatible con la CS108-2: ambas trabajan con **UHF EPC Gen2**.

En este proyecto se usará para dos cosas: identificar al **camión** (etiqueta en el parabrisas, que es su uso natural) y identificar los **pallets** (uso fuera del diseño original, ver sección 5).

## 2. Compatibilidad con la lectora CS108-2

| Requisito | TAG | Lectora CS108-2 | ¿Compatible? |
|---|---|---|---|
| Tecnología | UHF, EPCglobal Gen2v2 | UHF EPC Class 1 Gen 2 / ISO 18000-6C | Sí |
| Banda | Global 865–928 MHz | 902–928 MHz (FCC), a confirmar en la etiqueta del equipo | Sí (la banda de México cae dentro del rango del TAG) |

## 3. Especificaciones

| Característica | Valor |
|---|---|
| Fabricante / modelo | Beontag — CRUISER WINDSHIELD (producto 3004076) |
| Tipo de dispositivo | UHF RFID, EPCglobal Gen2v2 |
| Frecuencia de operación | Global 865–928 MHz |
| Chip (IC) | Impinj M780 |
| Memoria | EPC 496 bit · User 128 bit · TID 96 bit |
| Contenido de memoria EPC | **EPC único y aleatorio de 96 bits en cada etiqueta** |
| Alcance de lectura | ETSI: hasta 14 m · **FCC: hasta 12 m** (valores teóricos, sobre cristal) |
| Superficie aplicable | **Vidrio** (única superficie que declara el fabricante) |
| Dimensiones de la etiqueta | 92 × 26 × 0,2 mm |
| Peso | 1 g |
| Temperatura de operación / ambiente | −35 °C a +85 °C |
| Almacenamiento | 1 año a +20 °C y 50 % HR (vida útil del adhesivo) |
| Presentación | 1,500 piezas por rollo, con perforación entre etiquetas |

### Construcción
- **Lado del parabrisas (reverso):** PET con adhesivo de alta adherencia al vidrio.
- **Lado del conductor (frente):** PET imprimible (inyección de tinta y transferencia térmica; se recomienda cinta de resina).

## 4. Los dos "números" de una etiqueta: EPC y número impreso

Una etiqueta RFID puede tener dos identificadores distintos. No son lo mismo:

| | **EPC** | **Número impreso** |
|---|---|---|
| ¿Qué es? | Código digital guardado dentro del chip | Texto, número o código de barras impreso sobre la etiqueta |
| ¿Se ve? | No, solo lo lee la lectora | Sí, lo lee una persona |
| ¿Lo trae tu etiqueta? | **Sí, siempre** (único, de 96 bits, 24 caracteres hexadecimales) | **No**: tus etiquetas están en blanco |
| ¿Para qué sirve? | Es el identificador real del sistema | Solo es una comodidad visual |

La hoja de datos menciona el número impreso como una **opción de personalización** de fábrica (impresión de datos variables como código de barras, texto legible o número de serie en el lado del conductor). Como tus etiquetas vienen en blanco, **no hay ningún problema**: el sistema funciona 100 % con el EPC.

### ¿Hace falta un código visible?
**No es necesario** en el diseño actual: cada etiqueta de pallet se asocia a un **producto** (no a un pallet único) y la de parabrisas a un **camión**. El sistema trabaja solo con el EPC.

Si quisieras un código visible para tus ensayos (por ejemplo, para distinguir las etiquetas de prueba), puedes escribirlo en el **frente** de la etiqueta con marcador permanente (el PET es imprimible; prueba primero con una etiqueta y no toques la zona del chip). Sería solo una ayuda visual: no se guarda en el sistema.

Si más adelante quisieras etiquetas con número impreso de fábrica, se pueden pedir o imprimir con transferencia térmica y cinta de resina; para esta demo no es necesario.

## 5. Uso en el proyecto: camión y pallets

### Camión (parabrisas) — uso previsto
Es el uso para el que está diseñada la etiqueta: pegada en el interior del cristal, con hasta ~12 m de alcance teórico (banda FCC).

### Pallets — uso fuera de diseño ⚠️
El fabricante **solo declara vidrio como superficie aplicable**. Pegada sobre madera, plástico, cartón o film estirable el rendimiento puede bajar (menor alcance, lecturas intermitentes) y el adhesivo, pensado para vidrio, puede comportarse distinto. Sobre **metal** el rendimiento cae mucho (el fabricante pide que la antena no toque metal).

Por eso la **Fase 1 del roadmap incluye una prueba de superficies**: pegar unas pocas etiquetas sobre los materiales reales de tus pallets (madera, plástico, cartón, film) y medir a qué distancia las lee la CS108-2. Si alguna superficie funciona mal, alternativas para la demo: pegar la etiqueta sobre una tarjeta de cartón o plástico que viaje con el pallet, o separar la etiqueta unos milímetros del pallet con un espaciador de cartón.

### Recomendaciones generales
1. **Es no transferible: se destruye al despegarla.** Se pega una sola vez en su sitio definitivo. Con más de 1,000 etiquetas disponibles no es problema perder alguna en pruebas.
2. **Mantenerlas lejos del metal** (laptop, mesas o racks metálicos, flejes).
3. **Separar las etiquetas entre sí** al probar: varias etiquetas muy juntas o apiladas pueden interferirse.
4. **No tocar la zona del chip (IC) ni doblar la etiqueta** por debajo del diámetro mínimo del fabricante (el valor exacto no se leyó con claridad en el PDF; ver página 2 de la hoja de datos).
5. Condiciones ideales de instalación: +20 °C, 50 % de humedad, superficie limpia y seca; el adhesivo alcanza su mejor adherencia a las 24 horas.
6. **No vamos a escribir en la memoria del TAG.** Toda la información vive en PostgreSQL; el TAG solo aporta el EPC. Así se evitan problemas con etiquetas bloqueadas.

## 6. Etiquetas para la prueba

Cantidad máxima de la prueba: **20–30 etiquetas** (hay más de 1,000 disponibles). Reparto propuesto:

| Uso | Cantidad | Notas |
|---|---|---|
| Camiones (parabrisas) | 2–3 | En camiones **reales**, por dentro del parabrisas |
| Pallets | 20–25 | Pallets de **base de madera** con refrescos en PET; algunos con emplaye |
| Reserva / pruebas de ubicación | 3–5 | Para probar posiciones en el pallet |

## 7. Datos confirmados y pendientes

### Confirmado
- Pallets: base de madera, producto refrescos (todos en plástico/PET), algunos con emplaye.
- Camiones: reales, con la etiqueta en el parabrisas.
- Las etiquetas están en blanco (sin número impreso). La identificación visual no es necesaria: el sistema usa el EPC y asocia cada etiqueta a un producto o a un camión.
- Lectura real validada con la app demo de CSL: EPC `E28011C0A500007042D701FB` (96 bits, 24 hex), contraseñas Access y Kill en `00000000`. **No usar el botón "Write" de esa app.**

### Riesgos físicos propios de este producto
- **Refrescos en PET = líquido:** el agua absorbe la señal UHF. Las etiquetas de pallets cubiertos por otros pallets (fondo o centro del camión) pueden no leerse.
- **Emplaye:** el plástico estirable no bloquea la señal; la etiqueta puede ir bajo o sobre el emplaye, pero hay que probar la mejor posición.
- **Madera:** la base de madera es aceptable para UHF, pero sigue sin ser vidrio (única superficie que declara el fabricante).

### Pendientes
- [ ] Posición estándar de la etiqueta en el pallet (cara exterior y alta, del lado de las puertas del camión; por probar).
- [ ] Porcentaje de lectura en un camión real cargado.
- [ ] Diámetro mínimo de doblez (página 2 del PDF).
