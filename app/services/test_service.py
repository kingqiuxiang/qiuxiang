from __future__ import annotations

import time
from typing import Any

import httpx

from app.models import APITestRequest, APITestResponse


def _extract_by_path(data: Any, path: str) -> Any:
    current = data
    for token in path.split("."):
        if isinstance(current, dict):
            current = current.get(token)
        else:
            return None
    return current


async def run_api_test(request: APITestRequest) -> APITestResponse:
    url = f"{request.base_url.rstrip('/')}/{request.path.lstrip('/')}"
    start = time.time()

    async with httpx.AsyncClient(timeout=30) as client:
        response = await client.request(
            method=request.method.upper(),
            url=url,
            headers=request.headers,
            params=request.params,
            json=request.body if request.body else None,
        )

    elapsed_ms = int((time.time() - start) * 1000)
    try:
        body = response.json()
    except ValueError:
        body = response.text

    assertion_results: list[str] = []
    all_passed = True
    for rule in request.assertions:
        value = _extract_by_path(body, rule.path)
        passed = value == rule.equals
        if not passed:
            all_passed = False
        assertion_results.append(
            f"{'PASS' if passed else 'FAIL'}: {rule.path} expected={rule.equals} actual={value}"
        )

    return APITestResponse(
        status_code=response.status_code,
        ok=response.is_success and all_passed,
        elapsed_ms=elapsed_ms,
        response_body=body,
        assertion_results=assertion_results,
    )
