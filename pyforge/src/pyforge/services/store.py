from __future__ import annotations

from pathlib import Path

from pyforge.session import DailySession
from pyforge.session_store import load_session, save_session


def persist(path: Path, session: DailySession) -> DailySession:
    save_session(path, session)
    return load_session(path)
