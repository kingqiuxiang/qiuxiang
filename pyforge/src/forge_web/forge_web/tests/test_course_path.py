from django.test import TestCase

from forge_web.models import Session


class CoursePathTests(TestCase):
    def test_path_lists_eight_layers_and_weeks(self) -> None:
        Session.objects.create(slice_id="W02", tags=["path"])
        page = self.client.get("/path/")
        self.assertEqual(page.status_code, 200)
        self.assertContains(page, "基础")
        self.assertContains(page, "底层")
        self.assertContains(page, "框架")
        self.assertContains(page, "W01")
        self.assertContains(page, "W48")
        self.assertContains(page, 'class="mermaid"')
        self.assertContains(page, "/lessons/W02/")

    def test_module_has_official_sources_and_diagram(self) -> None:
        page = self.client.get("/path/foundation/")
        self.assertEqual(page.status_code, 200)
        self.assertContains(page, "docs.python.org")
        self.assertContains(page, "DailySession")
        self.assertContains(page, "本层架构")

    def test_lesson_reader_shows_seven_sections(self) -> None:
        page = self.client.get("/lessons/W02/")
        self.assertEqual(page.status_code, 200)
        self.assertContains(page, "今晚能感觉到什么")
        self.assertContains(page, "和 Java 不同的一句")
        self.assertContains(page, "可变默认参数")
        self.assertContains(page, "开工这条")
        self.assertContains(page, "docs.python.org")

    def test_unknown_module_and_week_are_404(self) -> None:
        self.assertEqual(self.client.get("/path/nope/").status_code, 404)
        self.assertEqual(self.client.get("/lessons/W99/").status_code, 404)
