import sqlite3

from pyforge.gate import CapabilityGate
from pyforge.services.attempts import list_attempts
from pyforge.services.gate_engine import run_and_record
from pyforge.services.schema import apply_schema


def test_failed_gate_is_written() -> None:
    conn = sqlite3.connect(":memory:")
    apply_schema(conn)
    run_and_record(conn, CapabilityGate("g42"), "W42", lambda: 1 / 0)
    rows = list_attempts(conn)
    assert rows == [("g42", "W42", 0)]
