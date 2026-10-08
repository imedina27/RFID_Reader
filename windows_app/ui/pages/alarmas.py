"""Pantalla Alarmas: lista (abiertas y atendidas) con aviso visual y sonoro,
y marcar como atendida (docs/funcional.md, sección 6). Solo cubre los 4
tipos que de verdad se guardan en `alarms` (faltante, excedente, etiqueta no
registrada, pallet ya despachado) — "camión sin boletas activas" y "camión
no disponible" son avisos de la APK al momento, no quedan guardados aquí
(decisión del usuario, 2026-10-07)."""

from email.utils import parsedate_to_datetime
from zoneinfo import ZoneInfo

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtGui import QColor
from PyQt6.QtWidgets import (
    QAbstractItemView,
    QApplication,
    QComboBox,
    QHBoxLayout,
    QHeaderView,
    QInputDialog,
    QLabel,
    QMessageBox,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from ui import api_client

COLUMNAS = ["Fecha", "Tipo", "Camión", "Mensaje", "Atendida por", "Estado"]

_TIPOS = {
    "missing": "Faltante",
    "excess": "Excedente",
    "unknown_tag": "Etiqueta no registrada",
    "already_dispatched": "Pallet ya despachado",
    "no_active_tickets": "Camión sin boletas activas",
    "truck_not_available": "Camión no disponible",
}
_TIPOS_WARN = {"missing"}
_COLOR_WARN = QColor("#E0902E")
_COLOR_FAIL = QColor("#D9534F")

_FILTROS = {"Abiertas": "open", "Atendidas": "ack", "Todas": "all"}
_ZONA_LOCAL = ZoneInfo("America/Mexico_City")


def _fecha_local(valor: str | None) -> str:
    if not valor:
        return ""
    try:
        return parsedate_to_datetime(valor).astimezone(_ZONA_LOCAL).strftime("%d/%m/%Y %H:%M")
    except (TypeError, ValueError):
        return valor


class AlarmasPage(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self._alarmas: list[dict] = []
        self._ids_abiertas_previas: set[int] | None = None

        layout = QVBoxLayout(self)

        header = QLabel("Alarmas")
        header.setObjectName("section_header")
        layout.addWidget(header)

        filtros = QHBoxLayout()
        self.combo_estado = QComboBox()
        self.combo_estado.addItems(_FILTROS.keys())
        self.combo_estado.currentIndexChanged.connect(self.refrescar)
        filtros.addWidget(QLabel("Estado"))
        filtros.addWidget(self.combo_estado)
        filtros.addStretch(1)
        layout.addLayout(filtros)

        barra = QHBoxLayout()
        self.btn_atender = QPushButton("Marcar atendida")
        self.btn_atender.setObjectName("edit_button")
        self.btn_atender.setEnabled(False)
        self.btn_atender.clicked.connect(self._marcar_atendida)
        barra.addWidget(self.btn_atender)
        barra.addStretch(1)
        layout.addLayout(barra)

        self.tabla = QTableWidget(0, len(COLUMNAS))
        self.tabla.setHorizontalHeaderLabels(COLUMNAS)
        self.tabla.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.tabla.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.tabla.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.tabla.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        self.tabla.verticalHeader().setVisible(False)
        self.tabla.itemSelectionChanged.connect(self._actualizar_botones)
        layout.addWidget(self.tabla, 1)

        self._timer = QTimer(self)
        self._timer.setInterval(2000)
        self._timer.timeout.connect(lambda: self.refrescar(silencioso=True))
        self._timer.start()
        self.refrescar()

    # ----------------------------------------------------------

    def refrescar(self, silencioso: bool = False) -> None:
        filtro = _FILTROS[self.combo_estado.currentText()]
        try:
            alarmas = api_client.list_alarms(status=filtro)
            abiertas = alarmas if filtro == "open" else api_client.list_alarms(status="open")
        except Exception as ex:
            if not silencioso:
                QMessageBox.warning(self, "No se pudo cargar", str(ex))
            return

        ids_abiertas = {a["id"] for a in abiertas}
        if self._ids_abiertas_previas is not None and ids_abiertas - self._ids_abiertas_previas:
            QApplication.beep()
        self._ids_abiertas_previas = ids_abiertas

        actual = self._fila_seleccionada()
        id_seleccionado = actual["id"] if actual is not None else None

        self._alarmas = alarmas
        self.tabla.setRowCount(len(alarmas))
        for fila, alarma in enumerate(alarmas):
            abierta = alarma["acknowledged_at"] is None
            valores = [
                _fecha_local(alarma["created_at"]),
                _TIPOS.get(alarma["type"], alarma["type"]),
                alarma.get("truck_unit_number") or "",
                alarma["message"],
                alarma.get("acknowledged_by") or "",
                "Abierta" if abierta else "Atendida",
            ]
            color = None
            if abierta:
                color = _COLOR_WARN if alarma["type"] in _TIPOS_WARN else _COLOR_FAIL
            for col, texto in enumerate(valores):
                item = QTableWidgetItem(texto)
                item.setFlags(item.flags() & ~Qt.ItemFlag.ItemIsEditable)
                if color is not None:
                    item.setBackground(color)
                self.tabla.setItem(fila, col, item)
            if alarma["id"] == id_seleccionado:
                self.tabla.selectRow(fila)
        self._actualizar_botones()

    def _fila_seleccionada(self) -> dict | None:
        filas = self.tabla.selectionModel().selectedRows()
        if not filas:
            return None
        return self._alarmas[filas[0].row()]

    def _actualizar_botones(self) -> None:
        alarma = self._fila_seleccionada()
        self.btn_atender.setEnabled(alarma is not None and alarma["acknowledged_at"] is None)

    # ----------------------------------------------------------

    def _marcar_atendida(self) -> None:
        alarma = self._fila_seleccionada()
        if alarma is None:
            return
        nombre, ok = QInputDialog.getText(self, "Marcar atendida", "¿Quién atiende esta alarma?")
        if not ok or not nombre.strip():
            return
        try:
            api_client.ack_alarm(alarma["id"], nombre.strip())
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo marcar", ex.message)
            return
        self.refrescar()
