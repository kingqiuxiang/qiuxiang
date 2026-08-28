from pyforge.session import DailySession
from pyforge.services.weekly_report import WeeklyReportService


def test_weekly_report_counts_and_dedupes_tags() -> None:
    a = DailySession("W20-01")
    a.tags.extend(["gil", "uv"])
    b = DailySession("W20-02")
    b.tags.extend(["uv"])
    report = WeeklyReportService().build("W20", [a, b])
    assert report.session_count == 2
    assert report.tags == ("gil", "uv")
