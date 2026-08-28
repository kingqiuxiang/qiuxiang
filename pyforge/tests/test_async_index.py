import asyncio
import inspect

from pyforge.services.async_index import fetch_index


def test_fetch_index_sorted() -> None:
    rows = asyncio.run(fetch_index(["b", "a"]))
    assert [row["id"] for row in rows] == ["a", "b"]
    assert inspect.iscoroutinefunction(fetch_index)
