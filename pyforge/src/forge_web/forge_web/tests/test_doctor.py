from io import StringIO

from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import TestCase


class DoctorTests(TestCase):
    def test_dev_prints_ok(self) -> None:
        out = StringIO()
        call_command("pyforge_doctor", stdout=out)
        self.assertIn("ok", out.getvalue())

    def test_prod_rejects_debug(self) -> None:
        with self.assertRaises(CommandError):
            call_command("pyforge_doctor", env="prod")
