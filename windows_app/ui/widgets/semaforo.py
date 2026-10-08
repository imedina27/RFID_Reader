"""Semáforo vertical (rojo/amarillo/verde) del panel en vivo del Tablero.
Se mantiene apagado (gris) mientras escanea; se pinta un solo color al
terminar la salida (docs/funcional.md, sección 6)."""

from __future__ import annotations

from PyQt6.QtWidgets import QFrame, QVBoxLayout, QWidget

_APAGADO = "#50505A"
_COLORES = {"fail": "#D9534F", "warn": "#E0902E", "ok": "#2FA36B"}


class SemaforoWidget(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setMaximumHeight(28 * 3 + 8 * 2 + 4)  # 3 luces + espacios; no se estira
        layout = QVBoxLayout(self)
        layout.setSpacing(8)
        self._luces: dict[str, QFrame] = {}
        for clave in ("fail", "warn", "ok"):
            luz = QFrame()
            luz.setFixedSize(28, 28)
            layout.addWidget(luz)
            self._luces[clave] = luz
        self.apagar()

    def apagar(self) -> None:
        for luz in self._luces.values():
            luz.setStyleSheet(f"border-radius: 14px; background-color: {_APAGADO};")

    def pintar(self, clave: str) -> None:
        self.apagar()
        self._luces[clave].setStyleSheet(f"border-radius: 14px; background-color: {_COLORES[clave]};")
