from __future__ import annotations

import subprocess
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class ManagedProcess:
    project_name: str
    command: str
    cwd: str
    process: subprocess.Popen[str]
    logs: list[str] = field(default_factory=list)


class ProjectRunner:
    def __init__(self) -> None:
        self._processes: dict[str, ManagedProcess] = {}
        self._lock = threading.Lock()

    def _capture_logs(self, managed: ManagedProcess) -> None:
        process = managed.process
        if process.stdout is None:
            return
        for line in process.stdout:
            managed.logs.append(line.rstrip())
            if len(managed.logs) > 500:
                managed.logs = managed.logs[-500:]

    def start(self, project_name: str, command: str, cwd: str, ready_keyword: str | None, timeout_seconds: int) -> dict:
        with self._lock:
            existing = self._processes.get(project_name)
            if existing and existing.process.poll() is None:
                return {
                    "status": "already_running",
                    "pid": existing.process.pid,
                    "logs": existing.logs[-20:],
                }

            workdir = Path(cwd).resolve()
            process = subprocess.Popen(
                ["bash", "-lc", command],
                cwd=str(workdir),
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
            )
            managed = ManagedProcess(project_name=project_name, command=command, cwd=str(workdir), process=process)
            self._processes[project_name] = managed
            thread = threading.Thread(target=self._capture_logs, args=(managed,), daemon=True)
            thread.start()

        deadline = time.time() + max(timeout_seconds, 1)
        if ready_keyword:
            while time.time() < deadline:
                if any(ready_keyword.lower() in line.lower() for line in managed.logs):
                    return {"status": "running", "pid": process.pid, "logs": managed.logs[-20:]}
                if process.poll() is not None:
                    return {"status": "exited", "pid": process.pid, "logs": managed.logs[-30:]}
                time.sleep(0.3)
            return {"status": "running_no_ready_keyword", "pid": process.pid, "logs": managed.logs[-30:]}

        time.sleep(0.8)
        status = "running" if process.poll() is None else "exited"
        return {"status": status, "pid": process.pid, "logs": managed.logs[-20:]}

    def stop(self, project_name: str) -> dict:
        with self._lock:
            managed = self._processes.get(project_name)
            if not managed:
                return {"status": "not_found"}

            process = managed.process
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
            return {"status": "stopped", "pid": process.pid}

    def status(self, project_name: str) -> dict:
        with self._lock:
            managed = self._processes.get(project_name)
            if not managed:
                return {"status": "not_found"}
            process = managed.process
            return {
                "status": "running" if process.poll() is None else "exited",
                "pid": process.pid,
                "exit_code": process.poll(),
                "logs": managed.logs[-30:],
            }
