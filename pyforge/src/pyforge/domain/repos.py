from __future__ import annotations

from typing import Protocol

from pyforge.session import DailySession


class SessionRepository(Protocol):
    def add(self, session: DailySession) -> None: ...

    def list_all(self) -> list[DailySession]: ...
