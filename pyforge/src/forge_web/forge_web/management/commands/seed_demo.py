from django.core.management.base import BaseCommand
from django.utils import timezone

from forge_web.models import Session


class Command(BaseCommand):
    help = "Seed demo sessions"

    def handle(self, *args: object, **options: object) -> None:
        Session.objects.get_or_create(slice_id="W47-demo", defaults={"tags": ["demo"], "started_at": timezone.now()})
        Session.objects.get_or_create(slice_id="W02", defaults={"tags": ["gil"], "started_at": timezone.now()})
        self.stdout.write("seeded")
