import json
from pathlib import Path

from pyforge.export import export_sessions, iter_sessions
from pyforge.session import DailySession


def test_export_is_atomic_complete_file(tmp_path: Path) -> None:
    dest = tmp_path / "out.json"
    sessions = [DailySession("W08").start()]
    export_sessions(dest, sessions)
    data = json.loads(dest.read_text(encoding="utf-8"))
    assert data[0]["slice_id"] == "W08"
    assert list(iter_sessions(sessions))[0]["slice_id"] == "W08"
    assert not dest.with_name(dest.name + ".tmp").exists()
