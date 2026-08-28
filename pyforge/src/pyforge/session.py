from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone


@dataclass
class DailySession:
    slice_id: str
    started_at: datetime | None = None  # | 是 3.10 联合类型，当 Java 的 T|null
    ended_at: datetime | None = None
    tags: list[str] = field(default_factory=list)

    def start(self) -> DailySession:
        if self.started_at is not None:
            raise RuntimeError("already started")
        self.started_at = datetime.now(timezone.utc)
        return self

    def stop(self) -> DailySession:
        if self.started_at is None:
            raise RuntimeError("not started")
        self.ended_at = datetime.now(timezone.utc)
        return self

    def has_tags(self) -> bool:
        return len(self.tags) > 0
