from __future__ import annotations

from contextvars import ContextVar
from uuid import uuid4

request_id_var: ContextVar[str] = ContextVar("request_id", default="")


def bind_request_id(request_id: str | None = None) -> str:
    value = request_id or uuid4().hex
    request_id_var.set(value)
    return value


def current_request_id() -> str:
    return request_id_var.get()


def log_fields() -> dict[str, str]:
    return {"request_id": current_request_id()}
