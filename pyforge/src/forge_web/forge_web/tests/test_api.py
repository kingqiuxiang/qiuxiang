from django.test import TestCase

from forge_web.models import Session


class NinjaApiTests(TestCase):
    def test_api_health_and_sessions(self) -> None:
        Session.objects.create(slice_id="W21-api", tags=["ninja"])
        health = self.client.get("/api/health")
        self.assertEqual(health.status_code, 200)
        self.assertEqual(health.json()["ok"], True)
        listing = self.client.get("/api/sessions")
        self.assertEqual(listing.status_code, 200)
        ids = [row["slice_id"] for row in listing.json()]
        self.assertIn("W21-api", ids)
