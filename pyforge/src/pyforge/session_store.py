from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path

from pyforge.session import DailySession


def session_to_dict(session: DailySession) -> dict[str, object]:
    return {
        "slice_id": session.slice_id,
        "started_at": session.started_at.isoformat() if session.started_at else None,
        "ended_at": session.ended_at.isoformat() if session.ended_at else None,
        "tags": list(session.tags),
    }


def session_from_dict(data: dict[str, object]) -> DailySession:
    started = data.get("started_at")
    ended = data.get("ended_at")
    tags = data.get("tags") or []
    if not isinstance(tags, list):
        raise ValueError("tags must be a list")
    return DailySession(
        slice_id=str(data["slice_id"]),
        started_at=datetime.fromisoformat(started) if isinstance(started, str) else None,
        ended_at=datetime.fromisoformat(ended) if isinstance(ended, str) else None,
        tags=[str(t) for t in tags],
    )


def save_session(path: Path, session: DailySession) -> None:
    path = Path(path)
    path.write_text(json.dumps(session_to_dict(session), ensure_ascii=False), encoding="utf-8")


def load_session(path: Path) -> DailySession:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("session json must be an object")
    return session_from_dict(data)


def save_sessions(path: Path, sessions: list[DailySession]) -> None:
    payload = [session_to_dict(s) for s in sessions]
    Path(path).write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


def load_sessions(path: Path) -> list[DailySession]:
    raw = Path(path).read_text(encoding="utf-8")
    data = json.loads(raw)
    if not isinstance(data, list):
        return [session_from_dict(data)]
    return [session_from_dict(item) for item in data if isinstance(item, dict)]
