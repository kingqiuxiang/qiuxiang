from pathlib import Path

import yaml
from django.core.management.base import BaseCommand

from forge_web.models import Slice
from pyforge.domain.manifest import CourseManifest


class Command(BaseCommand):
    help = "Import courses/curriculum.yaml"

    def add_arguments(self, parser) -> None:  # type: ignore[no-untyped-def]
        parser.add_argument("path")

    def handle(self, *args: object, **options: object) -> None:
        path = Path(str(options["path"]))
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        manifest = CourseManifest.model_validate(data)
        for week in manifest.weeks:
            Slice.objects.update_or_create(
                slice_id=f"W{week.n:02d}",
                defaults={"title": week.slice, "tags": [week.ship]},
            )
        self.stdout.write(f"imported {len(manifest.weeks)}")
