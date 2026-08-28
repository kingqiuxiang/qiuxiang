from pyforge.domain.events import ObservationEvent


def test_observation_event_redacts_password() -> None:
    event = ObservationEvent("error", "W40", {"user": "ada", "password": "s3cret"})
    safe = event.safe_payload()
    assert safe["password"] == "***"
    assert "s3cret" not in str(safe)
    assert event.payload["password"] == "s3cret"
