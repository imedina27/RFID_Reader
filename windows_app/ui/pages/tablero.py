"""Pantalla Tablero: estado de PostgreSQL, IP/puerto de la API, resumen del
día y el panel en vivo de la salida que se está escaneando ahora mismo
(docs/funcional.md, sección 6). Consulta PostgreSQL directamente cada 2 s
(ROADMAP.md, sección 4.1), sin pasar por la API HTTP.

Panel en vivo — secuencia (decisión del usuario, 2026-10-07):
1. Se escanea el parabrisas (la APK consulta `GET /dispatch/lookup`, que
   corre en el mismo proceso): el camión aparece, con sus datos.
2. Se confirman las boletas (`POST /dispatches`, ya hay salida en la base):
   aparecen en "Boletas Asignadas" y empiezan los círculos de pulso.
3. Termina la salida (deja de estar `in_progress`): se apagan los
   círculos y se pinta el semáforo según cómo salió.
4. Todo se queda fijo 5 s y el panel vuelve al estado vacío.
"""

import socket
import time

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtWidgets import (
    QHBoxLayout,
    QLabel,
    QListWidget,
    QVBoxLayout,
    QWidget,
)

import db
import live_state
from ui.widgets.camion_vivo import CamionVivoWidget
from ui.widgets.semaforo import SemaforoWidget

API_PORT = 5000

_SEGUNDOS_RESULTADO_VISIBLE = 5.0
_CLAVE_SEMAFORO = {
    "completed": "ok",
    "completed_with_difference": "warn",
    "cancelled": "fail",
}


def _ip_local() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))  # no se envía nada; solo elige la interfaz de salida
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


