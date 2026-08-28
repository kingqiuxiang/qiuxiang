from pyforge.services.redact import redact_event


def test_password_and_token_are_redacted() -> None:
    raw = {"user": "ada", "password": "s3cret", "token": "t", "nested": {"secret": "x"}}
    out = redact_event(raw)
    assert out["password"] == "***"
    assert out["token"] == "***"
    assert out["nested"]["secret"] == "***"
    assert "s3cret" not in str(out)
    assert out["user"] == "ada"
