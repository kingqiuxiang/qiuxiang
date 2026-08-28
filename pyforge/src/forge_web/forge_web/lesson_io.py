from __future__ import annotations

import html
import re
from dataclasses import dataclass
from pathlib import Path

from pyforge.domain.modules import find_courses_dir


@dataclass(frozen=True)
class LessonFile:
    week: int
    path: Path
    title: str
    slug: str


@dataclass(frozen=True)
class LessonSection:
    heading: str
    html: str


_FENCE = re.compile(r"```(\w*)\n(.*?)```", re.DOTALL)
_LINK = re.compile(r"\[([^\]]+)\]\((https?://[^)]+|/?[^\s)]+)\)")


def list_lessons(week: int, courses_dir: Path | None = None) -> list[LessonFile]:
    root = (courses_dir or find_courses_dir()) / "lessons"
    found: list[LessonFile] = []
    for path in sorted(root.glob(f"W{week:02d}-*.md")):
        title = path.read_text(encoding="utf-8").splitlines()[0].lstrip("# ").strip()
        found.append(LessonFile(week=week, path=path, title=title, slug=path.stem))
    return found


def read_lesson(path: Path) -> tuple[str, list[LessonSection]]:
    text = path.read_text(encoding="utf-8")
    chunks = re.split(r"\n(?=## )", text)
    title = chunks[0].splitlines()[0].lstrip("# ").strip()
    sections: list[LessonSection] = []
    for chunk in chunks[1:]:
        lines = chunk.splitlines()
        heading = lines[0].lstrip("# ").strip()
        body = "\n".join(lines[1:]).strip()
        sections.append(LessonSection(heading=heading, html=_render_body(body)))
    return title, sections


def _render_body(body: str) -> str:
    parts: list[str] = []
    cursor = 0
    for match in _FENCE.finditer(body):
        parts.append(_render_prose(body[cursor : match.start()]))
        lang = match.group(1) or ""
        code = match.group(2).rstrip("\n")
        if lang == "mermaid":
            parts.append(f'<pre class="mermaid">{code}</pre>')
        else:
            parts.append(f'<pre><code class="lang-{html.escape(lang)}">{html.escape(code)}</code></pre>')
        cursor = match.end()
    parts.append(_render_prose(body[cursor:]))
    return "\n".join(part for part in parts if part)


def _render_prose(text: str) -> str:
    blocks: list[str] = []
    for raw in re.split(r"\n\s*\n", text.strip()):
        if not raw.strip():
            continue
        line = html.escape(raw.strip())
        line = line.replace("\n", "<br>")
        line = _LINK.sub(r'<a href="\2" rel="noreferrer">\1</a>', line)
        line = re.sub(r"`([^`]+)`", r"<code>\1</code>", line)
        line = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", line)
        blocks.append(f"<p>{line}</p>")
    return "\n".join(blocks)
