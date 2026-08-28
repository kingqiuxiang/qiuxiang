import sqlite3
from pathlib import Path

import pytest

from pyforge.services.import_events import ImportFailed, import_learning_events
from pyforge.services.schema import apply_schema


def test_good_csv_imports_all(tmp_path: Path) -> None:
    conn = sqlite3.connect(":memory:")
    apply_schema(conn)
    path = tmp_path / "ok.csv"
    path.write_text("slice_id,kind,week,payload\nW19,note,19,a\nW19,note,19,b\n", encoding="utf-8")
    assert import_learning_events(conn, path) == 2
    count = conn.execute("SELECT COUNT(*) FROM learning_events").fetchone()
    assert count is not None
    assert count[0] == 2


def test_dirty_csv_imports_nothing(tmp_path: Path) -> None:
    conn = sqlite3.connect(":memory:")
    apply_schema(conn)
    path = tmp_path / "bad.csv"
    path.write_text("slice_id,kind,week,payload\nW19,note,19,a\n,,nope,\n", encoding="utf-8")
    with pytest.raises(ImportFailed):
        import_learning_events(conn, path)
    count = conn.execute("SELECT COUNT(*) FROM learning_events").fetchone()
    assert count is not None
    assert count[0] == 0
