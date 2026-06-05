from __future__ import annotations

from pathlib import Path


ALLOWED_SUFFIXES = {
    ".java",
    ".kt",
    ".py",
    ".go",
    ".js",
    ".ts",
    ".tsx",
    ".vue",
    ".json",
    ".yml",
    ".yaml",
}

IGNORED_DIRS = {".git", "node_modules", "dist", "build", ".idea", ".venv", "__pycache__"}


def build_context(project_root: str, keywords: list[str], max_chars: int = 8000) -> str:
    root = Path(project_root).resolve()
    if not root.exists():
        return ""

    lowered_keywords = [k.lower() for k in keywords if k.strip()]
    if not lowered_keywords:
        return ""

    chunks: list[str] = []
    total = 0
    for path in root.rglob("*"):
        if any(part in IGNORED_DIRS for part in path.parts):
            continue
        if not path.is_file() or path.suffix not in ALLOWED_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        lowered = text.lower()
        if not any(k in lowered or k in path.name.lower() for k in lowered_keywords):
            continue

        snippet = text[:800]
        block = f"\n# file: {path}\n{snippet}\n"
        projected = total + len(block)
        if projected > max_chars:
            break
        chunks.append(block)
        total = projected
    return "\n".join(chunks)
