from pyforge.services.log_context import bind_request_id, log_fields


def test_same_request_shares_id() -> None:
    first = bind_request_id("abc")
    assert log_fields()["request_id"] == "abc"
    assert first == "abc"
    second = bind_request_id("def")
    assert second != first
    assert log_fields()["request_id"] == "def"
