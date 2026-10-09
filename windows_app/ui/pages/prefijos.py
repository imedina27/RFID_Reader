"""Pantalla Prefijos: lista blanca de prefijos de EPC (docs/funcional.md,
sección 6). Una etiqueta leída que no empiece con ninguno de estos
prefijos se ignora por completo (ajena al proyecto). Solo Agregar y
Eliminar — no hace falta editar un prefijo, solo corregirlo si se
ingresó mal (se borra y se vuelve a agregar)."""

from email.utils import parsedate_to_datetime
from zoneinfo import ZoneInfo

from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import (
    QAbstractItemView,
    QHBoxLayout,
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

COLUMNAS = ["Prefijo", "Fecha"]
_ZONA_LOCAL = ZoneInfo("America/Mexico_City")


def _fecha_local(valor: str | None) -> str:
    if not valor:
        return ""
    try:
        return parsedate_to_datetime(valor).astimezone(_ZONA_LOCAL).strftime("%d/%m/%Y %H:%M")
    except (TypeError, ValueError):
        return valor


class PrefijosPage(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self._prefijos: list[dict] = []

        layout = QVBoxLayout(self)

        header = QLabel("Prefijos")
        header.setObjectName("section_header")
        layout.addWidget(header)

        nota = QLabel(
            "Una etiqueta leída que no empiece con ninguno de estos prefijos "
            "se ignora por completo (se asume ajena al proyecto)."
        )
        layout.addWidget(nota)

        barra = QHBoxLayout()
        self.btn_agregar = QPushButton("+ Agregar")
        self.btn_agregar.setObjectName("add_button")
        self.btn_agregar.clicked.connect(self._agregar)
        barra.addWidget(self.btn_agregar)

        self.btn_eliminar = QPushButton("Eliminar")
        self.btn_eliminar.setObjectName("edit_button")
        self.btn_eliminar.setEnabled(False)
        self.btn_eliminar.clicked.connect(self._eliminar)
        barra.addWidget(self.btn_eliminar)

        barra.addStretch(1)
        layout.addLayout(barra)

        self.tabla = QTableWidget(0, len(COLUMNAS))
        self.tabla.setHorizontalHeaderLabels(COLUMNAS)
        self.tabla.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.tabla.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.tabla.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.tabla.verticalHeader().setVisible(False)
        self.tabla.setAlternatingRowColors(True)
        self.tabla.itemSelectionChanged.connect(self._actualizar_botones)
        layout.addWidget(self.tabla, 1)

        self.refrescar()

    # ----------------------------------------------------------

    def refrescar(self) -> None:
        try:
            self._prefijos = api_client.list_epc_prefixes()
        except Exception as ex:
            QMessageBox.warning(self, "No se pudo cargar", str(ex))
            return

        self.tabla.setRowCount(len(self._prefijos))
        for fila, prefijo in enumerate(self._prefijos):
            valores = [prefijo["prefix"], _fecha_local(prefijo["created_at"])]
            for col, texto in enumerate(valores):
                item = QTableWidgetItem(texto)
                item.setFlags(item.flags() & ~Qt.ItemFlag.ItemIsEditable)
                self.tabla.setItem(fila, col, item)
        self._actualizar_botones()

    def _fila_seleccionada(self) -> dict | None:
        filas = self.tabla.selectionModel().selectedRows()
        if not filas:
            return None
        return self._prefijos[filas[0].row()]

    def _actualizar_botones(self) -> None:
        self.btn_eliminar.setEnabled(self._fila_seleccionada() is not None)

    # ----------------------------------------------------------

    def _agregar(self) -> None:
        prefijo, ok = QInputDialog.getText(self, "Agregar prefijo", "Prefijo de EPC (ej. E28011):")
        if not ok or not prefijo.strip():
            return
        try:
            api_client.create_epc_prefix(prefijo.strip())
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo agregar", ex.message)
            return
        self.refrescar()

    def _eliminar(self) -> None:
        prefijo = self._fila_seleccionada()
        if prefijo is None:
            return
        respuesta = QMessageBox.question(
            self, "Eliminar prefijo",
            f"¿Seguro que quieres eliminar el prefijo \"{prefijo['prefix']}\"?",
        )
        if respuesta != QMessageBox.StandardButton.Yes:
            return
        try:
            api_client.delete_epc_prefix(prefijo["id"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo eliminar", ex.message)
            return
        self.refrescar()
