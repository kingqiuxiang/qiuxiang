from __future__ import annotations

import shlex
import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass
class RunningProcess:
    command: str
    cwd: str
    process: subprocess.Popen[str]


class ProcessManager:
    def __init__(self) -> None:
        self._running: RunningProcess | None = None

    def start(self, command: str, cwd: str) -> RunningProcess:
        if self._running and self._running.process.poll() is None:
            return self._running

        target_dir = Path(cwd).resolve()
        args = shlex.split(command)
        proc = subprocess.Popen(
            args,
            cwd=target_dir,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            text=True,
        )
        self._running = RunningProcess(command=command, cwd=str(target_dir), process=proc)
        return self._running

    def status(self) -> dict[str, str | int | None]:
        if not self._running:
            return {"status": "idle", "pid": None, "command": None, "cwd": None}
        code = self._running.process.poll()
        return {
            "status": "running" if code is None else "exited",
            "pid": self._running.process.pid,
            "command": self._running.command,
            "cwd": self._running.cwd,
            "exit_code": code,
        }


process_manager = ProcessManager()
