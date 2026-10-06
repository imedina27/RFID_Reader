"""Pantalla Tablero: estado de PostgreSQL, IP/puerto de la API y resumen del
día (docs/funcional.md, sección 6). Consulta PostgreSQL directamente cada
2 s (ROADMAP.md, sección 4.1), sin pasar por la API HTTP."""

import socket

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtWidgets import QGridLayout, QLabel, QVBoxLayout, QWidget

import db

API_PORT = 5000


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
        layout = QVBoxLayout(self)

        header = QLabel("Tablero")
        header.setObjectName("section_header")
        layout.addWidget(header)

        direccion = QLabel(f"API: http://{_ip_local()}:{API_PORT}")
        layout.addWidget(direccion)

        grid = QGridLayout()
        layout.addLayout(grid)
        layout.addStretch(1)

        self._valores: dict[str, QLabel] = {}
        filas = [
            ("db", "PostgreSQL"),
            ("alarmas", "Alarmas abiertas"),
            ("en_ruta", "Camiones en ruta"),
            ("salidas_hoy", "Salidas del día"),
        ]
        for fila, (clave, etiqueta) in enumerate(filas):
            nombre = QLabel(etiqueta)
            nombre.setObjectName("field_label")
            valor = QLabel("—")
            valor.setObjectName("field_value")
            grid.addWidget(nombre, fila, 0)
            grid.addWidget(valor, fila, 1)
            self._valores[clave] = valor

        self._timer = QTimer(self)
        self._timer.setInterval(2000)
        self._timer.timeout.connect(self.refrescar)
        self._timer.start()
        self.refrescar()

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
        except Exception:
            self._set_valor("db", "No disponible", "fail")
            return

        self._set_valor("db", "Conectado", "ok")
        self._set_valor("alarmas", str(alarmas), "warn" if alarmas else "ok")
        self._set_valor("en_ruta", str(en_ruta), None)
        self._set_valor("salidas_hoy", str(salidas_hoy), None)

    def _set_valor(self, clave: str, texto: str, estado: str | None) -> None:
        label = self._valores[clave]
        label.setText(texto)
        label.setProperty("estado", estado)
        label.style().unpolish(label)
        label.style().polish(label)
