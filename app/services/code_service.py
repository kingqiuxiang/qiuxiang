from __future__ import annotations

from pathlib import Path

from app.models import CodeScanResult

TEXT_FILE_SUFFIXES = {
    ".java",
    ".kt",
    ".js",
    ".ts",
    ".tsx",
    ".jsx",
    ".py",
    ".go",
    ".rs",
    ".php",
    ".rb",
    ".json",
    ".yaml",
    ".yml",
    ".md",
}


def _is_text_code_file(path: Path) -> bool:
    return path.suffix.lower() in TEXT_FILE_SUFFIXES and path.is_file()


def scan_code_context(
    project_path: str, keywords: list[str], max_files: int = 10
) -> list[CodeScanResult]:
    root = Path(project_path).resolve()
    if not root.exists():
        return []

    normalized_keywords = [k.lower() for k in keywords if k.strip()]
    if not normalized_keywords:
        return []

    results: list[CodeScanResult] = []
    for file_path in root.rglob("*"):
        if len(results) >= max_files:
            break
        if ".git" in file_path.parts or "node_modules" in file_path.parts:
            continue
        if not _is_text_code_file(file_path):
            continue
        try:
            content = file_path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue

        lines = content.splitlines()
        for index, line in enumerate(lines):
            lower_line = line.lower()
            if any(keyword in lower_line for keyword in normalized_keywords):
                start = max(0, index - 3)
                end = min(len(lines), index + 4)
                snippet = "\n".join(lines[start:end])
                results.append(
                    CodeScanResult(file_path=str(file_path.relative_to(root)), snippet=snippet)
                )
                break

    return results
