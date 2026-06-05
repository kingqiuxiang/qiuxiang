from __future__ import annotations

import time
from typing import Any
from urllib.parse import urljoin

import httpx


def run_api_test(
    base_url: str,
    path: str,
    method: str,
    query: dict[str, Any],
    headers: dict[str, Any],
    body: dict[str, Any],
    timeout_seconds: int,
) -> dict[str, Any]:
    url = urljoin(base_url.rstrip("/") + "/", path.lstrip("/"))
    start = time.time()
    try:
        response = httpx.request(
            method=method.upper(),
            url=url,
            params=query,
            headers=headers,
            json=body if method.upper() in {"POST", "PUT", "PATCH", "DELETE"} else None,
            timeout=timeout_seconds,
        )
        elapsed_ms = int((time.time() - start) * 1000)
        content_type = response.headers.get("content-type", "")
        payload: Any
        if "application/json" in content_type:
            payload = response.json()
        else:
            payload = response.text[:3000]
        return {
            "ok": response.is_success,
            "status_code": response.status_code,
            "elapsed_ms": elapsed_ms,
            "response": payload,
            "url": str(response.request.url),
        }
    except httpx.HTTPError as exc:
        return {"ok": False, "error": str(exc), "url": url}


def run_ui_smoke_test(url: str, wait_ms: int = 3500) -> dict[str, Any]:
    try:
        from playwright.sync_api import sync_playwright
    except Exception:
        try:
            response = httpx.get(url, timeout=12)
            return {
                "ok": response.status_code < 400,
                "mode": "http_fallback",
                "status_code": response.status_code,
                "note": "playwright 未安装，已降级为页面可访问性检查。",
            }
        except httpx.HTTPError as exc:
            return {"ok": False, "mode": "http_fallback", "error": str(exc)}

    console_errors: list[str] = []
    page_errors: list[str] = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.on("console", lambda msg: console_errors.append(msg.text) if msg.type == "error" else None)
        page.on("pageerror", lambda exc: page_errors.append(str(exc)))
        response = page.goto(url, wait_until="networkidle", timeout=15000)
        page.wait_for_timeout(wait_ms)
        title = page.title()
        browser.close()
    return {
        "ok": response is not None and response.status < 400 and not page_errors,
        "mode": "playwright",
        "status_code": response.status if response else None,
        "title": title,
        "console_errors": console_errors[:15],
        "page_errors": page_errors[:15],
    }
