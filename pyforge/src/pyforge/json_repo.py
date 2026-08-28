from __future__ import annotations

from pathlib import Path

from pyforge.session import DailySession
from pyforge.session_store import load_sessions, save_sessions


class JsonSessionRepository:
    def __init__(self, path: Path) -> None:
        self.path = Path(path)

    def add(self, session: DailySession) -> None:
        items = self.list_all()
        items.append(session)
        save_sessions(self.path, items)

    def list_all(self) -> list[DailySession]:
        if not self.path.exists():
            return []
        return load_sessions(self.path)
