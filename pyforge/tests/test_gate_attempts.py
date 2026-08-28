import sqlite3

import pytest

from pyforge.services.attempts import DuplicateAttempt, list_attempts, record_attempt
from pyforge.services.schema import apply_schema


def test_duplicate_attempt_rolls_back_visibility() -> None:
    conn = sqlite3.connect(":memory:")
    apply_schema(conn)
    record_attempt(conn, "g1", "W18", ok=True)
    with pytest.raises(DuplicateAttempt):
        record_attempt(conn, "g1", "W18", ok=False, error="again")
    assert list_attempts(conn) == [("g1", "W18", 1)]
