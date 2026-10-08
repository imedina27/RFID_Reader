"""Diálogo de captura manual / corrección de un pallet (pantalla Pallets).
El producto es el único dato editable (docs/funcional.md, sección 5
"Captura desde Windows")."""

from __future__ import annotations

from PyQt6.QtWidgets import (
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QFormLayout,
    QLineEdit,
    QMessageBox,
    QVBoxLayout,
    QWidget,
)

from ui import api_client

_BORDE_FALTANTE = "#D9534F"


class PalletDialog(QDialog):
    def __init__(
        self,
        pallet: dict | None = None,
        productos: list[dict] | None = None,
        parent: QWidget | None = None,
    ):
        super().__init__(parent)
        self._editando = pallet is not None
        self.setWindowTitle("Editar pallet" if self._editando else "Capturar pallet")
        self.setMinimumWidth(320)
        self._resultado: dict | None = None

        self.in_epc = QLineEdit(pallet["epc"] if pallet else "")
        self.in_epc.setReadOnly(self._editando)  # el EPC no se puede cambiar

        self.combo_producto = QComboBox()
        for producto in productos or []:
            self.combo_producto.addItem(producto["name"], producto["id"])
        if pallet is not None:
            indice = self.combo_producto.findData(pallet.get("product_id"))
            if indice >= 0:
                self.combo_producto.setCurrentIndex(indice)

        form = QFormLayout()
        form.addRow("EPC", self.in_epc)
        form.addRow("Producto", self.combo_producto)

        botones = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        botones.accepted.connect(self._aceptar)
        botones.rejected.connect(self.reject)

        root = QVBoxLayout(self)
        root.addLayout(form)
        root.addWidget(botones)

    def _aceptar(self) -> None:
        epc = self.in_epc.text().strip()
        self.in_epc.setStyleSheet("")
        if not epc:
            self.in_epc.setStyleSheet(f"border: 2px solid {_BORDE_FALTANTE};")
            QMessageBox.warning(self, "Datos incompletos", "Falta el EPC.")
            return
        if self.combo_producto.count() == 0:
            QMessageBox.warning(self, "Sin productos", "No hay productos activos para elegir.")
            return
        self._resultado = {
            "epc": epc,
            "product_id": self.combo_producto.currentData(),
        }
        self.accept()

    def resultado(self) -> dict | None:
        return self._resultado

    @classmethod
    def pedir(
        cls,
        pallet: dict | None = None,
        parent: QWidget | None = None,
    ) -> dict | None:
        productos = [p for p in api_client.list_products() if p["active"]]
        dlg = cls(pallet, productos, parent)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            return dlg.resultado()
        return None
