from __future__ import annotations

from django.conf import settings
from django.utils import timezone

from forge_web.models import Session
from pyforge.plugins.pomodoro import PomodoroPlugin
from pyforge.plugins.registry import PluginRegistry
from pyforge.plugins.wrongbook import WrongbookPlugin
from pyforge.session import DailySession


def session_status(row: Session) -> str:
    if row.started_at is not None and row.ended_at is None:
        return "open"
    if row.ended_at is not None:
        return "stopped"
    return "idle"


def start_session(row: Session) -> Session:
    row.started_at = timezone.now()
    row.ended_at = None
    row.save(update_fields=["started_at", "ended_at"])
    return row


def stop_session(row: Session) -> Session:
    now = timezone.now()
    daily = DailySession(
        slice_id=row.slice_id,
        started_at=row.started_at or now,
        ended_at=None,
        tags=list(row.tags or []),
    )
    daily.stop()
    registry = PluginRegistry()
    registry.register(PomodoroPlugin())
    registry.register(WrongbookPlugin())
    disabled = set(getattr(settings, "PYFORGE_DISABLED_PLUGINS", []))
    registry.run_hook(daily, disabled)
    row.started_at = daily.started_at
    row.ended_at = daily.ended_at
    row.tags = daily.tags
    row.save(update_fields=["started_at", "ended_at", "tags"])
    return row


def delete_session(row: Session) -> None:
    row.delete()
