from __future__ import annotations

from pydantic import BaseModel, Field


class WeekSpec(BaseModel):
    n: int
    slice: str
    ship: str
    verify: str
    skip: str = ""


class GateSpec(BaseModel):
    week: int
    cmd: str


class CourseManifest(BaseModel):
    version: int
    track: str
    gates: dict[str, GateSpec] = Field(default_factory=dict)
    weeks: list[WeekSpec]
