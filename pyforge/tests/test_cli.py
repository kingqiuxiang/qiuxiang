from pathlib import Path

from pyforge.cli import main


def test_session_start_and_list(tmp_path: Path, capsys) -> None:
    store = tmp_path / "sessions.json"
    assert main(["session", "start", "W06"], store_path=store) == 0
    assert main(["session", "list"], store_path=store) == 0
    out = capsys.readouterr().out
    assert "W06" in out
