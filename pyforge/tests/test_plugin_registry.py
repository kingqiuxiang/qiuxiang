from pyforge.plugins.pomodoro import PomodoroPlugin
from pyforge.plugins.registry import PluginRegistry
from pyforge.plugins.wrongbook import WrongbookPlugin
from pyforge.session import DailySession


def test_two_plugins_see_stop() -> None:
    session = DailySession("W33").start()
    registry = PluginRegistry()
    registry.register(PomodoroPlugin())
    registry.register(WrongbookPlugin())
    registry.run_hook(session)
    assert "pomodoro" in session.tags
    assert "wrongbook" in session.tags
