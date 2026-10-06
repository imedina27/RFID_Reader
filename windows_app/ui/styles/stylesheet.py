# ============================================================
# ESTILOS (ui/styles/stylesheet.py)
# Paleta e iconos tomados de Cam_Lens_V2 (imagen corporativa de
# Quantum Labs), recortados a lo que esta app usa. Los temas
# (dark/light) son diccionarios de colores/iconos; los selectores
# usan contexto (QFrame#content_frame QComboBox) en vez de listar
# cada widget por objectName.
# ============================================================

from pathlib import Path

IMAGES_DIR = Path(__file__).resolve().parent.parent / "images"
ICONS_DIR = IMAGES_DIR / "icons"

# Color de acento de la marca (mismo naranja que el logo de Quantum Labs)
_ACENTO = "#E2724B"
_OK = "#2FA36B"
_WARN = "#E0902E"
_FAIL = "#D9534F"


class StyleSheet:

    light_theme = {
        "background": "#FAF8F6",
        "text": "#1E1E28",
        "frame_background": "#EDE8E2",
        "border": "#DCD4C9",
        "border_light": "#1E1E28",
        "hover_background": "#E4DDD3",
        "theme_icon": str(ICONS_DIR / "light.png"),
        "open_icon": str(ICONS_DIR / "light_arrow_open.png"),
        "close_icon": str(ICONS_DIR / "light_arrow_close.png"),
        "dropdown_arrow": str(ICONS_DIR / "light_dropdown.png"),
    }

    dark_theme = {
        "background": "#1E1E28",
        "text": "#F5F5FA",
        "frame_background": "#3C3C46",
        "border": "#50505A",
        "border_light": "#F5F5FA",
        "hover_background": "#50505A",
        "theme_icon": str(ICONS_DIR / "dark.png"),
        "open_icon": str(ICONS_DIR / "dark_arrow_open.png"),
        "close_icon": str(ICONS_DIR / "dark_arrow_close.png"),
        "dropdown_arrow": str(ICONS_DIR / "dark_dropdown.png"),
    }

    @staticmethod
    def styles(theme: dict) -> str:
        t = theme
        return f"""
        /* ====================================================
           VENTANA Y CONTENEDORES
           ==================================================== */
        QMainWindow {{
            background-color: {t['background']};
            color: {t['text']};
        }}
        QWidget#title_widget {{
            background-color: transparent;
            border: none;
        }}
        QFrame#content_frame {{
            background-color: {t['frame_background']};
            border: 2px solid {t['border']};
            border-radius: 20px;
            margin: 4px;
        }}
        QFrame#nav_frame {{
            background-color: {t['background']};
            border: none;
        }}
        QStatusBar {{
            color: {t['text']};
            background-color: {t['background']};
        }}

        /* ====================================================
           LABELS
           ==================================================== */
        QLabel {{
            color: {t['text']};
            background-color: transparent;
            border: none;
            font-weight: bold;
        }}
        QLabel#title_label {{
            font-size: 22pt;
        }}
        QLabel#section_header {{
            font-size: 14pt;
            font-weight: bold;
            color: {_ACENTO};
            padding: 3px 0 2px 0;
        }}
        QLabel#field_label {{
            font-size: 11pt;
            font-weight: normal;
        }}
        QLabel#field_value {{
            font-size: 11pt;
            font-weight: bold;
        }}
        QLabel#field_value[estado="ok"]   {{ color: {_OK}; }}
        QLabel#field_value[estado="warn"] {{ color: {_WARN}; }}
        QLabel#field_value[estado="fail"] {{ color: {_FAIL}; }}

        /* ====================================================
           NAVEGACIÓN IZQUIERDA
           ==================================================== */
        QListWidget#nav_list {{
            background-color: {t['background']};
            border: none;
            outline: none;
            font-size: 12pt;
            font-weight: 600;
        }}
        QListWidget#nav_list::item {{
            padding: 10px 14px;
            border-radius: 8px;
            margin: 2px 8px;
        }}
        QListWidget#nav_list::item:hover {{
            background-color: {t['hover_background']};
        }}
        QListWidget#nav_list::item:selected {{
            background-color: {_ACENTO};
            color: {t['background']};
        }}

        /* ====================================================
           INPUTS / COMBOS
           ==================================================== */
        QLineEdit {{
            background-color: {t['background']};
            color: {t['text']};
            font-weight: bold;
            padding: 4px 5px;
            min-height: 22px;
            border: none;
            border-bottom: 2px solid {t['border']};
            selection-background-color: {_ACENTO};
        }}
        QLineEdit:focus {{
            border-bottom: 2px solid {_ACENTO};
        }}
        QLineEdit:read-only {{
            background-color: {t['frame_background']};
        }}
        QComboBox {{
            background-color: {t['background']};
            color: {t['text']};
            border: 1px solid {t['border']};
            border-radius: 3px;
            padding: 2px 18px 2px 5px;
            min-height: 22px;
        }}
        QComboBox:focus {{
            border: 1px solid {_ACENTO};
        }}
        QComboBox::drop-down {{
            subcontrol-origin: padding;
            subcontrol-position: top right;
            width: 20px;
            border: none;
            background: {t['background']};
        }}
        QComboBox::down-arrow {{
            width: 16px;
            height: 18px;
            image: url({t['dropdown_arrow']});
        }}
        QComboBox QAbstractItemView {{
            background-color: {t['background']};
            color: {t['text']};
            border: 1px solid {t['border']};
            selection-background-color: {t['hover_background']};
            outline: none;
        }}

        /* ====================================================
           BOTONES
           ==================================================== */
        QPushButton#theme_button {{
            background-color: transparent;
            border: none;
        }}
        QPushButton#add_button {{
            background: none;
            color: {t['text']};
            border: 1px solid {t['border']};
            border-radius: 6px;
            padding: 5px 16px;
            font-size: 11pt;
            font-weight: 700;
        }}
        QPushButton#add_button:hover {{
            background-color: {t['hover_background']};
            border: 1px solid {_ACENTO};
        }}
        QPushButton#edit_button {{
            background: none;
            color: {t['text']};
            border: 1px solid {t['border']};
            border-radius: 6px;
            padding: 3px 12px;
            font-size: 10pt;
        }}
        QPushButton#edit_button:hover {{
            background-color: {t['hover_background']};
            border: 1px solid {_ACENTO};
        }}
        QPushButton#edit_button:disabled {{
            color: {t['border']};
        }}

        /* ====================================================
           TABLAS
           ==================================================== */
        QTableWidget {{
            background-color: {t['background']};
            alternate-background-color: {t['frame_background']};
            color: {t['text']};
            gridline-color: {t['border']};
            border: 1px solid {t['border']};
        }}
        QHeaderView::section {{
            background-color: {t['frame_background']};
            color: {t['text']};
            border: none;
            border-right: 1px solid {t['border']};
            border-bottom: 2px solid {t['border_light']};
            padding: 4px;
            font-weight: 600;
        }}
        QTableWidget QTableCornerButton::section {{
            background-color: {t['frame_background']};
            border: none;
        }}

        /* ====================================================
           DIÁLOGOS Y SCROLLBAR
           ==================================================== */
        QDialog {{
            background-color: {t['background']};
            color: {t['text']};
        }}
        QDialog QPushButton {{
            background-color: {t['background']};
            color: {t['text']};
            border: 1px solid {t['border']};
            border-radius: 4px;
            padding: 5px 14px;
            min-width: 70px;
        }}
        QDialog QPushButton:hover {{
            background-color: {t['hover_background']};
        }}
        QDialog QPushButton:default {{
            border: 1px solid {_ACENTO};
        }}
        QScrollBar:vertical {{
            background: transparent;
            width: 8px;
            margin: 2px;
        }}
        QScrollBar::handle:vertical {{
            background: {t['border']};
            border-radius: 4px;
            min-height: 24px;
        }}
        QScrollBar::handle:vertical:hover {{
            background: {t['hover_background']};
        }}
        QScrollBar::add-line:vertical,
        QScrollBar::sub-line:vertical,
        QScrollBar::add-page:vertical,
        QScrollBar::sub-page:vertical {{
            height: 0;
            background: transparent;
        }}
        """
