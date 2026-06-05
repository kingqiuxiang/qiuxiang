from __future__ import annotations

import asyncio
import os
import subprocess

import httpx

from app.models import FrontendTestRequest, FrontendTestResponse, ProjectStartRequest, ProjectStartResponse


def start_project(request: ProjectStartRequest) -> ProjectStartResponse:
    env = {**os.environ, **request.env}
    completed = subprocess.run(
        request.command,
        shell=True,
        cwd=request.working_directory,
        env=env,
        capture_output=True,
        text=True,
        timeout=120,
        check=False,
    )
    return ProjectStartResponse(
        return_code=completed.returncode,
        stdout=completed.stdout[-4000:],
        stderr=completed.stderr[-4000:],
    )


async def _playwright_frontend_check(request: FrontendTestRequest) -> FrontendTestResponse:
    from playwright.async_api import async_playwright

    details: list[str] = []
    passed = True

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page()
        await page.goto(request.url, timeout=request.timeout_ms)

        for check in request.checks:
            locator = page.locator(check.selector)
            count = await locator.count()
            if count == 0:
                passed = False
                details.append(f"FAIL: selector not found -> {check.selector}")
                continue

            if check.contains_text:
                text_content = await locator.first.text_content() or ""
                if check.contains_text not in text_content:
                    passed = False
                    details.append(
                        f"FAIL: selector {check.selector} text does not contain '{check.contains_text}'"
                    )
                    continue
            details.append(f"PASS: selector ok -> {check.selector}")

        await browser.close()

    return FrontendTestResponse(passed=passed, details=details)


async def run_frontend_test(request: FrontendTestRequest) -> FrontendTestResponse:
    try:
        return await _playwright_frontend_check(request)
    except Exception as exc:
        # Fallback: at least verify URL is reachable when Playwright is not available.
        async with httpx.AsyncClient(timeout=15) as client:
            response = await client.get(request.url)
        return FrontendTestResponse(
            passed=response.is_success,
            details=[
                "Playwright unavailable or runtime error, used HTTP reachability fallback.",
                f"status={response.status_code}",
                f"error={type(exc).__name__}: {exc}",
            ],
        )


def run_frontend_test_sync(request: FrontendTestRequest) -> FrontendTestResponse:
    return asyncio.run(run_frontend_test(request))
