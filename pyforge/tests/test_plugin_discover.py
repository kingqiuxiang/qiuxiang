from pyforge.plugins.discover import discover_plugins


def test_discover_bundled_plugins() -> None:
    names = {plugin.name for plugin in discover_plugins()}
    assert {"pomodoro", "wrongbook"} <= names
