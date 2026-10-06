from pathlib import Path

from PyQt6.QtCore import QSize, Qt
from PyQt6.QtGui import QCursor, QIcon, QPixmap
from PyQt6.QtWidgets import (
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QPushButton,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from ui.pages.placeholder import PlaceholderPage
from ui.pages.tablero import TableroPage
from ui.styles.stylesheet import StyleSheet
from ui.widgets.imagen_escalada import ImagenEscalada

IMAGES_DIR = Path(__file__).resolve().parent / "images"

# Pantallas de la app Windows (docs/funcional.md, sección 6). "Tablero" ya
# tiene contenido real; el resto son placeholders hasta que se construyan.
NAV_ITEMS = [
    "Tablero",
    "Productos",
    "Camiones",
    "Etiquetas",
    "Boletas de salida",
    "Salidas a ruta",
    "Alarmas",
]


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("RFID — Salida a Ruta")
        self.setWindowIcon(QIcon(str(IMAGES_DIR / "general" / "logo.png")))
        self.setMinimumSize(1280, 720)

        self.is_dark_theme = True

        self._build_ui()
        self.apply_theme()

    # ----------------------------------------------------------
    # CONSTRUCCIÓN DE LA UI
    # ----------------------------------------------------------

    def _build_ui(self):
        self.main_widget = QWidget()
        self.setCentralWidget(self.main_widget)
        self.root_layout = QVBoxLayout(self.main_widget)
        self.root_layout.setContentsMargins(0, 0, 0, 0)
        self.root_layout.setSpacing(10)

        self._build_top_bar()
        self._build_title_bar()
        self._build_body()
        self.statusBar()

    def _build_top_bar(self):
        """Barra de color decorativa en la parte superior (imagen corporativa)."""
        top_bar = QLabel()
        top_bar.setFixedHeight(8)
        top_bar.setPixmap(QPixmap(str(IMAGES_DIR / "general" / "header.png")))
        top_bar.setScaledContents(True)
        self.root_layout.addWidget(top_bar)

    def _build_title_bar(self):
        """Barra superior con logo, título y botón de tema."""
        bar_height = 56

        title_widget = QWidget()
        title_widget.setObjectName("title_widget")
        title_widget.setFixedHeight(bar_height)

        layout = QHBoxLayout(title_widget)
        layout.setContentsMargins(20, 0, 20, 0)

        self.logo_label = ImagenEscalada()
        self.logo_label.setFixedSize(int(bar_height * 1.7), bar_height)
        self.logo_label.set_imagen(str(IMAGES_DIR / "general" / "logo_txt.png"))
        layout.addWidget(self.logo_label)
        layout.addStretch(1)

        title_label = QLabel("Salida a Ruta y Captura de Tags")
        title_label.setObjectName("title_label")
        title_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title_label, 1)
        layout.addStretch(1)

        self.theme_button = QPushButton()
        self.theme_button.setObjectName("theme_button")
        self.theme_button.setFixedSize(QSize(40, 40))
        self.theme_button.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.theme_button.clicked.connect(self.toggle_theme)
        layout.addWidget(self.theme_button)

        self.root_layout.addWidget(title_widget)

    def _build_body(self):
        """Navegación izquierda + pantallas en un QStackedWidget."""
        body_widget = QWidget()
        body_layout = QHBoxLayout(body_widget)
        body_layout.setContentsMargins(0, 0, 0, 0)
        body_layout.setSpacing(0)

        self.nav_list = QListWidget()
        self.nav_list.setObjectName("nav_list")
        self.nav_list.setFixedWidth(200)
        for item in NAV_ITEMS:
            QListWidgetItem(item, self.nav_list)

        nav_frame = QWidget()
        nav_frame.setObjectName("nav_frame")
        nav_layout = QVBoxLayout(nav_frame)
        nav_layout.setContentsMargins(0, 10, 0, 0)
        nav_layout.addWidget(self.nav_list)
        body_layout.addWidget(nav_frame)

        content_frame = QWidget()
        content_frame.setObjectName("content_frame")
        content_layout = QVBoxLayout(content_frame)
        self.stacked = QStackedWidget()
        content_layout.addWidget(self.stacked)
        body_layout.addWidget(content_frame, 1)

        for item in NAV_ITEMS:
            page = TableroPage() if item == "Tablero" else PlaceholderPage(item)
            self.stacked.addWidget(page)

        self.nav_list.currentRowChanged.connect(self.stacked.setCurrentIndex)
        self.nav_list.setCurrentRow(0)

        self.root_layout.addWidget(body_widget, 1)

    # ----------------------------------------------------------
    # TEMA
    # ----------------------------------------------------------

    def apply_theme(self):
        theme = StyleSheet.dark_theme if self.is_dark_theme else StyleSheet.light_theme
        self.setStyleSheet(StyleSheet.styles(theme))
        self.theme_button.setIcon(QIcon(theme["theme_icon"]))
        self.theme_button.setIconSize(QSize(30, 30))
        self.logo_label.rescale()

    def toggle_theme(self):
        self.is_dark_theme = not self.is_dark_theme
        self.apply_theme()
