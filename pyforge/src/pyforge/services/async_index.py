from __future__ import annotations

import asyncio


async def fetch_one(item_id: str) -> dict[str, str]:
    await asyncio.sleep(0)
    return {"id": item_id}


async def fetch_index(ids: list[str]) -> list[dict[str, str]]:
    rows = await asyncio.gather(*[fetch_one(item_id) for item_id in ids])
    return sorted(rows, key=lambda row: row["id"])
