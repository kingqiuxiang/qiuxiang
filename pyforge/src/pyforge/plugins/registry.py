from __future__ import annotations

from pyforge.plugins.protocol import SessionPlugin
from pyforge.session import DailySession


class PluginRegistry:
    def __init__(self) -> None:
        self._plugins: list[SessionPlugin] = []
        self.failures: list[str] = []

    def register(self, plugin: SessionPlugin) -> None:
        self._plugins.append(plugin)

    def enabled(self, disabled: set[str] | None = None) -> list[SessionPlugin]:
        blocked = disabled or set()
        return [plugin for plugin in self._plugins if plugin.name not in blocked]

    def run_hook(self, session: DailySession, disabled: set[str] | None = None) -> None:
        for plugin in self.enabled(disabled):
            try:
                plugin.on_session_stop(session)
            except Exception as err:  # noqa: BLE001 — isolation is the lesson
                self.failures.append(f"{plugin.name}:{type(err).__name__}")
