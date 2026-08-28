import asyncio

import httpx
import pytest

from pyforge.services.catalog import (
    cancel_fetch,
    fetch_catalog,
    fetch_catalog_async,
    fetch_catalog_cancellable,
)


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


def test_fetch_catalog_rejects_array() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=[1, 2])

    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(ValueError, match="object"):
            fetch_catalog("https://example.test/catalog", client=client)


def test_fetch_catalog_cancellable_ok() -> None:
    async def run() -> None:
        async def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, json={"n": 1})

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            payload = await fetch_catalog_cancellable("https://example.test/c", client)
            assert payload["n"] == 1

    asyncio.run(run())


def test_fetch_catalog_owns_and_closes_client(monkeypatch: pytest.MonkeyPatch) -> None:
    closed = {"n": 0}

    class FakeResp:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict[str, object]:
            return {"owned": True}

    class FakeClient:
        def __init__(self, timeout: float = 0.5) -> None:
            self.timeout = timeout

        def get(self, url: str) -> FakeResp:
            return FakeResp()

        def close(self) -> None:
            closed["n"] += 1

    monkeypatch.setattr("pyforge.services.catalog.httpx.Client", FakeClient)
    assert fetch_catalog("https://example.test/c")["owned"] is True
    assert closed["n"] == 1


def test_fetch_catalog_async_rejects_array() -> None:
    async def run() -> None:
        async def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, json=[1])

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            with pytest.raises(ValueError, match="object"):
                await fetch_catalog_async("https://example.test/c", client)

    asyncio.run(run())


def test_cancel_fetch_cancels_task() -> None:
    async def run() -> None:
        async def sleeper() -> None:
            await asyncio.sleep(10)

        task = asyncio.create_task(sleeper())
        cancel_fetch(task)
        with pytest.raises(asyncio.CancelledError):
            await task

    asyncio.run(run())
