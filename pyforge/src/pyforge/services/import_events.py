from __future__ import annotations

import csv
import sqlite3
from pathlib import Path


class ImportFailed(Exception):
    pass


def import_learning_events(conn: sqlite3.Connection, csv_path: Path) -> int:
    rows: list[tuple[str, str, int, str]] = []
    with Path(csv_path).open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for index, raw in enumerate(reader, start=1):
            slice_id = (raw.get("slice_id") or "").strip()
            kind = (raw.get("kind") or "").strip()
            week_raw = (raw.get("week") or "").strip()
            payload = (raw.get("payload") or "").strip()
            if not slice_id or not kind or not week_raw.isdigit():
                raise ImportFailed(f"bad row {index}")
            rows.append((slice_id, kind, int(week_raw), payload))
    with conn:
        conn.executemany(
            "INSERT INTO learning_events (slice_id, kind, week, payload) VALUES (?, ?, ?, ?)",
            rows,
        )
    return len(rows)
