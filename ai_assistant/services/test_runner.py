from __future__ import annotations

import re
from typing import Any

import httpx

from ai_assistant.models import ApiTestResponse, EndpointSpec


TITLE_PATTERN = re.compile(r"<title>(.*?)</title>", re.IGNORECASE | re.DOTALL)


def _build_url(base_url: str, endpoint: EndpointSpec, path_values: dict[str, Any]) -> str:
    path = endpoint.path
    for key, value in path_values.items():
        path = path.replace(f"{{{key}}}", str(value)).replace(f":{key}", str(value))
    return f"{base_url.rstrip('/')}/{path.lstrip('/')}"


def run_api_test(
    base_url: str,
    endpoint: EndpointSpec,
    query: dict[str, Any],
    path: dict[str, Any],
    body: dict[str, Any],
    frontend_url: str | None = None,
) -> ApiTestResponse:
    request_url = _build_url(base_url, endpoint, path)

    with httpx.Client(timeout=30.0) as client:
        resp = client.request(method=endpoint.method.upper(), url=request_url, params=query, json=body or None)
        response_body: Any
        try:
            response_body = resp.json()
        except ValueError:
            response_body = resp.text

        frontend_status = None
        frontend_title = None
        if frontend_url:
            front = client.get(frontend_url)
            frontend_status = front.status_code
            match = TITLE_PATTERN.search(front.text)
            frontend_title = match.group(1).strip() if match else None

    return ApiTestResponse(
        request_url=str(resp.request.url),
        status_code=resp.status_code,
        response_body=response_body,
        frontend_status=frontend_status,
        frontend_title=frontend_title,
    )
