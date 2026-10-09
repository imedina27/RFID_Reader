"""Dibuja el camión (SVG, vista superior) del panel en vivo del Tablero,
entre dos "banquetas" fijas (líneas amarillas) que simulan un carril. No
se ve al inicio: aparece con una animación simple de izquierda a derecha
(como entrando en pantalla) cuando se escanea el parabrisas, y mientras
`escaneando` es True se ven círculos de pulso sobre la caja de carga
(misma idea del "leyendo" que tenía la APK en la pantalla de conexión, se
quitó de ahí, se recicla la idea aquí). Al salir —de cualquier forma—
sigue de frente, en el mismo sentido que traía al entrar: hacia la
derecha, nunca en reversa (pedido del usuario, 2026-10-07).

Dos formas de mostrarlo:
- `mostrar_camion()` + `ocultar_camion()`: entra y se queda hasta que algo
  externo decida ocultarlo (lo usa Tablero mientras hay una salida real en
  curso, se está esperando que se confirmen boletas, o se congela el
  resultado final 5 s antes de limpiarse) — `ocultar_camion()` siempre
  anima la salida, nunca desaparece de golpe.
- `mostrar_temporalmente(segundos, mensaje)`: entra, se queda `segundos` y
  sale sola — para el caso "unidad sin boletas", con un letrero arriba del
  carril que desaparece justo cuando el camión ya salió de escena.

Todo el dibujo (camión, pulsos, letrero y animaciones) queda recortado a
este widget — nunca se monta sobre los cuadros vecinos."""

from __future__ import annotations

import time

from PyQt6.QtCore import QPointF, QRectF, Qt, QTimer
from PyQt6.QtGui import QColor, QFont, QPainter, QPen
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
_DURACION_SALIDA = 0.5  # segundos que tarda en salir, de frente hacia la derecha

# "Banquetas" del carril: fijas, no se animan con el camión. Un poco más
# largas que el camión a cada lado y separadas verticalmente de su silueta.
_MARGEN_CARRIL = 24
_MARGEN_VERTICAL_CARRIL = 18
_GROSOR_CARRIL = 4
_COLOR_CARRIL = QColor("#FFC107")

