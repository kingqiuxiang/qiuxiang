from __future__ import annotations

import argparse
import os
from pathlib import Path

import pyforge
from pyforge.json_repo import JsonSessionRepository
from pyforge.session import DailySession


def _store_path(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit)
    raw = os.environ.get("PYFORGE_STORE", "pyforge-sessions.json")
    return Path(raw)


def main(argv: list[str] | None = None, store_path: Path | None = None) -> int:
    parser = argparse.ArgumentParser(prog="pyforge")
    parser.add_argument("--version", action="version", version=pyforge.__version__)
    parser.add_argument("--store", default=None, help="JSON session store path")
    sub = parser.add_subparsers(dest="cmd")

    session = sub.add_parser("session", help="DailySession commands")
    session_sub = session.add_subparsers(dest="session_cmd", required=True)

    start = session_sub.add_parser("start")
    start.add_argument("slice_id")

    session_sub.add_parser("end")
    session_sub.add_parser("list")

    args = parser.parse_args(argv)
    path = store_path or _store_path(args.store)
    repo = JsonSessionRepository(path)

    if args.cmd != "session":
        parser.print_help()
        return 0

    if args.session_cmd == "start":
        item = DailySession(args.slice_id).start()
        repo.add(item)
        print(item.slice_id)
        return 0
    if args.session_cmd == "end":
        items = repo.list_all()
        open_ones = [s for s in items if s.started_at is not None and s.ended_at is None]
        if not open_ones:
            raise SystemExit("no open session")
        open_ones[-1].stop()
        from pyforge.session_store import save_sessions

        save_sessions(path, items)
        print(open_ones[-1].slice_id)
        return 0
    if args.session_cmd == "list":
        for item in repo.list_all():
            print(item.slice_id)
        return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
