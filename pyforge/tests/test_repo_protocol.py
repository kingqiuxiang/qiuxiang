from pathlib import Path

from pyforge.domain.repos import SessionRepository
from pyforge.json_repo import JsonSessionRepository
from pyforge.session import DailySession
from pyforge.sqlite_repo import SqliteSessionRepository


def _exercise(repo: SessionRepository) -> None:
    repo.add(DailySession("W09").start())
    assert [s.slice_id for s in repo.list_all()] == ["W09"]


def test_json_and_sqlite_share_protocol(tmp_path: Path) -> None:
    _exercise(JsonSessionRepository(tmp_path / "s.json"))
    _exercise(SqliteSessionRepository(tmp_path / "s.sqlite"))