class TableroPage(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self._dispatch_id: int | None = None
        self._terminado_en: float | None = None
        self._sin_boletas_activo = False

        layout = QVBoxLayout(self)

        header = QLabel("Tablero")
        header.setObjectName("section_header")
        layout.addWidget(header)

        direccion = QLabel(f"API: http://{_ip_local()}:{API_PORT}")
        layout.addWidget(direccion)

        panel_vivo = QWidget()
        panel_vivo.setLayout(self._armar_panel_vivo())
        layout.addWidget(panel_vivo, 1)

        # Barra "Tablero" al fondo, con el resumen de siempre (como en el
        # boceto: una franja horizontal, no una lista vertical).
        barra_resumen = QHBoxLayout()
        self._valores: dict[str, QLabel] = {}
        filas = [
            ("db", "PostgreSQL"),
            ("alarmas", "Alarmas abiertas"),
            ("en_ruta", "Camiones en ruta"),
            ("salidas_hoy", "Salidas del día"),
        ]
        for clave, etiqueta in filas:
            bloque = QVBoxLayout()
            nombre = QLabel(etiqueta)
            nombre.setObjectName("field_label")
            valor = QLabel("—")
            valor.setObjectName("field_value")
            bloque.addWidget(nombre)
            bloque.addWidget(valor)
            barra_resumen.addLayout(bloque)
            barra_resumen.addStretch(1)
            self._valores[clave] = valor
        layout.addLayout(barra_resumen)

        self._timer = QTimer(self)
        self._timer.setInterval(2000)
        self._timer.timeout.connect(self.refrescar)
        self._timer.start()
        self.refrescar()

    # ----------------------------------------------------------
    # Construcción del panel en vivo
    # ----------------------------------------------------------

    def _armar_panel_vivo(self) -> QHBoxLayout:
        # El camión se centra de verdad (no solo "a ojo" dentro de su propia
        # columna) dándole a la zona izquierda (boletas + datos) y a la
        # derecha (semáforo) el mismo stretch — así pesan igual aunque su
        # contenido no mida lo mismo, y lo que sobra queda como espacio en
        # blanco dentro de cada zona, no recortando su ancho.
        panel = QHBoxLayout()

        zona_izquierda = QHBoxLayout()

        columna_boletas = QVBoxLayout()
        titulo_boletas = QLabel("Boletas asignadas")
        titulo_boletas.setObjectName("field_label")
        columna_boletas.addWidget(titulo_boletas)
        self.lista_boletas = QListWidget()
        columna_boletas.addWidget(self.lista_boletas, 1)
        zona_izquierda.addLayout(columna_boletas, 1)

        columna_datos = QVBoxLayout()
        titulo_datos = QLabel("Datos de la unidad")
        titulo_datos.setObjectName("field_label")
        columna_datos.addWidget(titulo_datos)
        self.label_unidad = QLabel("Unidad: —")
        self.label_unidad.setObjectName("field_value")
        self.label_placa = QLabel("Placa: —")
        self.label_placa.setObjectName("field_value")
        columna_datos.addWidget(self.label_unidad)
        columna_datos.addWidget(self.label_placa)
        columna_datos.addStretch(1)
        zona_izquierda.addLayout(columna_datos, 1)

        panel.addLayout(zona_izquierda, 1)

        self.camion_widget = CamionVivoWidget()
        panel.addWidget(self.camion_widget, 2)

        columna_semaforo = QVBoxLayout()
        self.semaforo = SemaforoWidget()
        columna_semaforo.addWidget(self.semaforo, 0, Qt.AlignmentFlag.AlignHCenter)
        titulo_semaforo = QLabel("Semáforo")
        titulo_semaforo.setObjectName("field_label")
        titulo_semaforo.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        columna_semaforo.addWidget(titulo_semaforo)
        columna_semaforo.addStretch(1)
        panel.addLayout(columna_semaforo, 1)

        return panel

    # ----------------------------------------------------------
    # Refresco
    # ----------------------------------------------------------

    def refrescar(self) -> None:
        try:
            with db.get_connection() as conn:
                alarmas = conn.execute(
                    "SELECT count(*) AS n FROM alarms WHERE acknowledged_at IS NULL"
                ).fetchone()["n"]
                en_ruta = conn.execute(
                    "SELECT count(*) AS n FROM trucks WHERE status = 'en_route'"
                ).fetchone()["n"]
                salidas_hoy = conn.execute(
                    "SELECT count(*) AS n FROM dispatches WHERE started_at::date = current_date"
                ).fetchone()["n"]
                self._refrescar_panel_vivo(conn)
        except Exception:
            self._set_valor("db", "No disponible", "fail")
            return

        self._set_valor("db", "Conectado", "ok")
        self._set_valor("alarmas", str(alarmas), "warn" if alarmas else "ok")
        self._set_valor("en_ruta", str(en_ruta), None)
        self._set_valor("salidas_hoy", str(salidas_hoy), None)

    def _refrescar_panel_vivo(self, conn) -> None:
        if self._terminado_en is not None:
            if time.monotonic() - self._terminado_en >= _SEGUNDOS_RESULTADO_VISIBLE:
                self._reiniciar_panel()
            return  # congelado mostrando el resultado durante los 5 s

        en_proceso = conn.execute(
            "SELECT id, truck_id FROM dispatches WHERE status = 'in_progress' "
            "ORDER BY started_at DESC LIMIT 1"
        ).fetchone()

        if en_proceso is not None:
            self._dispatch_id = en_proceso["id"]
            truck = conn.execute(
                "SELECT unit_number, plate FROM trucks WHERE id = %s", (en_proceso["truck_id"],)
            ).fetchone()
            boletas = conn.execute(
                """
                SELECT et.folio, et.customer FROM dispatch_tickets dt
                JOIN exit_tickets et ON et.id = dt.ticket_id
                WHERE dt.dispatch_id = %s ORDER BY et.folio
                """,
                (en_proceso["id"],),
            ).fetchall()
            self._mostrar_unidad(truck)
            self._mostrar_boletas(boletas)
            self.camion_widget.iniciar_escaneo()
            return

        if self._dispatch_id is not None:
            # ya no está in_progress: acaba de terminar
            dispatch = conn.execute(
                "SELECT status FROM dispatches WHERE id = %s", (self._dispatch_id,)
            ).fetchone()
            self.camion_widget.detener_escaneo()
            self.semaforo.pintar(_CLAVE_SEMAFORO.get(dispatch["status"] if dispatch else None, "warn"))
            self._terminado_en = time.monotonic()
            return

        escaneo = live_state.obtener_escaneo()
        if escaneo is not None:
            if escaneo.get("sin_boletas"):
                self._set_texto_unidad(escaneo["truck"])
                self._mostrar_boletas([])
                if not self._sin_boletas_activo:
                    self._sin_boletas_activo = True
                    self.camion_widget.mostrar_temporalmente(2.0, "Unidad sin boletas")
                elif self.camion_widget.esta_oculto():
                    # ya entró, esperó y salió por completo
                    self._sin_boletas_activo = False
                    self._set_texto_unidad(None)
                    live_state.limpiar_escaneo()
                return
            self._sin_boletas_activo = False
            self._mostrar_unidad(escaneo["truck"])
            self._mostrar_boletas([])
            return

        self._sin_boletas_activo = False
        self._mostrar_unidad(None)
        self._mostrar_boletas([])

    def _set_texto_unidad(self, truck: dict | None) -> None:
        self.label_unidad.setText(f"Unidad: {truck['unit_number']}" if truck else "Unidad: —")
        self.label_placa.setText(f"Placa: {truck.get('plate') or '—'}" if truck else "Placa: —")

    def _mostrar_unidad(self, truck: dict | None) -> None:
        self._set_texto_unidad(truck)
        if truck is not None:
            self.camion_widget.mostrar_camion()
        else:
            self.camion_widget.ocultar_camion()

    def _mostrar_boletas(self, boletas: list[dict]) -> None:
        self.lista_boletas.clear()
        for boleta in boletas:
            self.lista_boletas.addItem(f"{boleta['folio']} — {boleta['customer']}")

    def _reiniciar_panel(self) -> None:
        self._dispatch_id = None
        self._terminado_en = None
        self._sin_boletas_activo = False
        self.semaforo.apagar()
        self.camion_widget.detener_escaneo()
        self._mostrar_unidad(None)
        self._mostrar_boletas([])
        live_state.limpiar_escaneo()

    def _set_valor(self, clave: str, texto: str, estado: str | None) -> None:
        label = self._valores[clave]
        label.setText(texto)
        label.setProperty("estado", estado)
        label.style().unpolish(label)
        label.style().polish(label)
