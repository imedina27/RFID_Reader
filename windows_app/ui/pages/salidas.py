"""Pantalla Salidas a ruta: historial de salidas con filtros, detalle
(monitor en vivo si sigue en proceso) y "Unidad en planta" (docs/funcional.md,
sección 6). Windows no abre ni modifica salidas: todo lo crea y lo cierra la
APK; aquí solo se consulta y se marca el regreso del camión a planta."""

from email.utils import parsedate_to_datetime
from zoneinfo import ZoneInfo

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtWidgets import (
    QAbstractItemView,
    QComboBox,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QMessageBox,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

import db
from ui import api_client
from ui.dialogs.salida_detalle_dialog import SalidaDetalleDialog

COLUMNAS = ["Camión", "Folio(s)", "Cliente(s)", "Estado", "Tipo de salida", "Inicio", "Entregado"]

_TODOS = "Todos"
_TODAS = "Todas"
_ESTADOS_FILTRO = [_TODAS, "No ha salido", "En ruta", "Entregado", "Cancelada"]
_ZONA_LOCAL = ZoneInfo("America/Mexico_City")

_CLAUSULAS_ESTADO = {
    "No ha salido": "d.status = 'in_progress'",
    "En ruta": "d.status IN ('completed', 'completed_with_difference') AND d.delivered_at IS NULL",
    "Entregado": "d.status IN ('completed', 'completed_with_difference') AND d.delivered_at IS NOT NULL",
    "Cancelada": "d.status = 'cancelled'",
}


def _fecha_local(valor) -> str:
    if not valor:
        return ""
    try:
        if isinstance(valor, str):
            valor = parsedate_to_datetime(valor)
        return valor.astimezone(_ZONA_LOCAL).strftime("%d/%m/%Y %H:%M")
    except (TypeError, ValueError):
        return str(valor)


def _estado_texto(status: str, delivered_at) -> str:
    if status == "in_progress":
        return "No ha salido"
    if status == "cancelled":
        return "Cancelada"
    return "Entregado" if delivered_at else "En ruta"


def _tipo_salida_texto(status: str) -> str:
    if status == "completed":
        return "Normal"
    if status == "completed_with_difference":
        return "Con autorización"
    return "—"


class SalidasPage(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self._salidas: list[dict] = []

        layout = QVBoxLayout(self)

        header = QLabel("Salidas a ruta")
        header.setObjectName("section_header")
        layout.addWidget(header)

        filtros = QHBoxLayout()
        self.combo_estado = QComboBox()
        self.combo_estado.addItems(_ESTADOS_FILTRO)
        self.combo_estado.currentIndexChanged.connect(self.refrescar)
        filtros.addWidget(QLabel("Estado"))
        filtros.addWidget(self.combo_estado)

        self.combo_camion = QComboBox()
        self.combo_camion.addItem(_TODOS, None)
        for camion in api_client.list_trucks():
            self.combo_camion.addItem(camion["unit_number"], camion["id"])
        self.combo_camion.currentIndexChanged.connect(self.refrescar)
        filtros.addWidget(QLabel("Camión"))
        filtros.addWidget(self.combo_camion)

        with db.get_connection() as conn:
            clientes = conn.execute(
                "SELECT DISTINCT customer FROM exit_tickets ORDER BY customer"
            ).fetchall()
            folios = conn.execute("SELECT folio FROM exit_tickets ORDER BY folio").fetchall()

        self.combo_cliente = QComboBox()
        self.combo_cliente.addItem(_TODOS, None)
        for fila in clientes:
            self.combo_cliente.addItem(fila["customer"], fila["customer"])
        self.combo_cliente.currentIndexChanged.connect(self.refrescar)
        filtros.addWidget(QLabel("Cliente"))
        filtros.addWidget(self.combo_cliente)

        self.combo_folio = QComboBox()
        self.combo_folio.addItem(_TODOS, None)
        for fila in folios:
            self.combo_folio.addItem(fila["folio"], fila["folio"])
        self.combo_folio.currentIndexChanged.connect(self.refrescar)
        filtros.addWidget(QLabel("Folio"))
        filtros.addWidget(self.combo_folio)

        filtros.addStretch(1)
        layout.addLayout(filtros)

        barra = QHBoxLayout()
        self.btn_detalle = QPushButton("Ver detalle")
        self.btn_detalle.setObjectName("edit_button")
        self.btn_detalle.setEnabled(False)
        self.btn_detalle.clicked.connect(self._ver_detalle)
        barra.addWidget(self.btn_detalle)

        self.btn_en_planta = QPushButton("Unidad en planta")
        self.btn_en_planta.setObjectName("edit_button")
        self.btn_en_planta.setEnabled(False)
        self.btn_en_planta.clicked.connect(self._unidad_en_planta)
        barra.addWidget(self.btn_en_planta)

        barra.addStretch(1)
        layout.addLayout(barra)

        self.tabla = QTableWidget(0, len(COLUMNAS))
        self.tabla.setHorizontalHeaderLabels(COLUMNAS)
        self.tabla.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.tabla.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.tabla.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.tabla.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        self.tabla.verticalHeader().setVisible(False)
        self.tabla.setAlternatingRowColors(True)
        self.tabla.itemSelectionChanged.connect(self._actualizar_botones)
        self.tabla.doubleClicked.connect(self._ver_detalle)
        layout.addWidget(self.tabla, 1)

        self._timer = QTimer(self)
        self._timer.setInterval(2000)
        self._timer.timeout.connect(lambda: self.refrescar(silencioso=True))
        self._timer.start()
        self.refrescar()

    # ----------------------------------------------------------

    def refrescar(self, silencioso: bool = False) -> None:
        clausulas = ["1 = 1"]
        params: dict = {}

        estado_filtro = self.combo_estado.currentText()
        if estado_filtro in _CLAUSULAS_ESTADO:
            clausulas.append(_CLAUSULAS_ESTADO[estado_filtro])

        truck_id = self.combo_camion.currentData()
        if truck_id is not None:
            clausulas.append("d.truck_id = %(truck_id)s")
            params["truck_id"] = truck_id

        cliente = self.combo_cliente.currentData()
        if cliente is not None:
            clausulas.append(
                "EXISTS (SELECT 1 FROM dispatch_tickets dt JOIN exit_tickets et ON et.id = dt.ticket_id "
                "WHERE dt.dispatch_id = d.id AND et.customer = %(cliente)s)"
            )
            params["cliente"] = cliente

        folio = self.combo_folio.currentData()
        if folio is not None:
            clausulas.append(
                "EXISTS (SELECT 1 FROM dispatch_tickets dt JOIN exit_tickets et ON et.id = dt.ticket_id "
                "WHERE dt.dispatch_id = d.id AND et.folio = %(folio)s)"
            )
            params["folio"] = folio

        consulta = f"""
            SELECT d.id, d.status, d.started_at, d.finished_at, d.delivered_at,
                   d.authorized_by, d.authorization_reason, t.unit_number,
                   (SELECT string_agg(et.folio, ', ' ORDER BY et.folio)
                      FROM dispatch_tickets dt JOIN exit_tickets et ON et.id = dt.ticket_id
                     WHERE dt.dispatch_id = d.id) AS folios,
                   (SELECT string_agg(DISTINCT et.customer, ', ' ORDER BY et.customer)
                      FROM dispatch_tickets dt JOIN exit_tickets et ON et.id = dt.ticket_id
                     WHERE dt.dispatch_id = d.id) AS clientes
            FROM dispatches d
            JOIN trucks t ON t.id = d.truck_id
            WHERE {' AND '.join(clausulas)}
            ORDER BY d.started_at DESC
        """
        try:
            with db.get_connection() as conn:
                salidas = conn.execute(consulta, params).fetchall()
        except Exception as ex:
            if not silencioso:
                QMessageBox.warning(self, "No se pudo cargar", str(ex))
            return

        actual = self._fila_seleccionada()
        id_seleccionado = actual["id"] if actual is not None else None

        self._salidas = salidas
        self.tabla.setRowCount(len(salidas))
        for fila, salida in enumerate(salidas):
            valores = [
                salida["unit_number"],
                salida["folios"] or "",
                salida["clientes"] or "",
                _estado_texto(salida["status"], salida["delivered_at"]),
                _tipo_salida_texto(salida["status"]),
                _fecha_local(salida["started_at"]),
                _fecha_local(salida["delivered_at"]),
            ]
            for col, texto in enumerate(valores):
                item = QTableWidgetItem(texto)
                item.setFlags(item.flags() & ~Qt.ItemFlag.ItemIsEditable)
                self.tabla.setItem(fila, col, item)
            if salida["id"] == id_seleccionado:
                self.tabla.selectRow(fila)
        self._actualizar_botones()

    def _fila_seleccionada(self) -> dict | None:
        filas = self.tabla.selectionModel().selectedRows()
        if not filas:
            return None
        return self._salidas[filas[0].row()]

    def _actualizar_botones(self) -> None:
        salida = self._fila_seleccionada()
        self.btn_detalle.setEnabled(salida is not None)
        en_ruta = salida is not None and _estado_texto(salida["status"], salida["delivered_at"]) == "En ruta"
        self.btn_en_planta.setEnabled(en_ruta)

    # ----------------------------------------------------------

    def _ver_detalle(self) -> None:
        salida = self._fila_seleccionada()
        if salida is None:
            return
        datos = dict(salida)
        datos["estado_texto"] = _estado_texto(salida["status"], salida["delivered_at"])
        datos["tipo_salida_texto"] = _tipo_salida_texto(salida["status"])
        SalidaDetalleDialog(datos, parent=self).exec()

    def _unidad_en_planta(self) -> None:
        salida = self._fila_seleccionada()
        if salida is None:
            return
        try:
            api_client.deliver_dispatch(salida["id"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo marcar", ex.message)
            return
        self.refrescar()
