from pyforge.session import DailySession


def test_two_sessions_do_not_share_tags():
    a = DailySession("W02").start()
    b = DailySession("W02").start()
    a.tags.append("gil")
    assert b.tags == []
    assert a.tags == ["gil"]


def test_empty_tags_are_not_has_tags():
    s = DailySession("W02")
    assert s.has_tags() is False
    s.tags.append("copy")
    assert s.has_tags() is True


def test_stop_without_start_raises():
    s = DailySession("W02")
    try:
        s.stop()
    except RuntimeError as exc:
        assert "not started" in str(exc)
    else:
        raise AssertionError("expected RuntimeError")
