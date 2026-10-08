"""Pantalla Camiones: alta, edición y etiqueta del parabrisas
(docs/funcional.md, sección 6). El número económico no se puede cambiar una
vez creado, igual que la clave de un producto. Sin "Marcar disponible": no
hace falta para esta demo (decisión del usuario, 2026-10-07)."""

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
from ui.dialogs.camion_dialog import CamionDialog

COLUMNAS = ["Número económico", "Placa", "Chofer", "Etiqueta (parabrisas)", "Estado"]

_ESTADOS = {"available": "Disponible", "en_route": "En ruta"}


class CamionesPage(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self._camiones: list[dict] = []

        layout = QVBoxLayout(self)

        header = QLabel("Camiones")
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

        self.btn_asignar_tag = QPushButton("Asignar etiqueta")
        self.btn_asignar_tag.setObjectName("edit_button")
        self.btn_asignar_tag.setEnabled(False)
        self.btn_asignar_tag.clicked.connect(self._asignar_etiqueta)
        barra.addWidget(self.btn_asignar_tag)

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
            self._camiones = api_client.list_trucks()
            tags_camion = api_client.list_tags(kind="truck")
        except Exception as ex:
            QMessageBox.warning(self, "No se pudo cargar", str(ex))
            return

        epc_por_camion = {tag["truck_id"]: tag["epc"] for tag in tags_camion}
        for camion in self._camiones:
            camion["epc"] = epc_por_camion.get(camion["id"])

        self.tabla.setRowCount(len(self._camiones))
        for fila, camion in enumerate(self._camiones):
            valores = [
                camion["unit_number"],
                camion.get("plate") or "",
                camion.get("driver") or "",
                camion["epc"] or "Sin asignar",
                _ESTADOS.get(camion["status"], camion["status"]),
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
        return self._camiones[filas[0].row()]

    def _actualizar_botones(self) -> None:
        camion = self._fila_seleccionada()
        self.btn_editar.setEnabled(camion is not None)
        self.btn_asignar_tag.setEnabled(camion is not None)

    # ----------------------------------------------------------

    def _agregar(self) -> None:
        datos = CamionDialog.pedir(parent=self)
        if datos is None:
            return
        try:
            api_client.create_truck(datos["unit_number"], datos["plate"], datos["driver"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo crear", ex.message)
            return
        self.refrescar()

    def _editar(self) -> None:
        camion = self._fila_seleccionada()
        if camion is None:
            return
        datos = CamionDialog.pedir(camion, parent=self)
        if datos is None:
            return
        try:
            api_client.update_truck(camion["id"], plate=datos["plate"], driver=datos["driver"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo guardar", ex.message)
            return
        self.refrescar()

    def _asignar_etiqueta(self) -> None:
        camion = self._fila_seleccionada()
        if camion is None:
            return
        datos = CamionDialog.pedir(camion, modo="etiqueta", parent=self)
        if datos is None:
            return

        epc_anterior = camion.get("epc")
        epc_nuevo = datos["epc"]
        if epc_nuevo == epc_anterior:
            return  # sin cambios

        try:
            if epc_anterior:
                api_client.delete_tag(epc_anterior)
            if epc_nuevo:
                api_client.assign_truck_tag(camion["id"], epc_nuevo)
        except api_client.ApiError as ex:
            mensaje = ex.message
            if epc_anterior:
                mensaje += f"\n\nLa etiqueta anterior ({epc_anterior}) ya se quitó del camión."
            QMessageBox.warning(self, "No se pudo guardar la etiqueta", mensaje)
            self.refrescar()
            return
        self.refrescar()
