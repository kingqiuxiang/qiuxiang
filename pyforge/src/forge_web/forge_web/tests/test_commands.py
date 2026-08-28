from io import StringIO
from pathlib import Path
from tempfile import TemporaryDirectory

from django.core.management import call_command
from django.test import TestCase

from forge_web.models import Session, Slice


def _curriculum_path() -> Path:
    for parent in Path(__file__).resolve().parents:
        candidate = parent / "courses" / "curriculum.yaml"
        if candidate.exists():
            return candidate
    raise FileNotFoundError("courses/curriculum.yaml")


class ManagementCommandTests(TestCase):
    def test_plugins_lists_pomodoro_and_wrongbook(self) -> None:
        out = StringIO()
        call_command("pyforge_plugins", stdout=out)
        text = out.getvalue()
        self.assertIn("pomodoro", text)
        self.assertIn("wrongbook", text)

    def test_import_course_loads_48_weeks(self) -> None:
        call_command("import_course", str(_curriculum_path()))
        self.assertEqual(Slice.objects.count(), 48)
        self.assertTrue(Slice.objects.filter(slice_id="W01").exists())
        self.assertTrue(Slice.objects.filter(slice_id="W48").exists())

    def test_seed_demo_creates_known_rows(self) -> None:
        call_command("seed_demo")
        self.assertTrue(Session.objects.filter(slice_id="W47-demo").exists())
        self.assertTrue(Session.objects.filter(slice_id="W02").exists())

    def test_backup_command_round_trip(self) -> None:
        Session.objects.create(slice_id="keep", tags=["b"])
        with TemporaryDirectory() as raw:
            dest = Path(raw) / "b.json"
            call_command("pyforge_backup", out=str(dest))
            Session.objects.create(slice_id="extra", tags=["x"])
            self.assertEqual(Session.objects.count(), 2)
            call_command("pyforge_backup", restore=str(dest))
            self.assertEqual(list(Session.objects.values_list("slice_id", flat=True)), ["keep"])
