"""Pantalla Boletas de salida: alta (folio automático, cliente, camión y
líneas de producto/pallets), edición y cancelación (docs/funcional.md,
sección 6). Solo se puede editar o cancelar una boleta `active`."""

from email.utils import parsedate_to_datetime
from zoneinfo import ZoneInfo

from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import (
    QAbstractItemView,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QMessageBox,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from ui import api_client
from ui.dialogs.boleta_dialog import BoletaDialog

COLUMNAS = ["Folio", "Cliente", "Camión", "Líneas", "Estado", "Fecha"]

_ESTADOS = {"active": "Activa", "dispatched": "Despachada", "cancelled": "Cancelada"}
_ZONA_LOCAL = ZoneInfo("America/Mexico_City")


def _fecha_local(valor: str | None) -> str:
    if not valor:
        return ""
    try:
        return parsedate_to_datetime(valor).astimezone(_ZONA_LOCAL).strftime("%d/%m/%Y %H:%M")
    except (TypeError, ValueError):
        return valor


def _resumen_lineas(lineas: list[dict]) -> str:
    return ", ".join(f"{linea['product_name']} ×{linea['pallets']}" for linea in lineas)


class BoletasPage(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self._boletas: list[dict] = []

        layout = QVBoxLayout(self)

        header = QLabel("Boletas de salida")
        header.setObjectName("section_header")
        layout.addWidget(header)

        barra = QHBoxLayout()
        self.btn_agregar = QPushButton("+ Agregar")
        self.btn_agregar.setObjectName("add_button")
        self.btn_agregar.clicked.connect(self._agregar)
        barra.addWidget(self.btn_agregar)

        self.btn_editar = QPushButton("Editar")
        self.btn_editar.setObjectName("edit_button")
        self.btn_editar.setEnabled(False)
        self.btn_editar.clicked.connect(self._editar)
        barra.addWidget(self.btn_editar)

        self.btn_cancelar = QPushButton("Cancelar boleta")
        self.btn_cancelar.setObjectName("edit_button")
        self.btn_cancelar.setEnabled(False)
        self.btn_cancelar.clicked.connect(self._cancelar)
        barra.addWidget(self.btn_cancelar)

        barra.addStretch(1)
        layout.addLayout(barra)

        self.tabla = QTableWidget(0, len(COLUMNAS))
        self.tabla.setHorizontalHeaderLabels(COLUMNAS)
        self.tabla.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.tabla.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.tabla.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.tabla.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        self.tabla.verticalHeader().setVisible(False)
        self.tabla.setAlternatingRowColors(True)
        self.tabla.itemSelectionChanged.connect(self._actualizar_botones)
        self.tabla.doubleClicked.connect(self._editar)
        layout.addWidget(self.tabla, 1)

        self.refrescar()

    # ----------------------------------------------------------

    def refrescar(self) -> None:
        try:
            self._boletas = api_client.list_exit_tickets()
            camiones = {t["id"]: t["unit_number"] for t in api_client.list_trucks()}
        except Exception as ex:
            QMessageBox.warning(self, "No se pudo cargar", str(ex))
            return

        self.tabla.setRowCount(len(self._boletas))
        for fila, boleta in enumerate(self._boletas):
            valores = [
                boleta["folio"],
                boleta["customer"],
                camiones.get(boleta["truck_id"], "Sin asignar"),
                _resumen_lineas(boleta["lines"]),
                _ESTADOS.get(boleta["status"], boleta["status"]),
                _fecha_local(boleta["created_at"]),
            ]
            for col, texto in enumerate(valores):
                item = QTableWidgetItem(texto)
                item.setFlags(item.flags() & ~Qt.ItemFlag.ItemIsEditable)
                self.tabla.setItem(fila, col, item)
        self._actualizar_botones()

    def _fila_seleccionada(self) -> dict | None:
        filas = self.tabla.selectionModel().selectedRows()
        if not filas:
            return None
        return self._boletas[filas[0].row()]

    def _actualizar_botones(self) -> None:
        boleta = self._fila_seleccionada()
        activa = boleta is not None and boleta["status"] == "active"
        self.btn_editar.setEnabled(activa)
        self.btn_cancelar.setEnabled(activa)

    # ----------------------------------------------------------

    def _agregar(self) -> None:
        datos = BoletaDialog.pedir(parent=self)
        if datos is None:
            return
        try:
            api_client.create_exit_ticket(datos["customer"], datos["truck_id"], datos["lines"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo crear", ex.message)
            return
        self.refrescar()

    def _editar(self) -> None:
        boleta = self._fila_seleccionada()
        if boleta is None or boleta["status"] != "active":
            return
        datos = BoletaDialog.pedir(boleta, parent=self)
        if datos is None:
            return
        try:
            api_client.update_exit_ticket(
                boleta["id"], customer=datos["customer"], lines=datos["lines"]
            )
            if datos["truck_id"] != boleta.get("truck_id") and datos["truck_id"] is not None:
                api_client.assign_exit_ticket_truck(boleta["id"], datos["truck_id"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo guardar", ex.message)
            return
        self.refrescar()

    def _cancelar(self) -> None:
        boleta = self._fila_seleccionada()
        if boleta is None or boleta["status"] != "active":
            return
        respuesta = QMessageBox.question(
            self, "Cancelar boleta",
            f"¿Seguro que quieres cancelar la boleta {boleta['folio']}? No se puede deshacer.",
        )
        if respuesta != QMessageBox.StandardButton.Yes:
            return
        try:
            api_client.cancel_exit_ticket(boleta["id"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo cancelar", ex.message)
            return
        self.refrescar()
