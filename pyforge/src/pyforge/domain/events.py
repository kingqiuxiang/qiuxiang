from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from pyforge.services.redact import redact_event


@dataclass(frozen=True)
class ObservationEvent:
    kind: str
    slice_id: str
    payload: dict[str, Any]

    def safe_payload(self) -> dict[str, Any]:
        return redact_event(self.payload)
