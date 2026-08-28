from django.conf import settings
from django.core.management.base import BaseCommand

from pyforge.plugins.pomodoro import PomodoroPlugin
from pyforge.plugins.registry import PluginRegistry
from pyforge.plugins.wrongbook import WrongbookPlugin


class Command(BaseCommand):
    help = "List PyForge plugins"

    def handle(self, *args: object, **options: object) -> None:
        registry = PluginRegistry()
        registry.register(PomodoroPlugin())
        registry.register(WrongbookPlugin())
        disabled = set(getattr(settings, "PYFORGE_DISABLED_PLUGINS", []))
        for plugin in registry.enabled(disabled):
            self.stdout.write(plugin.name)
