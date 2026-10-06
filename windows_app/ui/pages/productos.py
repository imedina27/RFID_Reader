"""Pantalla Productos: catálogo con alta, edición y baja/reactivación
(docs/funcional.md, sección 6). "Baja" es `active = false` (no se borra la
fila: products.id sigue referenciado por tags y exit_ticket_lines)."""

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
from ui.dialogs.producto_dialog import ProductoDialog

COLUMNAS = ["Clave", "Nombre", "Presentación", "Estado"]


class ProductosPage(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self._productos: list[dict] = []

        layout = QVBoxLayout(self)

        header = QLabel("Productos")
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

        self.btn_baja = QPushButton("Dar de baja")
        self.btn_baja.setObjectName("edit_button")
        self.btn_baja.setEnabled(False)
        self.btn_baja.clicked.connect(self._alternar_activo)
        barra.addWidget(self.btn_baja)

        barra.addStretch(1)
        layout.addLayout(barra)

        self.tabla = QTableWidget(0, len(COLUMNAS))
        self.tabla.setHorizontalHeaderLabels(COLUMNAS)
        self.tabla.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.tabla.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.tabla.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.tabla.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.tabla.verticalHeader().setVisible(False)
        self.tabla.setAlternatingRowColors(True)
        self.tabla.itemSelectionChanged.connect(self._actualizar_botones)
        self.tabla.doubleClicked.connect(self._editar)
        layout.addWidget(self.tabla, 1)

        self.refrescar()

    # ----------------------------------------------------------

    def refrescar(self) -> None:
        try:
            self._productos = api_client.list_products()
        except Exception as ex:
            QMessageBox.warning(self, "No se pudo cargar", str(ex))
            return
        self.tabla.setRowCount(len(self._productos))
        for fila, producto in enumerate(self._productos):
            valores = [
                producto["code"],
                producto["name"],
                producto.get("presentation") or "",
                "Activo" if producto["active"] else "Baja",
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
        return self._productos[filas[0].row()]

    def _actualizar_botones(self) -> None:
        producto = self._fila_seleccionada()
        self.btn_editar.setEnabled(producto is not None)
        self.btn_baja.setEnabled(producto is not None)
        if producto is not None:
            self.btn_baja.setText("Reactivar" if not producto["active"] else "Dar de baja")

    # ----------------------------------------------------------

    def _agregar(self) -> None:
        datos = ProductoDialog.pedir(parent=self)
        if datos is None:
            return
        try:
            api_client.create_product(datos["code"], datos["name"], datos["presentation"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo crear", ex.message)
            return
        self.refrescar()

    def _editar(self) -> None:
        producto = self._fila_seleccionada()
        if producto is None:
            return
        datos = ProductoDialog.pedir(producto, parent=self)
        if datos is None:
            return
        try:
            api_client.update_product(
                producto["id"], name=datos["name"], presentation=datos["presentation"]
            )
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo guardar", ex.message)
            return
        self.refrescar()

    def _alternar_activo(self) -> None:
        producto = self._fila_seleccionada()
        if producto is None:
            return
        try:
            api_client.update_product(producto["id"], active=not producto["active"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo guardar", ex.message)
            return
        self.refrescar()
