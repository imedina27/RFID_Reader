class ApiError(Exception):
    """Error de regla de negocio o de validacion devuelto por la API.

    docs/api.md: el cuerpo del error siempre es {"error": codigo, "message": texto}.
    """

    def __init__(self, status_code: int, error: str, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.error = error
        self.message = message

    def to_dict(self) -> dict:
        return {"error": self.error, "message": self.message}


def not_found(message: str, error: str = "not_found") -> ApiError:
    return ApiError(404, error, message)


def bad_request(message: str, error: str = "invalid_request") -> ApiError:
    return ApiError(400, error, message)


def conflict(message: str, error: str) -> ApiError:
    return ApiError(409, error, message)
