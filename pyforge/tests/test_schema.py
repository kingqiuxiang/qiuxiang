import sqlite3

from pyforge.services.schema import apply_schema, table_names


def test_schema_is_idempotent() -> None:
    conn = sqlite3.connect(":memory:")
    apply_schema(conn)
    apply_schema(conn)
    names = table_names(conn)
    assert {"sessions", "slices", "gate_attempts", "learning_events"} <= names
