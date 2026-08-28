from dataclasses import FrozenInstanceError

from pyforge.gate import CapabilityGate, GateFailed
from pyforge.session import DailySession


def test_cause_is_kept():
    gate = CapabilityGate("w04")

    def boom() -> None:
        return 1 / 0

    try:
        gate.run(boom)
    except GateFailed as exc:
        assert exc.__cause__ is not None
        assert isinstance(exc.__cause__, ZeroDivisionError)
    else:
        raise AssertionError("expected GateFailed")


def test_session_stop_without_start_is_cause():
    gate = CapabilityGate("w04")
    try:
        gate.run(lambda: DailySession("W04").stop())
    except GateFailed as exc:
        assert isinstance(exc.__cause__, RuntimeError)
    else:
        raise AssertionError("expected GateFailed")


def test_gate_is_hashable_value():
    a = CapabilityGate("w04")
    b = CapabilityGate("w04")
    bag = {a, b}
    assert len(bag) == 1


def test_cannot_mutate_gate_name():
    g = CapabilityGate("w04")
    try:
        g.name = "hack"
    except FrozenInstanceError:
        return
    raise AssertionError("expected FrozenInstanceError")
