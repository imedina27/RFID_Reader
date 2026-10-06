"""Receptor de prueba para la APK minima de lectura RFID (ROADMAP.md, Fase 6).

Standalone y separado de windows_app/: solo imprime en la consola cada EPC
que llega de la APK. No toca PostgreSQL ni la API principal -- sirve para
validar, antes que nada, que el celular se conecta a la lectora CS108-2 y
que la red (hotspot) entrega los datos hasta la laptop.

Uso:
    pipenv run python tools/tag_receiver.py [puerto]

Luego, en la pantalla de Ajustes de la APK, apuntar a la IP de esta laptop
(se ve con `ipconfig`) y este puerto (5000 por defecto).
"""

import sys
from datetime import datetime

from flask import Flask, jsonify, request

app = Flask(__name__)


@app.post("/tag")
def recibir_tag():
    body = request.get_json(force=True) or {}
    epc = str(body.get("epc", "")).strip().upper()
    if not epc:
        return jsonify({"error": "missing_epc", "message": "Falta el EPC."}), 400
    print(f"{datetime.now():%H:%M:%S}  {epc}")
    return jsonify({"status": "ok", "epc": epc})


@app.get("/health")
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    puerto = int(sys.argv[1]) if len(sys.argv) > 1 else 5000
    print(f"Receptor de tags escuchando en 0.0.0.0:{puerto} (Ctrl+C para salir)")
    app.run(host="0.0.0.0", port=puerto)
