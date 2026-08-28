from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class KnowledgeSlice:
    slice_id: str
    title: str
    tags: tuple[str, ...]


class SliceRegistry:
    def __init__(self) -> None:
        self._items: list[KnowledgeSlice] = []

    def add(self, item: KnowledgeSlice) -> None:
        self._items.append(item)

    def by_tag(self, tag: str) -> list[KnowledgeSlice]:
        return [s for s in self._items if tag in s.tags]
