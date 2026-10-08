"""Ventana de solo lectura con el detalle de una salida a ruta (pantalla
Salidas a ruta): el palomeo completo, igual que lo ve la APK. Si la salida
sigue "No ha salido" (in_progress), se refresca sola cada 2 s — es el
monitor en vivo; si ya terminó, es una foto fija del resultado final."""

from PyQt6.QtCore import QTimer
from PyQt6.QtGui import QColor
from PyQt6.QtWidgets import (
    QDialog,
    QDialogButtonBox,
    QFormLayout,
    QLabel,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from ui import api_client

_COLOR_OK = QColor("#2FA36B")
_COLOR_WARN = QColor("#E0902E")
_COLOR_FAIL = QColor("#D9534F")

_RESULTADOS_PROBLEMA = {
    "unknown": "Etiqueta no registrada",
    "already_dispatched": "Pallet ya despachado",
}


class SalidaDetalleDialog(QDialog):
    def __init__(self, salida: dict, parent: QWidget | None = None):
        super().__init__(parent)
        self._dispatch_id = salida["id"]
        self._es_vivo = salida["status"] == "in_progress"
        self.setWindowTitle(f"Detalle — {salida['unit_number']}")
        self.setMinimumWidth(480)

        form = QFormLayout()
        form.addRow("Camión", QLabel(salida["unit_number"]))
        form.addRow("Folio(s)", QLabel(salida["folios"] or "—"))
        form.addRow("Cliente(s)", QLabel(salida["clientes"] or "—"))
        form.addRow("Estado", QLabel(salida["estado_texto"]))
        form.addRow("Tipo de salida", QLabel(salida["tipo_salida_texto"]))
        if salida.get("authorized_by"):
            form.addRow("Autorizó", QLabel(salida["authorized_by"]))
            form.addRow("Motivo", QLabel(salida.get("authorization_reason") or "—"))

        self.tabla = QTableWidget(0, 4)
        self.tabla.setHorizontalHeaderLabels(["Producto", "Esperado", "Leído", "Diferencia"])
        self.tabla.verticalHeader().setVisible(False)

        self.label_problemas = QLabel("")
        self.label_problemas.setWordWrap(True)

        botones = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        botones.rejected.connect(self.reject)
        botones.button(QDialogButtonBox.StandardButton.Close).clicked.connect(self.accept)

        root = QVBoxLayout(self)
        root.addLayout(form)
        root.addWidget(self.tabla, 1)
        root.addWidget(self.label_problemas)
        root.addWidget(botones)

        if self._es_vivo:
            self._timer = QTimer(self)
            self._timer.setInterval(2000)
            self._timer.timeout.connect(self.refrescar)
            self._timer.start()
        self.refrescar()

    def refrescar(self) -> None:
        try:
            estado = api_client.get_dispatch_status(self._dispatch_id)
        except Exception:
            return

        productos = estado["products"]
        self.tabla.setRowCount(len(productos))
        for fila, producto in enumerate(productos):
            diff = producto["diff"]
            color = _COLOR_OK if diff == 0 else (_COLOR_WARN if diff < 0 else _COLOR_FAIL)
            valores = [producto["name"], str(producto["expected"]), str(producto["read"]), str(diff)]
            for col, texto in enumerate(valores):
                item = QTableWidgetItem(texto)
                item.setBackground(color)
                self.tabla.setItem(fila, col, item)

        problemas = estado["problem_tags"]
        if problemas:
            textos = [f"{p['epc']} ({_RESULTADOS_PROBLEMA.get(p['result'], p['result'])})" for p in problemas]
            self.label_problemas.setText("Etiquetas problema: " + "; ".join(textos))
        else:
            self.label_problemas.setText("")
