"""Diálogo de alta/edición de boleta de salida (pantalla Boletas de salida).
El folio lo genera el sistema (no se captura aquí); el cliente es texto
libre (no hay catálogo de clientes); el camión solo puede ser uno
`disponible` de la tabla Camiones; las líneas son producto + cantidad de
pallets (no se asignan etiquetas específicas, docs/funcional.md sección 3)."""

from __future__ import annotations

from PyQt6.QtWidgets import (
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QFormLayout,
    QHBoxLayout,
    QLineEdit,
    QMessageBox,
    QPushButton,
    QSpinBox,
    QTableWidget,
    QVBoxLayout,
    QWidget,
)

from ui import api_client

_BORDE_FALTANTE = "#D9534F"
_SIN_ASIGNAR = "Sin asignar"


class BoletaDialog(QDialog):
    def __init__(
        self,
        boleta: dict | None = None,
        productos: list[dict] | None = None,
        camiones: list[dict] | None = None,
        parent: QWidget | None = None,
    ):
        super().__init__(parent)
        self._editando = boleta is not None
        self._productos = productos or []
        self.setWindowTitle("Editar boleta" if self._editando else "Nueva boleta")
        self.setMinimumWidth(420)
        self._resultado: dict | None = None

        self.in_folio = QLineEdit(boleta["folio"] if boleta else "(se asigna al guardar)")
        self.in_folio.setReadOnly(True)
        self.in_cliente = QLineEdit(boleta["customer"] if boleta else "")

        camiones = camiones or []
        self.combo_camion = QComboBox()
        truck_id_actual = boleta.get("truck_id") if boleta else None
        if truck_id_actual is None:
            # una vez asignado no se puede quitar (la API no lo soporta), así
            # que "Sin asignar" solo aparece mientras no tenga camión.
            self.combo_camion.addItem(_SIN_ASIGNAR, None)
        for camion in camiones:
            if camion["status"] == "available" or camion["id"] == truck_id_actual:
                etiqueta = camion["unit_number"]
                if camion["status"] != "available":
                    # el camión asignado ya no está disponible (p. ej. quedó en
                    # ruta por otra boleta); se deja visible para no perderlo.
                    etiqueta += " (no disponible)"
                self.combo_camion.addItem(etiqueta, camion["id"])
        if truck_id_actual is not None:
            indice = self.combo_camion.findData(truck_id_actual)
            if indice >= 0:
                self.combo_camion.setCurrentIndex(indice)

        form = QFormLayout()
        form.addRow("Folio", self.in_folio)
        form.addRow("Cliente", self.in_cliente)
        form.addRow("Camión", self.combo_camion)

        self.tabla_lineas = QTableWidget(0, 3)
        self.tabla_lineas.setHorizontalHeaderLabels(["Producto", "Pallets", ""])
        self.tabla_lineas.verticalHeader().setVisible(False)
        self.tabla_lineas.horizontalHeader().setStretchLastSection(False)
        self.tabla_lineas.setColumnWidth(2, 70)

        btn_agregar_linea = QPushButton("+ Línea")
        btn_agregar_linea.clicked.connect(lambda: self._agregar_linea())

        botones = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        botones.accepted.connect(self._aceptar)
        botones.rejected.connect(self.reject)

        root = QVBoxLayout(self)
        root.addLayout(form)
        root.addWidget(self.tabla_lineas, 1)
        root.addWidget(btn_agregar_linea)
        root.addWidget(botones)

        lineas_iniciales = boleta["lines"] if boleta else []
        if lineas_iniciales:
            for linea in lineas_iniciales:
                self._agregar_linea(linea["product_id"], linea["pallets"])
        else:
            self._agregar_linea()

    def _agregar_linea(self, product_id: int | None = None, pallets: int = 1) -> None:
        fila = self.tabla_lineas.rowCount()
        self.tabla_lineas.insertRow(fila)

        combo = QComboBox()
        for producto in self._productos:
            combo.addItem(producto["name"], producto["id"])
        if product_id is not None:
            indice = combo.findData(product_id)
            if indice >= 0:
                combo.setCurrentIndex(indice)
        self.tabla_lineas.setCellWidget(fila, 0, combo)

        spin = QSpinBox()
        spin.setRange(1, 999)
        spin.setValue(pallets)
        self.tabla_lineas.setCellWidget(fila, 1, spin)

        btn_quitar = QPushButton("✕")
        btn_quitar.clicked.connect(lambda: self._quitar_linea(btn_quitar))
        contenedor = QWidget()
        envoltura = QHBoxLayout(contenedor)
        envoltura.setContentsMargins(0, 0, 0, 0)
        envoltura.addWidget(btn_quitar)
        self.tabla_lineas.setCellWidget(fila, 2, contenedor)

    def _quitar_linea(self, boton: QPushButton) -> None:
        for fila in range(self.tabla_lineas.rowCount()):
            contenedor = self.tabla_lineas.cellWidget(fila, 2)
            if contenedor is not None and boton in contenedor.findChildren(QPushButton):
                self.tabla_lineas.removeRow(fila)
                return

    def _leer_lineas(self) -> list[dict]:
        lineas = []
        for fila in range(self.tabla_lineas.rowCount()):
            combo = self.tabla_lineas.cellWidget(fila, 0)
            spin = self.tabla_lineas.cellWidget(fila, 1)
            if combo.currentData() is None:
                continue
            lineas.append({"product_id": combo.currentData(), "pallets": spin.value()})
        return lineas

    def _aceptar(self) -> None:
        cliente = self.in_cliente.text().strip()
        self.in_cliente.setStyleSheet("")
        if not cliente:
            self.in_cliente.setStyleSheet(f"border: 2px solid {_BORDE_FALTANTE};")
            QMessageBox.warning(self, "Datos incompletos", "Falta el cliente.")
            return
        lineas = self._leer_lineas()
        if not lineas:
            QMessageBox.warning(self, "Datos incompletos", "Agrega al menos una línea con producto.")
            return

        self._resultado = {
            "customer": cliente,
            "truck_id": self.combo_camion.currentData(),
            "lines": lineas,
        }
        self.accept()

    def resultado(self) -> dict | None:
        return self._resultado

    @classmethod
    def pedir(cls, boleta: dict | None = None, parent: QWidget | None = None) -> dict | None:
        productos = [p for p in api_client.list_products() if p["active"]]
        camiones = api_client.list_trucks()
        dlg = cls(boleta, productos, camiones, parent)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            return dlg.resultado()
        return None
