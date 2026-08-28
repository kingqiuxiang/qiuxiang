from __future__ import annotations

from dataclasses import dataclass

from forge_web.models import GateAttempt, Session
from pyforge.domain.manifest import WeekSpec
from pyforge.domain.modules import (
    ModuleSpec,
    load_manifest,
    load_module_book,
    module_by_id,
    module_for_week,
    week_by_n,
)


@dataclass(frozen=True)
class WeekOnPath:
    n: int
    code: str
    title: str
    ship: str
    verify: str
    touched: bool
    gated: bool


@dataclass(frozen=True)
class ModuleOnPath:
    spec: ModuleSpec
    weeks: list[WeekOnPath]
    touched: int


def _touched_weeks() -> set[int]:
    found: set[int] = set()
    for slice_id in Session.objects.values_list("slice_id", flat=True):
        text = str(slice_id)
        if len(text) >= 3 and text[0] == "W" and text[1:3].isdigit():
            found.add(int(text[1:3]))
    return found


def _ok_gates() -> set[str]:
    return set(GateAttempt.objects.filter(ok=True).values_list("gate_name", flat=True))


def _week_on_path(week: WeekSpec, touched: set[int], ok_gates: set[str], gate_name: str) -> WeekOnPath:
    return WeekOnPath(
        n=week.n,
        code=f"W{week.n:02d}",
        title=week.slice,
        ship=week.ship,
        verify=week.verify,
        touched=week.n in touched,
        gated=gate_name.lower() in {name.lower() for name in ok_gates} if week.n % 4 == 0 else week.n in touched,
    )


def build_path() -> tuple[list[ModuleOnPath], str]:
    book = load_module_book()
    manifest = load_manifest()
    touched = _touched_weeks()
    ok_gates = _ok_gates()
    modules: list[ModuleOnPath] = []
    for spec in book.modules:
        weeks = [_week_on_path(week_by_n(manifest, n), touched, ok_gates, spec.gate) for n in spec.weeks]
        modules.append(ModuleOnPath(spec=spec, weeks=weeks, touched=sum(1 for item in weeks if item.touched)))
    return modules, book.progression


def build_module(module_id: str) -> ModuleOnPath:
    book = load_module_book()
    manifest = load_manifest()
    spec = module_by_id(book, module_id)
    touched = _touched_weeks()
    ok_gates = _ok_gates()
    weeks = [_week_on_path(week_by_n(manifest, n), touched, ok_gates, spec.gate) for n in spec.weeks]
    return ModuleOnPath(spec=spec, weeks=weeks, touched=sum(1 for item in weeks if item.touched))


def build_week_context(n: int) -> tuple[ModuleSpec, WeekSpec]:
    book = load_module_book()
    manifest = load_manifest()
    return module_for_week(book, n), week_by_n(manifest, n)
