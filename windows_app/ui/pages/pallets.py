"""Pantalla Pallets: lista de las etiquetas de pallet ya capturadas (las que
salieron de la línea de producción), más reciente arriba, con captura manual
y corrección del producto (docs/funcional.md, secciones 5 y 6). Las
etiquetas de camión no aparecen aquí: esas se administran en Camiones. Se
refresca sola cada 2 s para que una captura nueva desde la APK aparezca sin
tener que hacer nada."""

from email.utils import parsedate_to_datetime
from zoneinfo import ZoneInfo

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtWidgets import (
    QAbstractItemView,
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

from ui import api_client
from ui.dialogs.pallet_dialog import PalletDialog

COLUMNAS = ["Fecha", "EPC", "Folio", "Producto", "Estado"]

_ESTADOS = {"captured": "Capturada", "dispatched": "Despachada"}
_ZONA_LOCAL = ZoneInfo("America/Mexico_City")


def _fecha_local(valor: str | None) -> str:
    if not valor:
        return ""
    try:
        return parsedate_to_datetime(valor).astimezone(_ZONA_LOCAL).strftime("%d/%m/%Y %H:%M")
    except (TypeError, ValueError):
        return valor


class PalletsPage(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self._pallets: list[dict] = []

        layout = QVBoxLayout(self)

        header = QLabel("Pallets")
        header.setObjectName("section_header")
        layout.addWidget(header)

        barra = QHBoxLayout()
        self.btn_agregar = QPushButton("+ Agregar")
        self.btn_agregar.setObjectName("add_button")
        self.btn_agregar.clicked.connect(self._agregar)
        barra.addWidget(self.btn_agregar)

        self.btn_editar = QPushButton("Editar")
        self.btn_editar.setObjectName("edit_button")
        self.btn_editar.setEnabled(False)
        self.btn_editar.clicked.connect(self._editar)
        barra.addWidget(self.btn_editar)

        barra.addSpacing(400)  # separado a propósito: no es una acción más

        self.btn_limpiar_estado = QPushButton("Limpiar Estado")
        self.btn_limpiar_estado.setObjectName("edit_button")
        self.btn_limpiar_estado.clicked.connect(self._limpiar_estado)
        barra.addWidget(self.btn_limpiar_estado)

        barra.addStretch(1)
        layout.addLayout(barra)

        self.tabla = QTableWidget(0, len(COLUMNAS))
        self.tabla.setHorizontalHeaderLabels(COLUMNAS)
        self.tabla.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.tabla.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.tabla.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.tabla.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        self.tabla.verticalHeader().setVisible(False)
        self.tabla.setAlternatingRowColors(True)
        self.tabla.itemSelectionChanged.connect(self._actualizar_botones)
        self.tabla.doubleClicked.connect(self._editar)
        layout.addWidget(self.tabla, 1)

        self._timer = QTimer(self)
        self._timer.setInterval(2000)
        self._timer.timeout.connect(lambda: self.refrescar(silencioso=True))
        self._timer.start()
        self.refrescar()

    # ----------------------------------------------------------

    def refrescar(self, silencioso: bool = False) -> None:
        try:
            pallets = api_client.list_tags(kind="pallet")
            productos = {p["id"]: p["name"] for p in api_client.list_products()}
        except Exception as ex:
            if not silencioso:
                QMessageBox.warning(self, "No se pudo cargar", str(ex))
            return

        actual = self._fila_seleccionada()
        epc_seleccionado = actual["epc"] if actual is not None else None

        self._pallets = pallets
        self.tabla.setRowCount(len(pallets))
        for fila, pallet in enumerate(pallets):
            valores = [
                _fecha_local(pallet["captured_at"]),
                pallet["epc"],
                pallet.get("folio") or "",
                productos.get(pallet["product_id"], ""),
                _ESTADOS.get(pallet["status"], pallet["status"]),
            ]
            for col, texto in enumerate(valores):
                item = QTableWidgetItem(texto)
                item.setFlags(item.flags() & ~Qt.ItemFlag.ItemIsEditable)
                self.tabla.setItem(fila, col, item)
            if pallet["epc"] == epc_seleccionado:
                self.tabla.selectRow(fila)
        self._actualizar_botones()

    def _fila_seleccionada(self) -> dict | None:
        filas = self.tabla.selectionModel().selectedRows()
        if not filas:
            return None
        return self._pallets[filas[0].row()]

    def _actualizar_botones(self) -> None:
        pallet = self._fila_seleccionada()
        self.btn_editar.setEnabled(pallet is not None)

    # ----------------------------------------------------------

    def _agregar(self) -> None:
        datos = PalletDialog.pedir(parent=self)
        if datos is None:
            return
        try:
            resultado = api_client.capture_pallet_tag(datos["product_id"], datos["epc"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo capturar", ex.message)
            return
        resultado_epc = resultado["results"][0]
        if resultado_epc["result"] != "created":
            QMessageBox.warning(
                self, "No se capturó",
                f"Esa etiqueta ya existe ({resultado_epc['result']}).",
            )
        self.refrescar()

    def _editar(self) -> None:
        pallet = self._fila_seleccionada()
        if pallet is None:
            return
        datos = PalletDialog.pedir(pallet, parent=self)
        if datos is None:
            return
        try:
            api_client.update_tag_product(pallet["epc"], datos["product_id"])
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo guardar", ex.message)
            return
        self.refrescar()

    def _limpiar_estado(self) -> None:
        respuesta = QMessageBox.question(
            self, "Limpiar Estado",
            "¿Seguro que quieres reiniciar el estado de los pallets ya entregados?\n\n"
            "Los pallets de una salida ya \"Entregada\" vuelven a \"Capturada\" para "
            "poder reutilizar las mismas etiquetas en otro ensayo. Los que siguen "
            "\"En ruta\" o no han salido no se tocan.",
        )
        if respuesta != QMessageBox.StandardButton.Yes:
            return
        try:
            resultado = api_client.reset_delivered_tags()
        except api_client.ApiError as ex:
            QMessageBox.warning(self, "No se pudo limpiar", ex.message)
            return
        QMessageBox.information(
            self, "Limpiar Estado",
            f"{resultado['reset_count']} pallet(s) regresaron a \"Capturada\".",
        )
        self.refrescar()
