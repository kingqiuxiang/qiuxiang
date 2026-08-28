from django.test import Client, TestCase

from forge_web.models import Session


class SessionFormTests(TestCase):
    def test_csrf_required_and_create_works(self) -> None:
        locked = Client(enforce_csrf_checks=True)
        denied = locked.post("/sessions/new/", {"slice_id": "W24"}, HTTP_HX_REQUEST="true")
        self.assertEqual(denied.status_code, 403)
        ok = self.client.post(
            "/sessions/new/",
            {"slice_id": "W24", "tags_text": "form"},
            HTTP_HX_REQUEST="true",
        )
        self.assertEqual(ok.status_code, 200)
        self.assertTrue(Session.objects.filter(slice_id="W24").exists())
        self.assertEqual(Session.objects.get(slice_id="W24").tags, ["form"])
