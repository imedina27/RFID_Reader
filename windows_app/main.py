"""Punto de entrada de la app Windows.

Proceso unico (ROADMAP.md, seccion 4.1): la interfaz PyQt6 corre en el hilo
principal y Flask corre en un hilo secundario, en 0.0.0.0:5000.
"""

import sys
import threading

from PyQt6.QtWidgets import QApplication

import db
from api import create_app
from ui.app import MainWindow


def _run_api() -> None:
    app = create_app()
    app.run(host="0.0.0.0", port=5000, use_reloader=False)


if __name__ == "__main__":
    db.init_schema()

    api_thread = threading.Thread(target=_run_api, daemon=True)
    api_thread.start()

    qt_app = QApplication(sys.argv)
    qt_app.setApplicationDisplayName("RFID — Salida a Ruta")
    window = MainWindow()
    window.showMaximized()
    sys.exit(qt_app.exec())
