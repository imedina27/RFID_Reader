"""Dibuja el camión (SVG, vista superior) del panel en vivo del Tablero.
No se ve al inicio: aparece con una animación simple de izquierda a
derecha (como entrando en pantalla) cuando se escanea el parabrisas —
`mostrar_camion()` — y mientras `escaneando` es True se ven círculos de
pulso sobre la caja de carga (misma idea del "leyendo" que tenía la APK en
la pantalla de conexión, se quitó de ahí, se recicla la idea aquí). Todo
el dibujo (camión, pulsos y la animación de entrada) queda recortado a
este widget — nunca se monta sobre los cuadros vecinos."""

from __future__ import annotations

import time

from PyQt6.QtCore import QPointF, QRectF, Qt, QTimer
from PyQt6.QtGui import QColor, QPainter
from PyQt6.QtSvg import QSvgRenderer
from PyQt6.QtWidgets import QWidget

from ui.styles.stylesheet import StyleSheet

# Tamaño de viewBox y área de carga marcada ("marcador") del SVG original.
_VIEWBOX_W = 728.0
_VIEWBOX_H = 152.0
_MARCADOR = QRectF(30.22, 25.70, 513.56, 100.39)

_DURACION_PULSO = 1.4  # segundos que tarda un círculo en crecer y desvanecerse
_INTERVALO_NUEVO_PULSO = 0.5  # segundos entre un círculo nuevo y el siguiente
_COLOR_PULSO = QColor("#8B5CF6")  # morado "leyendo" (mismo que ya usa la APK)

# Ajuste fino de tamaño a ojo (pedido del usuario, 2026-10-07): un poco
# más chico que a todo lo ancho/alto disponible.
_ESCALA_EXTRA = 0.8

_DURACION_ENTRADA = 0.5  # segundos que tarda en entrar de izquierda a derecha


def _ease_out(progreso: float) -> float:
    return 1.0 - (1.0 - progreso) ** 2


class CamionVivoWidget(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setMinimumHeight(110)
        self._renderers: dict[bool, QSvgRenderer] = {}
        self.escaneando = False
        self._pulsos: list[float] = []
        self._ultimo_pulso = 0.0
        self._visible = False
        self._entrada_inicio: float | None = None

        self._timer = QTimer(self)
        self._timer.setInterval(30)
        self._timer.timeout.connect(self._avanzar)
        self._timer.start()

    # ----------------------------------------------------------

    def mostrar_camion(self) -> None:
        """Dispara la animación de entrada. No hace nada si ya está visible
        (para no reiniciarla en cada refresco de 2 s del Tablero)."""
        if self._visible:
            return
        self._visible = True
        self._entrada_inicio = time.monotonic()
        self.update()

    def ocultar_camion(self) -> None:
        self._visible = False
        self._entrada_inicio = None
        self.detener_escaneo()

    def iniciar_escaneo(self) -> None:
        self.escaneando = True

    def detener_escaneo(self) -> None:
        self.escaneando = False
        self._pulsos.clear()
        self.update()

    # ----------------------------------------------------------

    def _progreso_entrada(self) -> float:
        if not self._visible:
            return 0.0
        if self._entrada_inicio is None:
            return 1.0
        progreso = (time.monotonic() - self._entrada_inicio) / _DURACION_ENTRADA
        return min(1.0, progreso)

    def _avanzar(self) -> None:
        ahora = time.monotonic()
        if self.escaneando and ahora - self._ultimo_pulso >= _INTERVALO_NUEVO_PULSO:
            self._pulsos.append(ahora)
            self._ultimo_pulso = ahora
        if self._pulsos:
            self._pulsos = [t for t in self._pulsos if ahora - t < _DURACION_PULSO]
        entrando = self._visible and self._progreso_entrada() < 1.0
        if self._pulsos or entrando:
            self.update()

    def _renderer(self) -> QSvgRenderer:
        es_oscuro = StyleSheet.current_is_dark
        if es_oscuro not in self._renderers:
            ruta = (StyleSheet.dark_theme if es_oscuro else StyleSheet.light_theme)["truck_svg"]
            self._renderers[es_oscuro] = QSvgRenderer(ruta)
        return self._renderers[es_oscuro]

    def _area_dibujo(self) -> tuple[QRectF, float, float, float]:
        escala = min(self.width() / _VIEWBOX_W, self.height() / _VIEWBOX_H) * _ESCALA_EXTRA
        ancho, alto = _VIEWBOX_W * escala, _VIEWBOX_H * escala
        x = (self.width() - ancho) / 2
        y = (self.height() - alto) / 2
        return QRectF(x, y, ancho, alto), escala, x, y

    def paintEvent(self, event) -> None:  # noqa: N802 (API de Qt)
        if not self._visible or self.width() < 2 or self.height() < 2:
            return
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        area, escala, offset_x, offset_y = self._area_dibujo()

        progreso = _ease_out(self._progreso_entrada())
        x_inicial = -area.width()  # fuera de pantalla, a la izquierda
        desplazamiento_x = (x_inicial - area.x()) * (1.0 - progreso)
        area_animada = area.translated(desplazamiento_x, 0)

        self._renderer().render(painter, area_animada)

        if self._pulsos:
            centro = QPointF(
                offset_x + desplazamiento_x + (_MARCADOR.x() + _MARCADOR.width() / 2) * escala,
                offset_y + (_MARCADOR.y() + _MARCADOR.height() / 2) * escala,
            )
            radio_max = min(_MARCADOR.width(), _MARCADOR.height()) * escala / 2
            ahora = time.monotonic()
            painter.setPen(Qt.PenStyle.NoPen)
            for nacimiento in self._pulsos:
                prog = (ahora - nacimiento) / _DURACION_PULSO
                color = QColor(_COLOR_PULSO)
                color.setAlphaF(max(0.0, 1.0 - prog) * 0.6)
                painter.setBrush(color)
                painter.drawEllipse(centro, radio_max * prog, radio_max * prog)
        painter.end()
