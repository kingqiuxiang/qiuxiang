from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.db import connection


class Command(BaseCommand):
    help = "Check PyForge environment"

    def add_arguments(self, parser) -> None:  # type: ignore[no-untyped-def]
        parser.add_argument("--env", default="dev")

    def handle(self, *args: object, **options: object) -> None:
        env = str(options.get("env") or "dev")
        self.stdout.write(f"debug={settings.DEBUG}")
        self.stdout.write(f"engine={connection.vendor}")
        if env == "prod" and settings.DEBUG:
            raise CommandError("DEBUG=True is not allowed in prod")
        self.stdout.write("ok")
