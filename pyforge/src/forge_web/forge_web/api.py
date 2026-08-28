from __future__ import annotations

from django.http import HttpRequest
from ninja import NinjaAPI

from forge_web.models import Session

api = NinjaAPI(title="pyforge", version="0.1.0")


@api.get("/health")
def api_health(request: HttpRequest) -> dict[str, bool]:
    return {"ok": True}


@api.get("/sessions")
def api_sessions(request: HttpRequest) -> list[dict[str, object]]:
    rows = Session.objects.all().order_by("id")
    return [{"slice_id": row.slice_id, "tags": list(row.tags or [])} for row in rows]
