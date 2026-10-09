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


def list_trucks() -> list[dict]:
    return _handle(requests.get(f"{BASE_URL}/trucks", timeout=TIMEOUT))


def create_truck(unit_number: str, plate: str | None, driver: str | None) -> dict:
    body = {"unit_number": unit_number, "plate": plate, "driver": driver}
    return _handle(requests.post(f"{BASE_URL}/trucks", json=body, timeout=TIMEOUT))


def update_truck(truck_id: int, **campos) -> dict:
    return _handle(requests.put(f"{BASE_URL}/trucks/{truck_id}", json=campos, timeout=TIMEOUT))


def list_tags(**filtros) -> list[dict]:
    return _handle(requests.get(f"{BASE_URL}/tags", params=filtros, timeout=TIMEOUT))


def assign_truck_tag(truck_id: int, epc: str) -> dict:
    body = {"truck_id": truck_id, "epc": epc}
    return _handle(requests.post(f"{BASE_URL}/tags/truck", json=body, timeout=TIMEOUT))


def delete_tag(epc: str) -> None:
    return _handle(requests.delete(f"{BASE_URL}/tags/{epc}", timeout=TIMEOUT))


def capture_pallet_tag(product_id: int, epc: str) -> dict:
    body = {"product_id": product_id, "epcs": [epc]}
    return _handle(requests.post(f"{BASE_URL}/tags/batch", json=body, timeout=TIMEOUT))


def update_tag_product(epc: str, product_id: int) -> dict:
    body = {"product_id": product_id}
    return _handle(requests.put(f"{BASE_URL}/tags/{epc}", json=body, timeout=TIMEOUT))


def reset_delivered_tags() -> dict:
    return _handle(requests.post(f"{BASE_URL}/tags/reset-delivered", timeout=TIMEOUT))


def list_exit_tickets(**filtros) -> list[dict]:
    return _handle(requests.get(f"{BASE_URL}/exit-tickets", params=filtros, timeout=TIMEOUT))


def create_exit_ticket(customer: str, truck_id: int | None, lines: list[dict]) -> dict:
    body = {"customer": customer, "truck_id": truck_id, "lines": lines}
    return _handle(requests.post(f"{BASE_URL}/exit-tickets", json=body, timeout=TIMEOUT))


def update_exit_ticket(ticket_id: int, **campos) -> dict:
    return _handle(requests.put(f"{BASE_URL}/exit-tickets/{ticket_id}", json=campos, timeout=TIMEOUT))


def assign_exit_ticket_truck(ticket_id: int, truck_id: int) -> dict:
    body = {"truck_id": truck_id}
    return _handle(
        requests.post(f"{BASE_URL}/exit-tickets/{ticket_id}/assign-truck", json=body, timeout=TIMEOUT)
    )


def cancel_exit_ticket(ticket_id: int) -> dict:
    return _handle(requests.post(f"{BASE_URL}/exit-tickets/{ticket_id}/cancel", timeout=TIMEOUT))


def get_dispatch_status(dispatch_id: int) -> dict:
    return _handle(requests.get(f"{BASE_URL}/dispatches/{dispatch_id}/status", timeout=TIMEOUT))


def list_alarms(**filtros) -> list[dict]:
    return _handle(requests.get(f"{BASE_URL}/alarms", params=filtros, timeout=TIMEOUT))


def ack_alarm(alarm_id: int, acknowledged_by: str) -> dict:
    body = {"acknowledged_by": acknowledged_by}
    return _handle(requests.post(f"{BASE_URL}/alarms/{alarm_id}/ack", json=body, timeout=TIMEOUT))


def deliver_dispatch(dispatch_id: int) -> dict:
    return _handle(requests.post(f"{BASE_URL}/dispatches/{dispatch_id}/deliver", timeout=TIMEOUT))


def list_epc_prefixes() -> list[dict]:
    return _handle(requests.get(f"{BASE_URL}/epc-prefixes", timeout=TIMEOUT))


def create_epc_prefix(prefix: str) -> dict:
    return _handle(requests.post(f"{BASE_URL}/epc-prefixes", json={"prefix": prefix}, timeout=TIMEOUT))


def delete_epc_prefix(prefix_id: int) -> None:
    return _handle(requests.delete(f"{BASE_URL}/epc-prefixes/{prefix_id}", timeout=TIMEOUT))
