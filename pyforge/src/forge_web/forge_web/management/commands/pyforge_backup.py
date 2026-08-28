import json
from pathlib import Path

from django.core.management.base import BaseCommand

from forge_web.backup import backup_sessions, restore_sessions


class Command(BaseCommand):
    help = "Backup or restore sessions"

    def add_arguments(self, parser) -> None:  # type: ignore[no-untyped-def]
        parser.add_argument("--restore", default="")
        parser.add_argument("--out", default="pyforge-backup.json")

    def handle(self, *args: object, **options: object) -> None:
        restore = str(options.get("restore") or "")
        if restore:
            payload = json.loads(Path(restore).read_text(encoding="utf-8"))
            restore_sessions(payload)
            self.stdout.write("restored")
            return
        out = Path(str(options.get("out") or "pyforge-backup.json"))
        out.write_text(json.dumps(backup_sessions(), default=str), encoding="utf-8")
        self.stdout.write(str(out))
