from __future__ import annotations

from dataclasses import dataclass


class GateFailed(Exception):
    pass


@dataclass(frozen=True)
class CapabilityGate:
    name: str

    def run(self, fn) -> None:
        try:
            fn()
        except Exception as err:
            raise GateFailed(self.name) from err
