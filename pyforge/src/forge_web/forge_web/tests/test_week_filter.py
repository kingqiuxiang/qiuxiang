from django.test import TestCase

from forge_web.models import Session


class WeekFilterTests(TestCase):
    def test_htmx_filter_only_returns_matching_rows(self) -> None:
        Session.objects.create(slice_id="keep", tags=["gil"])
        Session.objects.create(slice_id="drop", tags=["uv"])
        resp = self.client.get("/weeks/", {"tag": "gil"}, HTTP_HX_REQUEST="true")
        self.assertEqual(resp.status_code, 200)
        self.assertNotContains(resp, "<html")
        self.assertContains(resp, "keep")
        self.assertNotContains(resp, "drop")
