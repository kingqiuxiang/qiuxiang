import pytest

from pyforge.services.gate_runner import GateTimeout, run_in_subprocess
from tests.gate_runner_jobs import ok_job, slow_job


def test_ok_job_returns() -> None:
    assert run_in_subprocess(ok_job, timeout=5) == "ok"


def test_timeout_kills_slow_job() -> None:
    with pytest.raises(GateTimeout):
        run_in_subprocess(slow_job, timeout=0.3)
