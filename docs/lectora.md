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

### Sobre el sufijo "-2" ⚠️

Los distribuidores de EE. UU. venden referencias como `CS108-C-2` y `CS108-C2D-2` con banda **902–928 MHz (FCC)**, por lo que el "-2" probablemente indique la **región de frecuencia**. El fabricante define variantes regionales desde 865–868 MHz (Europa/India) hasta 922–928 MHz (Taiwán). **Confirmar en la etiqueta** que el equipo corresponde a la banda de México (902–928 MHz). Esto no impide el desarrollo, pero conviene dejarlo documentado.

### Datos a anotar de la etiqueta del equipo

- [ ] Modelo completo (¿lleva letra de antena V / H / C? ¿lleva "2D"?)
- [ ] Banda de frecuencia / región
- [ ] Número de serie
- [ ] Versión de firmware (visible desde la app demo)

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

## 7. Documentación oficial

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
