from __future__ import annotations

import asyncio

import httpx


def fetch_catalog(url: str, client: httpx.Client | None = None) -> dict[str, object]:
    owned = client is None
    http = client or httpx.Client(timeout=0.5)
    try:
        response = http.get(url)
        response.raise_for_status()
        payload = response.json()
        if not isinstance(payload, dict):
            raise ValueError("catalog must be an object")
        return payload
    finally:
        if owned:
            http.close()


async def fetch_catalog_async(url: str, client: httpx.AsyncClient) -> dict[str, object]:
    response = await client.get(url)
    response.raise_for_status()
    payload = response.json()
    if not isinstance(payload, dict):
        raise ValueError("catalog must be an object")
    return payload


async def fetch_catalog_cancellable(url: str, client: httpx.AsyncClient) -> dict[str, object]:
    return await fetch_catalog_async(url, client)


def cancel_fetch(task: asyncio.Task[object]) -> None:
    task.cancel()
