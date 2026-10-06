from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import QLabel, QVBoxLayout, QWidget


class PlaceholderPage(QWidget):
    """Pantalla aun no construida (ver ROADMAP.md, Fase 5)."""

    def __init__(self, titulo: str, parent: QWidget | None = None):
        super().__init__(parent)
        layout = QVBoxLayout(self)
        header = QLabel(titulo)
        header.setObjectName("section_header")
        layout.addWidget(header)
        aviso = QLabel("Pantalla en construcción.")
        aviso.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(aviso, 1)
