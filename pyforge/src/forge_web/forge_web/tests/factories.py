from __future__ import annotations

from django.utils import timezone

from forge_web.models import Session


def SessionFactory(*, slice_id: str = "W25", tags: list[str] | None = None) -> Session:
    return Session.objects.create(
        slice_id=slice_id,
        started_at=timezone.now(),
        tags=list(tags or ["factory"]),
    )
