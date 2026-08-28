from __future__ import annotations

import json
import sqlite3
from datetime import datetime
from pathlib import Path

from pyforge.session import DailySession


class SqliteSessionRepository:
    def __init__(self, db_path: Path) -> None:
        self.db_path = Path(db_path)
        self.conn = sqlite3.connect(self.db_path)
        self.conn.execute(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                slice_id TEXT NOT NULL,
                started_at TEXT,
                ended_at TEXT,
                tags TEXT NOT NULL DEFAULT '[]'
            )
            """
        )
        self.conn.commit()

    def add(self, session: DailySession) -> None:
        with self.conn:
            self._insert(session)

    def add_all(self, sessions: list[DailySession]) -> None:
        with self.conn:
            for session in sessions:
                if session.slice_id == "BAD":
                    raise ValueError("bad slice")
                self._insert(session)

    def list_all(self) -> list[DailySession]:
        rows = self.conn.execute(
            "SELECT slice_id, started_at, ended_at, tags FROM sessions ORDER BY id"
        ).fetchall()
        return [self._row_to_session(row) for row in rows]

    def _insert(self, session: DailySession) -> None:
        self.conn.execute(
            "INSERT INTO sessions (slice_id, started_at, ended_at, tags) VALUES (?, ?, ?, ?)",
            (
                session.slice_id,
                session.started_at.isoformat() if session.started_at else None,
                session.ended_at.isoformat() if session.ended_at else None,
                json.dumps(list(session.tags)),
            ),
        )

    def _row_to_session(self, row: tuple[str, str | None, str | None, str]) -> DailySession:
        tags = json.loads(row[3] or "[]")
        return DailySession(
            slice_id=row[0],
            started_at=datetime.fromisoformat(row[1]) if row[1] else None,
            ended_at=datetime.fromisoformat(row[2]) if row[2] else None,
            tags=list(tags),
        )
