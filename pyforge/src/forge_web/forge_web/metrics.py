from __future__ import annotations

REQUEST_COUNT = 0


def bump() -> int:
    global REQUEST_COUNT
    REQUEST_COUNT += 1
    return REQUEST_COUNT


def current() -> int:
    return REQUEST_COUNT
