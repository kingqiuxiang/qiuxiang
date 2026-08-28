from __future__ import annotations

import sqlite3


class DuplicateAttempt(Exception):
    pass


def record_attempt(
    conn: sqlite3.Connection, gate_name: str, slice_id: str, ok: bool, error: str | None = None
) -> None:
    try:
        with conn:
            conn.execute(
                "INSERT INTO gate_attempts (gate_name, slice_id, ok, error) VALUES (?, ?, ?, ?)",
                (gate_name, slice_id, 1 if ok else 0, error),
            )
    except sqlite3.IntegrityError as err:
        raise DuplicateAttempt(f"{gate_name}:{slice_id}") from err


def list_attempts(conn: sqlite3.Connection) -> list[tuple[str, str, int]]:
    rows = conn.execute("SELECT gate_name, slice_id, ok FROM gate_attempts ORDER BY id").fetchall()
    return [(str(r[0]), str(r[1]), int(r[2])) for r in rows]
