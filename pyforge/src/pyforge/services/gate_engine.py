from __future__ import annotations

import sqlite3
from collections.abc import Callable

from pyforge.gate import CapabilityGate, GateFailed
from pyforge.services.attempts import record_attempt


def run_and_record(
    conn: sqlite3.Connection, gate: CapabilityGate, slice_id: str, fn: Callable[[], object]
) -> None:
    try:
        gate.run(fn)
    except GateFailed as err:
        cause = err.__cause__
        message = type(cause).__name__ if cause is not None else str(err)
        record_attempt(conn, gate.name, slice_id, ok=False, error=message)
        return
    record_attempt(conn, gate.name, slice_id, ok=True, error=None)
