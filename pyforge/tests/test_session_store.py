from pathlib import Path

from pyforge.session import DailySession
from pyforge.session_store import load_session, save_session


def test_round_trip_json(tmp_path: Path) -> None:
    path = tmp_path / "w05.json"
    session = DailySession("W05").start()
    session.tags.append("json")
    save_session(path, session)
    loaded = load_session(path)
    assert loaded.slice_id == "W05"
    assert loaded.tags == ["json"]
    assert loaded.started_at is not None
    assert path.read_text(encoding="utf-8").startswith("{")
