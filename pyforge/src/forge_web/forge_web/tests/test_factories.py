from django.test import TestCase

from forge_web.tests.factories import SessionFactory


class FactoryTests(TestCase):
    def test_factory_creates_row(self) -> None:
        row = SessionFactory(slice_id="W25", tags=["factory"])
        self.assertEqual(row.slice_id, "W25")
        self.assertTrue(row.pk)
