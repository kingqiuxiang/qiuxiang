from __future__ import annotations

from typing import Generic, TypeVar

T = TypeVar("T")


class Repository(Generic[T]):
    def __init__(self) -> None:
        self._items: list[T] = []

    def add(self, item: T) -> None:
        self._items.append(item)

    def list_all(self) -> list[T]:
        return list(self._items)


class MemoryRepository(Repository[T]):
    pass


class UnitOfWork(Generic[T]):
    def __init__(self, repo: Repository[T]) -> None:
        self.repo = repo
        self.committed = False

    def commit(self) -> None:
        self.committed = True