_ALTO_LETRERO = 22


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

        self._estado = "oculto"  # oculto | entrando | quieto | saliendo
        self._cambio_en = 0.0
        self._espera_antes_de_salir: float | None = None
        self._mensaje: str | None = None

        self._timer = QTimer(self)
        self._timer.setInterval(30)
        self._timer.timeout.connect(self._avanzar)
        self._timer.start()

    # ----------------------------------------------------------

    def mostrar_camion(self) -> None:
        """Entra y se queda. No hace nada si ya está visible (para no
        reiniciar la animación en cada refresco de 2 s del Tablero)."""
        if self._estado != "oculto":
            return
        self._estado = "entrando"
        self._cambio_en = time.monotonic()
        self._espera_antes_de_salir = None
        self._mensaje = None
        self.update()

    def mostrar_temporalmente(self, segundos_visible: float, mensaje: str | None = None) -> None:
        """Entra, se queda `segundos_visible` y sale sola. Igual de
        idempotente que `mostrar_camion()`."""
        if self._estado != "oculto":
            return
        self._estado = "entrando"
        self._cambio_en = time.monotonic()
        self._espera_antes_de_salir = segundos_visible
        self._mensaje = mensaje
        self.update()

    def ocultar_camion(self) -> None:
        """Sale de frente hacia la derecha (mismo sentido con el que entró)
        y queda oculto al terminar. No hace nada si ya está oculto o ya saliendo — se llama
        en cada refresco de 2 s del Tablero mientras no hay nada que
        mostrar, y no debe repetir la animación cada vez."""
        if self._estado in ("oculto", "saliendo"):
            return
        self._estado = "saliendo"
        self._cambio_en = time.monotonic()
        self._espera_antes_de_salir = None
        self.detener_escaneo()

    def esta_oculto(self) -> bool:
        return self._estado == "oculto"

    def iniciar_escaneo(self) -> None:
        self.escaneando = True

    def detener_escaneo(self) -> None:
        self.escaneando = False
        self._pulsos.clear()
        self.update()

    # ----------------------------------------------------------

    def _avanzar(self) -> None:
        ahora = time.monotonic()
        if self.escaneando and ahora - self._ultimo_pulso >= _INTERVALO_NUEVO_PULSO:
            self._pulsos.append(ahora)
            self._ultimo_pulso = ahora
        if self._pulsos:
            self._pulsos = [t for t in self._pulsos if ahora - t < _DURACION_PULSO]

        if self._estado == "entrando" and ahora - self._cambio_en >= _DURACION_ENTRADA:
            self._estado = "quieto"
            self._cambio_en = ahora
        if (
            self._estado == "quieto"
            and self._espera_antes_de_salir is not None
            and ahora - self._cambio_en >= self._espera_antes_de_salir
        ):
            self._estado = "saliendo"
            self._cambio_en = ahora
            self._espera_antes_de_salir = None
        if self._estado == "saliendo" and ahora - self._cambio_en >= _DURACION_SALIDA:
            self._estado = "oculto"
            self._mensaje = None
            self.detener_escaneo()

        if self._estado in ("entrando", "saliendo") or self._pulsos:
            self.update()

    def _renderer(self) -> QSvgRenderer:
        es_oscuro = StyleSheet.current_is_dark
        if es_oscuro not in self._renderers:
            ruta = (StyleSheet.dark_theme if es_oscuro else StyleSheet.light_theme)["truck_svg"]
            self._renderers[es_oscuro] = QSvgRenderer(ruta)
        return self._renderers[es_oscuro]

    def _color_texto(self) -> QColor:
        tema = StyleSheet.dark_theme if StyleSheet.current_is_dark else StyleSheet.light_theme
        return QColor(tema["text"])

    def _area_dibujo(self) -> tuple[QRectF, float, float, float]:
        escala = min(self.width() / _VIEWBOX_W, self.height() / _VIEWBOX_H) * _ESCALA_EXTRA
        ancho, alto = _VIEWBOX_W * escala, _VIEWBOX_H * escala
        x = (self.width() - ancho) / 2
        y = (self.height() - alto) / 2
        return QRectF(x, y, ancho, alto), escala, x, y

    def _dibujar_carril(self, painter: QPainter, area: QRectF) -> None:
        painter.setPen(QPen(_COLOR_CARRIL, _GROSOR_CARRIL))
        x1 = area.x() - _MARGEN_CARRIL
        x2 = area.x() + area.width() + _MARGEN_CARRIL
        y_arriba = area.y() - _MARGEN_VERTICAL_CARRIL
        y_abajo = area.y() + area.height() + _MARGEN_VERTICAL_CARRIL
        painter.drawLine(QPointF(x1, y_arriba), QPointF(x2, y_arriba))
        painter.drawLine(QPointF(x1, y_abajo), QPointF(x2, y_abajo))

    def _desplazamiento_x(self, area: QRectF) -> float:
        ahora = time.monotonic()
        if self._estado == "entrando":
            progreso = _ease_out(min(1.0, (ahora - self._cambio_en) / _DURACION_ENTRADA))
            x_inicial = -area.width()
            return (x_inicial - area.x()) * (1.0 - progreso)
        if self._estado == "saliendo":
            # sigue de frente, en el mismo sentido que traia al entrar: sale
            # por la derecha, nunca en reversa (pedido del usuario,
            # 2026-10-07: "cualquier tipo de salida").
            progreso = _ease_out(min(1.0, (ahora - self._cambio_en) / _DURACION_SALIDA))
            x_destino = float(self.width())
            return (x_destino - area.x()) * progreso
        return 0.0

    def paintEvent(self, event) -> None:  # noqa: N802 (API de Qt)
        if self.width() < 2 or self.height() < 2:
            return
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        area, escala, offset_x, offset_y = self._area_dibujo()

        self._dibujar_carril(painter, area)

        if self._estado == "oculto":
            painter.end()
            return

        desplazamiento_x = self._desplazamiento_x(area)
        area_animada = area.translated(desplazamiento_x, 0)
        self._renderer().render(painter, area_animada)

        if self._mensaje:
            fuente = QFont(painter.font())
            fuente.setBold(True)
            fuente.setPointSize(11)
            painter.setFont(fuente)
            painter.setPen(self._color_texto())
            rect_letrero = QRectF(
                area.x() - _MARGEN_CARRIL,
                area.y() - _MARGEN_VERTICAL_CARRIL - _ALTO_LETRERO,
                area.width() + 2 * _MARGEN_CARRIL,
                _ALTO_LETRERO,
            )
            painter.drawText(rect_letrero, Qt.AlignmentFlag.AlignCenter, self._mensaje)

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
