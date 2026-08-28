from __future__ import annotations

import copy


def shallow_rows(rows: list[list[str]]) -> list[list[str]]:
    return rows[:]


def deep_rows(rows: list[list[str]]) -> list[list[str]]:
    return copy.deepcopy(rows)
