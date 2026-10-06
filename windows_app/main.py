"""Punto de entrada de la app Windows (Fase 4: solo API).

En la Fase 5 la interfaz PyQt6 correra en el hilo principal y esta llamada
a app.run se movera a un hilo secundario (ROADMAP.md, seccion 4.1).
"""

import db
from api import create_app

if __name__ == "__main__":
    db.init_schema()
    app = create_app()
    app.run(host="0.0.0.0", port=5000)
