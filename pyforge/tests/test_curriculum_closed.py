from __future__ import annotations

import re
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


def test_every_week_verify_target_exists() -> None:
    data = yaml.safe_load((ROOT / "courses/curriculum.yaml").read_text(encoding="utf-8"))
    missing: list[str] = []
    for week in data["weeks"]:
        for rel in _targets(str(week["verify"])):
            if not (ROOT / rel).exists():
                missing.append(f"W{int(week['n']):02d}: {rel}")
    assert missing == []


def test_every_gate_script_or_test_exists() -> None:
    data = yaml.safe_load((ROOT / "courses/curriculum.yaml").read_text(encoding="utf-8"))
    missing: list[str] = []
    for name, spec in data["gates"].items():
        for rel in _targets(str(spec["cmd"])):
            if not (ROOT / rel).exists():
                missing.append(f"{name}: {rel}")
    assert missing == []


def _targets(verify: str) -> list[str]:
    found: list[str] = []
    for match in re.finditer(
        r"(?:^|\s)(tests/[\w./-]+\.py|forge_web/tests/[\w./-]+\.py|scripts/[\w./-]+\.ps1|src/[\w./-]+)",
        verify,
    ):
        found.append(match.group(1))
    if "manage.py test " in verify:
        for name in re.findall(r"forge_web\.tests\.(\w+)", verify):
            found.append(f"src/forge_web/forge_web/tests/{name}.py")
    if "manage.py" in verify:
        found.append("src/forge_web/manage.py")
    if "mypy " in verify:
        for part in verify.split():
            if part.startswith("src/"):
                found.append(part)
    if "uv sync --frozen" in verify:
        found.append("uv.lock")
    if "pyforge --" in verify:
        found.append("pyproject.toml")
    return found
