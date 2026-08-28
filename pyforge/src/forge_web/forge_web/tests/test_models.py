from django.test import TestCase

from forge_web.models import GateAttempt, Session, Slice


class ModelTests(TestCase):
    def test_three_tables_round_trip(self) -> None:
        session = Session.objects.create(slice_id="W22", tags=["orm"])
        Slice.objects.create(slice_id="W22", title="orm", tags=["orm"])
        GateAttempt.objects.create(gate_name="g1", slice_id="W22", ok=True)
        self.assertEqual(Session.objects.get(pk=session.pk).tags, ["orm"])
        self.assertEqual(Slice.objects.get(pk="W22").title, "orm")
        self.assertEqual(GateAttempt.objects.count(), 1)
