from django.test import TestCase

from forge_web.metrics import current


class MetricsTests(TestCase):
    def test_metrics_increases_after_request(self) -> None:
        before = current()
        self.client.get("/weeks/")
        self.client.get("/metrics/")
        self.assertGreater(current(), before)
        body = self.client.get("/metrics/").content.decode()
        self.assertIn("requests", body)
