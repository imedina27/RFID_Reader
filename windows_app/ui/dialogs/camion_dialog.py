"""Diálogo de alta/edición de camión (pantalla Camiones)."""

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


class CamionDialog(QDialog):
    """modo='editar': número económico fijo, placa/chofer editables (alta y
    edición normal). modo='etiqueta': todo fijo salvo la etiqueta de
    parabrisas (botón "Asignar etiqueta" de la pantalla Camiones)."""

    def __init__(
        self,
        camion: dict | None = None,
        modo: str = "editar",
        parent: QWidget | None = None,
    ):
        super().__init__(parent)
        self._editando = camion is not None
        self._modo = modo
        self.setWindowTitle(
            "Etiqueta de parabrisas" if modo == "etiqueta"
            else ("Editar camión" if self._editando else "Nuevo camión")
        )
        self.setMinimumWidth(320)
        self._resultado: dict | None = None

        self.in_unit_number = QLineEdit(camion["unit_number"] if camion else "")
        self.in_plate = QLineEdit(camion.get("plate") or "" if camion else "")
        self.in_driver = QLineEdit(camion.get("driver") or "" if camion else "")
        self.in_epc = QLineEdit(camion.get("epc") or "" if camion else "")

        editable_datos = modo == "editar"
        self.in_unit_number.setReadOnly(self._editando)  # no se puede cambiar (docs/api.md)
        self.in_plate.setEnabled(editable_datos)
        self.in_driver.setEnabled(editable_datos)
        self.in_epc.setEnabled(not editable_datos)

        form = QFormLayout()
        form.addRow("Número económico", self.in_unit_number)
        form.addRow("Placa", self.in_plate)
        form.addRow("Chofer", self.in_driver)
        form.addRow("Etiqueta (parabrisas)", self.in_epc)

        botones = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        botones.accepted.connect(self._aceptar)
        botones.rejected.connect(self.reject)

        root = QVBoxLayout(self)
        root.addLayout(form)
        root.addWidget(botones)

    def _aceptar(self) -> None:
        if self._modo == "etiqueta":
            self._resultado = {"epc": self.in_epc.text().strip() or None}
            self.accept()
            return

        unit_number = self.in_unit_number.text().strip()
        self.in_unit_number.setStyleSheet("")
        if not unit_number:
            self.in_unit_number.setStyleSheet(f"border: 2px solid {_BORDE_FALTANTE};")
            QMessageBox.warning(self, "Datos incompletos", "Falta el número económico.")
            return
        self._resultado = {
            "unit_number": unit_number,
            "plate": self.in_plate.text().strip() or None,
            "driver": self.in_driver.text().strip() or None,
        }
        self.accept()

    def resultado(self) -> dict | None:
        return self._resultado

    @classmethod
    def pedir(
        cls,
        camion: dict | None = None,
        modo: str = "editar",
        parent: QWidget | None = None,
    ) -> dict | None:
        dlg = cls(camion, modo, parent)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            return dlg.resultado()
        return None
