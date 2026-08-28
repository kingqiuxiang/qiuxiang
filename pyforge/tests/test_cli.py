from pathlib import Path

import pytest

from pyforge.cli import main
from pyforge.session_store import load_sessions


def test_session_start_and_list(tmp_path: Path, capsys) -> None:
    store = tmp_path / "sessions.json"
    assert main(["session", "start", "W06"], store_path=store) == 0
    assert main(["session", "list"], store_path=store) == 0
    out = capsys.readouterr().out
    assert "W06" in out


def test_session_end_closes_open(tmp_path: Path) -> None:
    store = tmp_path / "sessions.json"
    assert main(["session", "start", "W06"], store_path=store) == 0
    assert main(["session", "end"], store_path=store) == 0
    loaded = load_sessions(store)[0]
    assert loaded.ended_at is not None


def test_session_end_without_open_exits(tmp_path: Path) -> None:
    store = tmp_path / "empty.json"
    with pytest.raises(SystemExit, match="no open session"):
        main(["session", "end"], store_path=store)


def test_help_without_subcommand(capsys) -> None:
    assert main([]) == 0
    assert "session" in capsys.readouterr().out


def test_store_from_env(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    store = tmp_path / "env.json"
    monkeypatch.setenv("PYFORGE_STORE", str(store))
    assert main(["session", "start", "W06"]) == 0
    assert store.exists()
