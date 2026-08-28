from pathlib import Path

from pyforge.session import DailySession
from pyforge.services.store import persist


def test_persist_round_trip(tmp_path: Path) -> None:
    path = tmp_path / "session.json"
    session = DailySession("W05").start()
    session.tags.append("store")
    loaded = persist(path, session)
    assert loaded.slice_id == "W05"
    assert loaded.tags == ["store"]
    assert loaded.started_at is not None
