from __future__ import annotations

from typing import Any

from forge_web.models import Session


def backup_sessions() -> list[dict[str, Any]]:
    return list(Session.objects.all().values("slice_id", "started_at", "ended_at", "tags"))


def restore_sessions(payload: list[dict[str, Any]]) -> None:
    Session.objects.all().delete()
    Session.objects.bulk_create(
        [
            Session(
                slice_id=str(row["slice_id"]),
                started_at=row.get("started_at"),
                ended_at=row.get("ended_at"),
                tags=list(row.get("tags") or []),
            )
            for row in payload
        ]
    )
