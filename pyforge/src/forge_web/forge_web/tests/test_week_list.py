from django.test import TestCase

from forge_web.models import Session


class WeekListTests(TestCase):
    def test_full_page_and_htmx_partial(self) -> None:
        Session.objects.create(slice_id="W23", tags=["htmx"])
        full = self.client.get("/weeks/")
        self.assertEqual(full.status_code, 200)
        self.assertContains(full, "<html")
        self.assertContains(full, "W23")
        partial = self.client.get("/weeks/", HTTP_HX_REQUEST="true")
        self.assertEqual(partial.status_code, 200)
        self.assertNotContains(partial, "<html")
        self.assertContains(partial, "W23")
        self.assertContains(partial, "<tr")
