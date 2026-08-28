from __future__ import annotations

from pathlib import Path

import yaml
from pydantic import BaseModel, Field

from pyforge.domain.manifest import CourseManifest, WeekSpec


class SourceLink(BaseModel):
    title: str
    url: str


class ModuleSpec(BaseModel):
    id: str
    title: str
    layer: int
    weeks: list[int]
    gate: str = ""
    gist: str
    explain: str
    sources: list[SourceLink] = Field(default_factory=list)
    diagram: str = ""


class ModuleBook(BaseModel):
    version: int
    modules: list[ModuleSpec]
    progression: str = ""


def find_courses_dir(start: Path | None = None) -> Path:
    here = start or Path(__file__).resolve()
    for parent in [here, *here.parents]:
        candidate = parent / "courses"
        if (candidate / "curriculum.yaml").exists():
            return candidate
    raise FileNotFoundError("courses/curriculum.yaml")


def load_manifest(courses_dir: Path | None = None) -> CourseManifest:
    root = courses_dir or find_courses_dir()
    data = yaml.safe_load((root / "curriculum.yaml").read_text(encoding="utf-8"))
    return CourseManifest.model_validate(data)


def load_module_book(courses_dir: Path | None = None) -> ModuleBook:
    root = courses_dir or find_courses_dir()
    data = yaml.safe_load((root / "modules.yaml").read_text(encoding="utf-8"))
    return ModuleBook.model_validate(data)


def week_by_n(manifest: CourseManifest, n: int) -> WeekSpec:
    for week in manifest.weeks:
        if week.n == n:
            return week
    raise KeyError(n)


def module_by_id(book: ModuleBook, module_id: str) -> ModuleSpec:
    for module in book.modules:
        if module.id == module_id:
            return module
    raise KeyError(module_id)


def module_for_week(book: ModuleBook, n: int) -> ModuleSpec:
    for module in book.modules:
        if n in module.weeks:
            return module
    raise KeyError(n)
