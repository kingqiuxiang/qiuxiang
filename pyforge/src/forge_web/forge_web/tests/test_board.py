from django.test import TestCase

from forge_web.models import GateAttempt, Session, Slice


class BoardAndNavTests(TestCase):
    def test_board_counts_and_nav_pages(self) -> None:
        Session.objects.create(slice_id="W-board", tags=["demo"])
        Slice.objects.create(slice_id="W23", title="flesh", tags=["ui"])
        GateAttempt.objects.create(gate_name="g1", slice_id="W23", ok=True)

        board = self.client.get("/")
        self.assertEqual(board.status_code, 200)
        self.assertContains(board, "W-board")
        self.assertContains(board, "打开 weeks")

        slices = self.client.get("/slices/")
        self.assertEqual(slices.status_code, 200)
        self.assertContains(slices, "W23")
        self.assertContains(slices, "开一条")

        gates = self.client.get("/gates/")
        self.assertEqual(gates.status_code, 200)
        self.assertContains(gates, "g1")
        self.assertContains(gates, "ok")

    def test_prefill_slice_id_from_query(self) -> None:
        page = self.client.get("/weeks/", {"prefill": "W41"})
        self.assertContains(page, 'value="W41"')
