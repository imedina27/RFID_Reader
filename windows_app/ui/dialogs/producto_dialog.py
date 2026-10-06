"""Diálogo de alta/edición de producto (pantalla Productos)."""

from __future__ import annotations

from PyQt6.QtWidgets import (
    QDialog,
    QDialogButtonBox,
    QFormLayout,
    QLineEdit,
    QMessageBox,
    QVBoxLayout,
    QWidget,
)

_BORDE_FALTANTE = "#D9534F"


class ProductoDialog(QDialog):
    def __init__(self, producto: dict | None = None, parent: QWidget | None = None):
        super().__init__(parent)
        self._editando = producto is not None
        self.setWindowTitle("Editar producto" if self._editando else "Nuevo producto")
        self.setMinimumWidth(320)
        self._resultado: dict | None = None

        self.in_code = QLineEdit(producto["code"] if producto else "")
        self.in_code.setReadOnly(self._editando)  # el code no se puede cambiar (docs/api.md)
        self.in_name = QLineEdit(producto["name"] if producto else "")
        self.in_presentation = QLineEdit(producto.get("presentation") or "" if producto else "")

        form = QFormLayout()
        form.addRow("Clave", self.in_code)
        form.addRow("Nombre", self.in_name)
        form.addRow("Presentación", self.in_presentation)

        botones = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        botones.accepted.connect(self._aceptar)
        botones.rejected.connect(self.reject)

        root = QVBoxLayout(self)
        root.addLayout(form)
        root.addWidget(botones)

    def _aceptar(self) -> None:
        code = self.in_code.text().strip()
        name = self.in_name.text().strip()
        faltantes = []
        self.in_code.setStyleSheet("")
        self.in_name.setStyleSheet("")
        if not code:
            self.in_code.setStyleSheet(f"border: 2px solid {_BORDE_FALTANTE};")
            faltantes.append("Clave")
        if not name:
            self.in_name.setStyleSheet(f"border: 2px solid {_BORDE_FALTANTE};")
            faltantes.append("Nombre")
        if faltantes:
            QMessageBox.warning(
                self, "Datos incompletos",
                "Faltan datos obligatorios: " + ", ".join(faltantes),
            )
            return
        self._resultado = {
            "code": code,
            "name": name,
            "presentation": self.in_presentation.text().strip() or None,
        }
        self.accept()

    def resultado(self) -> dict | None:
        return self._resultado

    @classmethod
    def pedir(cls, producto: dict | None = None, parent: QWidget | None = None) -> dict | None:
        dlg = cls(producto, parent)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            return dlg.resultado()
        return None
