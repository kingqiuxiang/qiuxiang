from django.test import Client, TestCase
from django.utils import timezone

from forge_web.models import Session


class SessionLifecycleTests(TestCase):
    def test_start_stop_delete_and_plugin_tags(self) -> None:
        row = Session.objects.create(slice_id="W23-flesh", tags=["htmx"])
        start = self.client.post(f"/sessions/{row.pk}/start/", HTTP_HX_REQUEST="true")
        self.assertEqual(start.status_code, 200)
        row.refresh_from_db()
        self.assertEqual(row.status, "open")
        self.assertContains(start, "收工")

        stop = self.client.post(f"/sessions/{row.pk}/stop/", HTTP_HX_REQUEST="true")
        self.assertEqual(stop.status_code, 200)
        row.refresh_from_db()
        self.assertEqual(row.status, "stopped")
        self.assertIn("pomodoro", row.tags)
        self.assertIn("wrongbook", row.tags)
        self.assertContains(stop, "开工")

        locked = Client(enforce_csrf_checks=True)
        denied = locked.post(f"/sessions/{row.pk}/delete/")
        self.assertEqual(denied.status_code, 403)

        gone = self.client.post(f"/sessions/{row.pk}/delete/", HTTP_HX_REQUEST="true")
        self.assertEqual(gone.status_code, 200)
        self.assertFalse(Session.objects.filter(pk=row.pk).exists())
        self.assertContains(gone, "empty")

    def test_tag_chip_and_status_on_full_page(self) -> None:
        Session.objects.create(slice_id="W-idle", tags=["uv"])
        Session.objects.create(
            slice_id="W-open",
            tags=["gil"],
            started_at=timezone.now(),
        )
        page = self.client.get("/weeks/")
        self.assertContains(page, 'class="chip"')
        self.assertContains(page, "open")
        self.assertContains(page, "开工")
        self.assertContains(page, "收工")
        self.assertContains(page, "删除")
        self.assertContains(page, "台账")
