"""Cliente HTTP de la API local (docs/api.md), usado por las pantallas de la
interfaz. La API corre en el mismo proceso (hilo secundario, ver main.py)."""

import requests

BASE_URL = "http://127.0.0.1:5000/api"
TIMEOUT = 5


class ApiError(Exception):
    def __init__(self, error: str, message: str):
        super().__init__(message)
        self.error = error
        self.message = message


def _handle(resp: requests.Response):
    if resp.status_code >= 400:
        try:
            body = resp.json()
        except ValueError:
            body = {}
        raise ApiError(body.get("error", "error"), body.get("message", resp.text))
    if resp.status_code == 204 or not resp.content:
        return None
    return resp.json()


def list_products() -> list[dict]:
    return _handle(requests.get(f"{BASE_URL}/products", timeout=TIMEOUT))


def create_product(code: str, name: str, presentation: str | None) -> dict:
    body = {"code": code, "name": name, "presentation": presentation}
    return _handle(requests.post(f"{BASE_URL}/products", json=body, timeout=TIMEOUT))


def update_product(product_id: int, **campos) -> dict:
    return _handle(requests.put(f"{BASE_URL}/products/{product_id}", json=campos, timeout=TIMEOUT))
