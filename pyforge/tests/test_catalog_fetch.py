import asyncio

import httpx
import pytest

from pyforge.services.catalog import fetch_catalog, fetch_catalog_async


def test_fetch_catalog_ok() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"weeks": 48})

    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        assert fetch_catalog("https://example.test/catalog", client=client)["weeks"] == 48


def test_fetch_catalog_cancel() -> None:
    async def run() -> None:
        async def handler(request: httpx.Request) -> httpx.Response:
            await asyncio.sleep(10)
            return httpx.Response(200, json={})

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            task = asyncio.create_task(fetch_catalog_async("https://example.test/c", client))
            await asyncio.sleep(0)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task

    asyncio.run(run())
