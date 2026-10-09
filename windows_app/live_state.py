"""Estado efímero compartido entre el hilo de Flask y la ventana (mismo
proceso, ver main.py): el Tablero necesita saber "se acaba de escanear un
camión" aunque esa consulta (GET /dispatch/lookup) no guarde nada en la
base de datos. No se persiste nunca: se pierde si se reinicia la app, y es
correcto que así sea (es solo para el panel en vivo)."""

import threading
import time

_lock = threading.Lock()
_ultimo_escaneo: dict | None = None

# Si no se abre una salida en este tiempo tras escanear el parabrisas, el
# Tablero deja de mostrarlo (se trata como abandonado).
VIGENCIA_SEGUNDOS = 60


def marcar_escaneo(truck: dict, sin_boletas: bool = False) -> None:
    global _ultimo_escaneo
    with _lock:
        _ultimo_escaneo = {
            "truck": truck,
            "sin_boletas": sin_boletas,
            "escaneado_en": time.monotonic(),
        }


def obtener_escaneo() -> dict | None:
    with _lock:
        if _ultimo_escaneo is None:
            return None
        if time.monotonic() - _ultimo_escaneo["escaneado_en"] > VIGENCIA_SEGUNDOS:
            return None
        return dict(_ultimo_escaneo)


def limpiar_escaneo() -> None:
    global _ultimo_escaneo
    with _lock:
        _ultimo_escaneo = None
