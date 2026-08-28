import pyforge


def test_version_is_semver_shape():
    parts = pyforge.__version__.split(".")
    assert len(parts) == 3
    assert all(p.isdigit() for p in parts)
