from __future__ import annotations

from typing import Protocol

from pyforge.session import DailySession


class SessionPlugin(Protocol):
    name: str

    def on_session_stop(self, session: DailySession) -> None: ...
