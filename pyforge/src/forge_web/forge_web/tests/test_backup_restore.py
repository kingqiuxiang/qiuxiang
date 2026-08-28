from django.test import TestCase

from forge_web.backup import backup_sessions, restore_sessions
from forge_web.models import Session


class BackupRestoreTests(TestCase):
    def test_restore_returns_to_backup_point(self) -> None:
        Session.objects.create(slice_id="old", tags=["a"])
        payload = backup_sessions()
        Session.objects.create(slice_id="new", tags=["b"])
        self.assertEqual(Session.objects.count(), 2)
        restore_sessions(payload)
        self.assertEqual(list(Session.objects.values_list("slice_id", flat=True)), ["old"])
