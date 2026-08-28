from pathlib import Path

from pyforge.session import DailySession
from pyforge.sqlite_repo import SqliteSessionRepository


def test_add_and_list(tmp_path: Path) -> None:
    repo = SqliteSessionRepository(tmp_path / "db.sqlite")
    repo.add(DailySession("W07a").start())
    repo.add(DailySession("W07b").start())
    assert [s.slice_id for s in repo.list_all()] == ["W07a", "W07b"]


def test_add_all_rolls_back(tmp_path: Path) -> None:
    repo = SqliteSessionRepository(tmp_path / "db.sqlite")
    try:
        repo.add_all([DailySession("ok").start(), DailySession("BAD").start()])
    except ValueError:
        pass
    else:
        raise AssertionError("expected ValueError")
    assert repo.list_all() == []
