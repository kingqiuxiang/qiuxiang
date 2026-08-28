from __future__ import annotations

import threading
import time


def burn(n: int) -> int:
    total = 0
    for i in range(n):
        total += i * i
    return total


def wall_single(n: int) -> float:
    started = time.perf_counter()
    burn(n)
    return time.perf_counter() - started


def wall_two_threads(n: int) -> float:
    started = time.perf_counter()
    workers = [threading.Thread(target=burn, args=(n,)) for _ in range(2)]
    for worker in workers:
        worker.start()
    for worker in workers:
        worker.join()
    return time.perf_counter() - started
