from pyforge.plugins.registry import PluginRegistry
from pyforge.session import DailySession


class Boom:
    name = "boom"

    def on_session_stop(self, session: DailySession) -> None:
        raise RuntimeError("plugin exploded")


def test_plugin_failure_does_not_drop_session() -> None:
    session = DailySession("W35").start()
    session.tags.append("kept")
    registry = PluginRegistry()
    registry.register(Boom())
    registry.run_hook(session)
    assert session.tags == ["kept"]
    assert registry.failures == ["boom:RuntimeError"]
