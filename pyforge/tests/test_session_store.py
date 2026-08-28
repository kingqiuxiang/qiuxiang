from pathlib import Path

import pytest

from pyforge.session import DailySession
from pyforge.session_store import load_session, load_sessions, save_session, session_from_dict


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


def test_load_sessions_accepts_single_object(tmp_path: Path) -> None:
    path = tmp_path / "one.json"
    save_session(path, DailySession("W05"))
    rows = load_sessions(path)
    assert rows[0].slice_id == "W05"


def test_session_from_dict_rejects_bad_tags() -> None:
    with pytest.raises(ValueError, match="list"):
        session_from_dict({"slice_id": "W05", "tags": "gil"})
