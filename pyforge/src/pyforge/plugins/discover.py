from __future__ import annotations

from importlib.metadata import entry_points

from pyforge.plugins.protocol import SessionPlugin


def discover_plugins(group: str = "pyforge.plugins") -> list[SessionPlugin]:
    found: list[SessionPlugin] = []
    selected = entry_points().select(group=group)
    for item in selected:
        plugin = item.load()
        if callable(plugin) and not hasattr(plugin, "on_session_stop"):
            plugin = plugin()
        found.append(plugin)
    return found
