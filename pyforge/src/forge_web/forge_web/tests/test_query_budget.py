from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext

from forge_web.models import Session


class QueryBudgetTests(TestCase):
    def test_week_list_stays_under_eight_queries(self) -> None:
        Session.objects.bulk_create([Session(slice_id=f"W{i}", tags=["q"]) for i in range(6)])
        with CaptureQueriesContext(connection) as ctx:
            self.client.get("/weeks/")
        self.assertLessEqual(len(ctx), 8)
