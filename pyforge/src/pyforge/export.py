from __future__ import annotations

import json
from collections.abc import Iterable, Iterator
from pathlib import Path

from pyforge.session import DailySession
from pyforge.session_store import session_to_dict


def iter_sessions(sessions: Iterable[DailySession]) -> Iterator[dict[str, object]]:
    for session in sessions:
        yield session_to_dict(session)


def export_sessions(path: Path, sessions: Iterable[DailySession]) -> None:
    dest = Path(path)
    tmp = dest.with_name(dest.name + ".tmp")
    payload = list(iter_sessions(sessions))
    tmp.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    tmp.replace(dest)
