"""
QLabel que muestra una imagen reescalada a su tamaño actual.

Se re-escala en cada `resizeEvent` y cuando la ventana cambia de pantalla
(el `MainWindow` engancha `windowHandle().screenChanged` y llama a
`rescale()` en cada instancia). Tiene en cuenta el `devicePixelRatio` de
la pantalla, así que se ve nítida también en monitores con distinto DPI.
"""

from __future__ import annotations

from PyQt6.QtCore import QSize, Qt
from PyQt6.QtGui import QPixmap
from PyQt6.QtWidgets import QLabel, QSizePolicy, QWidget


class ImagenEscalada(QLabel):
    def __init__(self, parent: QWidget | None = None, *, maximo: int = 0):
        super().__init__(parent)
        self._original = QPixmap()
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setMinimumSize(1, 1)
        # Crece para llenar su hueco; el tamaño del pixmap NO arrastra al
        # layout (ver minimumSizeHint / sizeHint más abajo).
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        if maximo:
            self.setMaximumSize(maximo, maximo)

    def minimumSizeHint(self) -> QSize:  # noqa: N802 (API de Qt)
        return QSize(1, 1)

    def sizeHint(self) -> QSize:  # noqa: N802 (API de Qt)
        return QSize(64, 64)

    # ------------------------------------------------------------
    # API
    # ------------------------------------------------------------

    def set_imagen(self, fuente: str | QPixmap | None) -> None:
        """`fuente`: ruta de archivo o QPixmap. Vacío / inexistente -> limpia."""
        if isinstance(fuente, QPixmap):
            pixmap = fuente
        elif fuente:
            pixmap = QPixmap(fuente)
        else:
            pixmap = QPixmap()
        self._original = pixmap
        self._rescale()

    def limpiar(self) -> None:
        self._original = QPixmap()
        super().clear()

    def rescale(self) -> None:
        """Fuerza el reescalado (p. ej. al cambiar de pantalla)."""
        self._rescale()

    # ------------------------------------------------------------
    # Internos
    # ------------------------------------------------------------

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        self._rescale()

    def _rescale(self) -> None:
        if self._original.isNull() or self.width() < 2 or self.height() < 2:
            super().clear()
            return
        dpr = self.devicePixelRatioF()
        ancho_px = max(1, round(self.width() * dpr))
        alto_px = max(1, round(self.height() * dpr))
        escalada = self._original.scaled(
            ancho_px,
            alto_px,
            Qt.AspectRatioMode.KeepAspectRatio,
            Qt.TransformationMode.SmoothTransformation,
        )
        escalada.setDevicePixelRatio(dpr)
        super().setPixmap(escalada)
