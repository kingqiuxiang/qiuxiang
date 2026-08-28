from __future__ import annotations

from collections.abc import Callable
from concurrent.futures import ProcessPoolExecutor
from typing import TypeVar

T = TypeVar("T")


class GateTimeout(Exception):
    pass


def run_in_subprocess(fn: Callable[[], T], timeout: float = 2.0) -> T:
    with ProcessPoolExecutor(max_workers=1) as pool:
        future = pool.submit(fn)
        try:
            return future.result(timeout=timeout)
        except TimeoutError as err:
            future.cancel()
            raise GateTimeout("gate timed out") from err
