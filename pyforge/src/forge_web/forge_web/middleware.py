from __future__ import annotations

from collections.abc import Callable

from django.http import HttpRequest, HttpResponse

from forge_web.metrics import bump
from pyforge.services.log_context import bind_request_id


class RequestIdMiddleware:
    def __init__(self, get_response: Callable[[HttpRequest], HttpResponse]) -> None:
        self.get_response = get_response

    def __call__(self, request: HttpRequest) -> HttpResponse:
        bind_request_id()
        return self.get_response(request)


class MetricsMiddleware:
    def __init__(self, get_response: Callable[[HttpRequest], HttpResponse]) -> None:
        self.get_response = get_response

    def __call__(self, request: HttpRequest) -> HttpResponse:
        bump()
        return self.get_response(request)
