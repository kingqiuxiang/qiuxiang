import pytest
from pydantic import ValidationError

from pyforge.domain.manifest import CourseManifest


def test_rejects_dirty_payload() -> None:
    with pytest.raises(ValidationError):
        CourseManifest.model_validate({"version": 1, "track": "x"})
    with pytest.raises(ValidationError):
        CourseManifest.model_validate(
            {"version": 1, "track": "x", "weeks": [{"n": "nope", "slice": "a", "ship": "b", "verify": "c"}]}
        )


def test_accepts_week_row() -> None:
    manifest = CourseManifest.model_validate(
        {
            "version": 1,
            "track": "pyforge-python-os",
            "weeks": [{"n": 5, "slice": "json", "ship": "store", "verify": "pytest", "skip": "yaml"}],
        }
    )
    assert manifest.weeks[0].n == 5
