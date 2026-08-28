from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass

from pyforge.session import DailySession


@dataclass(frozen=True)
class WeeklyReport:
    week: str
    session_count: int
    tags: tuple[str, ...]


class WeeklyReportService:
    def build(self, week: str, sessions: list[DailySession]) -> WeeklyReport:
        tags: set[str] = set()
        for session in sessions:
            tags.update(session.tags)
        return WeeklyReport(week=week, session_count=len(sessions), tags=tuple(sorted(tags)))

    def group_by_slice_prefix(self, sessions: list[DailySession]) -> dict[str, int]:
        counts: dict[str, int] = defaultdict(int)
        for session in sessions:
            counts[session.slice_id.split("-")[0]] += 1
        return dict(counts)
