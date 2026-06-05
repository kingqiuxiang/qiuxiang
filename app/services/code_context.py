from __future__ import annotations

from pathlib import Path
from typing import Iterable

CODE_SUFFIXES = {
    ".py",
    ".java",
    ".js",
    ".ts",
    ".tsx",
    ".jsx",
    ".go",
    ".rb",
    ".php",
    ".kt",
    ".sql",
    ".vue",
}

SKIP_DIRS = {
    ".git",
    ".idea",
    ".vscode",
    "node_modules",
    "dist",
    "build",
    "__pycache__",
    ".venv",
    "venv",
}


def _iter_files(root: Path) -> Iterable[Path]:
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.suffix.lower() in CODE_SUFFIXES:
            yield path


def collect_project_context(project_root: str, keywords: list[str], max_chars: int = 12000) -> str:
    root = Path(project_root).resolve()
    if not root.exists():
        return ""

    budget = max_chars
    snippets: list[str] = []
    lowered_keywords = [k.lower() for k in keywords if k]

    for file_path in _iter_files(root):
        if budget <= 0:
            break
        try:
            text = file_path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        lowered = text.lower()
        if lowered_keywords and not any(k in lowered or k in file_path.name.lower() for k in lowered_keywords):
            continue

        excerpt = text[: min(len(text), 900)]
        block = f"\n# File: {file_path}\n{excerpt}\n"
        if len(block) > budget:
            block = block[:budget]
        snippets.append(block)
        budget -= len(block)

    return "\n".join(snippets)
